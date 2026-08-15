package dev.simplified.gson.factory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.NoArgsConstructor;
import dev.simplified.annotations.RequiredArgsConstructor;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.gson.annotation.Capture;
import dev.simplified.gson.annotation.Extract;
import dev.simplified.gson.annotation.Lenient;
import dev.simplified.gson.annotation.SerializedPath;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.accessor.FieldAccessor;
import dev.simplified.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

/**
 * Gson {@link TypeAdapterFactory} that processes {@link Lenient @Lenient} annotations on Map and
 * Collection fields.
 * <p>
 * {@code @Lenient} fields have incompatible entries silently filtered during
 * deserialization. Filtered entries are handed to {@link Overflow} and merged back during
 * serialization to preserve round-trip fidelity. A JSON object is incompatible with a
 * collection or array value type, so such entries are filtered rather than handed to the
 * delegate, which would fail the whole read.
 * <p>
 * A companion field can take one of those filtered entries for itself with
 * {@link Extract @Extract}, which {@link ExtractTypeAdapterFactory} handles from outside this
 * factory so that a {@code @Capture} source is reachable too.
 *
 * @see Lenient
 * @see Overflow
 */
@NoArgsConstructor
public final class LenientTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> @NotNull TypeAdapter<T> create(@NotNull Gson gson, @NotNull TypeToken<T> typeToken) {
        TypeAdapter<T> delegateAdapter = gson.getDelegateAdapter(this, typeToken);
        ConcurrentList<LenientFieldInfo> lenientFields = LenientFieldInfo.of(typeToken.getRawType());

        return lenientFields.isEmpty()
            ? delegateAdapter
            : new LenientTypeAdapter<>(gson, delegateAdapter, gson.getAdapter(JsonElement.class), lenientFields);
    }

    @Getter
    @RequiredArgsConstructor
    private static class LenientTypeAdapter<T> extends TypeAdapter<T> {

        private final @NotNull Gson gson;
        private final @NotNull TypeAdapter<T> delegateAdapter;
        private final @NotNull TypeAdapter<JsonElement> jsonElementAdapter;
        private final @NotNull ConcurrentList<LenientFieldInfo> lenientFields;

        @Override
        public void write(@NotNull JsonWriter out, @Nullable T value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            JsonElement jsonTree = this.getDelegateAdapter().toJsonTree(value);

            if (jsonTree.isJsonObject()) {
                JsonObject jsonObject = jsonTree.getAsJsonObject();

                // Merge overflow back into serialized JSON for each @Lenient field
                for (LenientFieldInfo lenientInfo : this.getLenientFields()) {
                    Object collection = lenientInfo.getAccessor().get(value);

                    if (collection == null)
                        continue;

                    JsonElement overflow = Overflow.find(collection, Overflow.Target.FIELD_ELEMENT);

                    if (overflow == null)
                        continue;

                    JsonElement fieldElement = locateElement(jsonObject, lenientInfo);

                    if (fieldElement != null && fieldElement.isJsonObject() && overflow.isJsonObject()) {
                        JsonObject fieldObj = fieldElement.getAsJsonObject();

                        for (Map.Entry<String, JsonElement> entry : overflow.getAsJsonObject().entrySet())
                            fieldObj.add(entry.getKey(), entry.getValue());
                    } else if (fieldElement != null && fieldElement.isJsonArray() && overflow.isJsonArray()) {
                        JsonArray fieldArr = fieldElement.getAsJsonArray();

                        for (JsonElement element : overflow.getAsJsonArray())
                            fieldArr.add(element);
                    }
                }

                this.getJsonElementAdapter().write(out, jsonObject);
            } else
                this.getDelegateAdapter().write(out, value);
        }

        @Override
        public @Nullable T read(@NotNull JsonReader in) throws IOException {
            JsonElement rootElement = this.getJsonElementAdapter().read(in);

            if (!rootElement.isJsonObject())
                return this.getDelegateAdapter().fromJsonTree(rootElement);

            JsonObject rootObject = rootElement.getAsJsonObject();

            // Per-field overflow storage (keyed by field name)
            ConcurrentList<FieldOverflow> overflows = Concurrent.newList();

            // Filter phase: process each @Lenient field
            for (LenientFieldInfo lenientInfo : this.getLenientFields()) {
                JsonElement fieldElement = locateElement(rootObject, lenientInfo);

                if (fieldElement == null)
                    continue;

                if (lenientInfo.isMap() && fieldElement.isJsonObject()) {
                    JsonObject original = fieldElement.getAsJsonObject();
                    JsonObject filtered = new JsonObject();
                    JsonObject overflow = new JsonObject();

                    for (Map.Entry<String, JsonElement> entry : original.entrySet()) {
                        if (isCompatibleMapEntry(entry.getKey(), entry.getValue(), lenientInfo.getKeyType(), lenientInfo.getValueType()))
                            filtered.add(entry.getKey(), entry.getValue());
                        else
                            overflow.add(entry.getKey(), entry.getValue());
                    }

                    replaceElement(rootObject, lenientInfo, filtered);
                    overflows.add(new FieldOverflow(lenientInfo.getFieldName(), overflow));
                } else if (!lenientInfo.isMap() && fieldElement.isJsonArray()) {
                    JsonArray original = fieldElement.getAsJsonArray();
                    JsonArray filtered = new JsonArray();
                    JsonArray overflow = new JsonArray();

                    for (JsonElement element : original) {
                        if (isCompatibleElement(element, lenientInfo.getElementType()))
                            filtered.add(element);
                        else
                            overflow.add(element);
                    }

                    replaceElement(rootObject, lenientInfo, filtered);
                    overflows.add(new FieldOverflow(lenientInfo.getFieldName(), overflow));
                }
            }

            // Delegate deserialization with sanitized tree
            T value = this.getDelegateAdapter().fromJsonTree(rootObject);

            if (value == null)
                return null;

            // Publish phase: hand each field's overflow to the store, keyed by the bound container.
            // Published unconditionally, even when empty - unlike @Capture, which publishes only a
            // non-empty overflow. A caller can observe the empty container on a later write.
            for (LenientFieldInfo lenientInfo : this.getLenientFields()) {
                Object collection = lenientInfo.getAccessor().get(value);

                if (collection == null)
                    continue;

                overflows.stream()
                    .filter(o -> o.fieldName().equals(lenientInfo.getFieldName()))
                    .findFirst()
                    .ifPresent(fieldOverflow -> Overflow.publish(collection, Overflow.Target.FIELD_ELEMENT, fieldOverflow.overflow()));
            }

            return value;
        }

        private boolean isCompatibleMapEntry(@NotNull String key, @NotNull JsonElement value, @NotNull Type keyType, @NotNull Type valueType) {
            try {
                // Check key compatibility
                Class<?> rawKeyType = getRawType(keyType);

                if (rawKeyType != String.class) {
                    try {
                        // an enum key matching no constant converts without throwing, so the throw
                        // alone is not enough - a null or a fallback is an unrecognized key and
                        // belongs in overflow, not bound onto a shared entry
                        Object converted = this.getGson().fromJson(new com.google.gson.JsonPrimitive(key), keyType);

                        if (converted == null || CaseInsensitiveEnumTypeAdapterFactory.isFallback(this.getGson(), keyType, converted))
                            return false;
                    } catch (Exception ex) {
                        return false;
                    }
                }

                // Check value compatibility
                return isCompatibleElement(value, valueType);
            } catch (Exception ignored) {
                return false;
            }
        }

        private boolean isCompatibleElement(@NotNull JsonElement element, @NotNull Type expectedType) {
            try {
                Class<?> rawType = getRawType(expectedType);

                if (element.isJsonNull())
                    return !rawType.isPrimitive();

                if (element.isJsonPrimitive()) {
                    com.google.gson.JsonPrimitive primitive = element.getAsJsonPrimitive();

                    if (rawType == String.class)
                        return primitive.isString();

                    if (rawType == Boolean.class || rawType == boolean.class)
                        return primitive.isBoolean();

                    if (Number.class.isAssignableFrom(rawType) || rawType.isPrimitive()) {
                        if (!primitive.isNumber())
                            return false;

                        // Verify no precision loss for integer types
                        if (rawType == Integer.class || rawType == int.class) {
                            double d = primitive.getAsDouble();
                            return d == Math.floor(d) && !Double.isInfinite(d)
                                && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE;
                        }

                        if (rawType == Long.class || rawType == long.class) {
                            double d = primitive.getAsDouble();
                            return d == Math.floor(d) && !Double.isInfinite(d);
                        }

                        return true;
                    }

                    // For enums, try to deserialize
                    if (rawType.isEnum()) {
                        // a fallback-resolved value is an unrecognized one, so it stays overflow -
                        // without this, marking an enum turns a lossless filter into a silent bind
                        Object result = this.getGson().fromJson(element, expectedType);
                        return result != null && !CaseInsensitiveEnumTypeAdapterFactory.isFallback(this.getGson(), expectedType, result);
                    }
                }

                // a JSON object never satisfies a collection or array type - accepting one here
                // let the delegate read it and throw "Expected BEGIN_ARRAY but was BEGIN_OBJECT"
                // instead of filtering the entry out, which is what @Lenient promises
                if (element.isJsonObject() && !rawType.isPrimitive() && !Number.class.isAssignableFrom(rawType)
                    && rawType != String.class && rawType != Boolean.class
                    && !Collection.class.isAssignableFrom(rawType) && !rawType.isArray())
                    return true;

                return element.isJsonArray() && (Collection.class.isAssignableFrom(rawType) || rawType.isArray());
            } catch (Exception ignored) {
                return false;
            }
        }

        private static @NotNull Class<?> getRawType(@NotNull Type type) {
            if (type instanceof Class<?> clazz)
                return clazz;

            if (type instanceof ParameterizedType parameterized)
                return (Class<?>) parameterized.getRawType();

            return Object.class;
        }

        private static @Nullable JsonElement locateElement(@NotNull JsonObject root, @NotNull LenientFieldInfo info) {
            if (info.getPathSegments() != null) {
                JsonElement current = root;

                for (String segment : info.getPathSegments()) {
                    if (current == null || !current.isJsonObject())
                        return null;

                    current = current.getAsJsonObject().get(segment);
                }

                return current;
            }

            return root.get(info.getSerializedName());
        }

        private static void replaceElement(@NotNull JsonObject root, @NotNull LenientFieldInfo info, @NotNull JsonElement replacement) {
            if (info.getPathSegments() != null) {
                JsonElement current = root;

                for (int i = 0; i < info.getPathSegments().size() - 1; i++) {
                    if (current == null || !current.isJsonObject())
                        return;

                    current = current.getAsJsonObject().get(info.getPathSegments().get(i));
                }

                if (current != null && current.isJsonObject())
                    current.getAsJsonObject().add(info.getPathSegments().getLast(), replacement);
            } else
                root.add(info.getSerializedName(), replacement);
        }

    }

    private record FieldOverflow(@NotNull String fieldName, @NotNull JsonElement overflow) { }

    @Getter
    private static final class LenientFieldInfo {

        private final @NotNull FieldAccessor<?> accessor;
        private final @NotNull String fieldName;
        private final @NotNull String serializedName;
        private final @Nullable ConcurrentList<String> pathSegments;
        private final boolean map;
        private final @NotNull Type keyType;
        private final @NotNull Type valueType;
        private final @NotNull Type elementType;

        private LenientFieldInfo(@NotNull FieldAccessor<?> accessor) {
            this.accessor = accessor;
            this.fieldName = accessor.getName();
            this.serializedName = accessor.getAnnotation(SerializedName.class)
                .map(SerializedName::value)
                .orElse(accessor.getName());
            this.pathSegments = accessor.getAnnotation(SerializedPath.class)
                .map(sp -> Concurrent.newList(StringUtil.split(sp.value(), ".")))
                .orElse(null);

            Type genericType = accessor.getGenericType();
            Class<?> rawType = accessor.getFieldType();
            this.map = Map.class.isAssignableFrom(rawType);

            if (genericType instanceof ParameterizedType parameterized) {
                Type[] typeArgs = parameterized.getActualTypeArguments();

                if (this.map && typeArgs.length >= 2) {
                    this.keyType = typeArgs[0];
                    this.valueType = typeArgs[1];
                    this.elementType = Object.class;
                } else if (!this.map && typeArgs.length >= 1) {
                    this.keyType = String.class;
                    this.valueType = Object.class;
                    this.elementType = typeArgs[0];
                } else {
                    this.keyType = Object.class;
                    this.valueType = Object.class;
                    this.elementType = Object.class;
                }
            } else {
                this.keyType = Object.class;
                this.valueType = Object.class;
                this.elementType = Object.class;
            }
        }

        private static @NotNull ConcurrentList<LenientFieldInfo> of(@NotNull Class<?> clazz) {
            Reflection<?> reflection = new Reflection<>(clazz);
            reflection.setProcessingSuperclass(false);
            ConcurrentList<LenientFieldInfo> result = Concurrent.newList();

            for (FieldAccessor<?> accessor : reflection.getFields()) {
                if (Modifier.isTransient(accessor.getModifiers()))
                    continue;

                if (accessor.hasAnnotation(Capture.class))
                    continue;

                if (accessor.hasAnnotation(Lenient.class)) {
                    Type genericType = accessor.getGenericType();

                    // Skip raw types without generics
                    if (!(genericType instanceof ParameterizedType))
                        continue;

                    result.add(new LenientFieldInfo(accessor));
                }
            }

            return result;
        }

    }

}
