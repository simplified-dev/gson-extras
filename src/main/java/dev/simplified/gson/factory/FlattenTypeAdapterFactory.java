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
import dev.simplified.gson.annotation.Flatten;
import dev.simplified.gson.annotation.Lenient;
import dev.simplified.gson.annotation.SerializedPath;
import dev.simplified.gson.exception.JsonException;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.accessor.FieldAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

/**
 * Gson {@link TypeAdapterFactory} that collapses a single-key wrapper object out of every entry of a
 * {@link Flatten @Flatten} map or collection field, and puts it back on write.
 * <p>
 * The transform is a projection: only the named member survives the round trip, so a wrapper
 * carrying a sibling member reads fine and serializes back without it. That is declared on the
 * annotation rather than hidden here.
 * <p>
 * {@code create} returns {@code null} for a type carrying no {@code @Flatten} field, which keeps
 * every other type off this path and leaves the delegate resolution of neighbouring factories
 * undisturbed.
 *
 * @see Flatten
 */
@NoArgsConstructor
public final class FlattenTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> @Nullable TypeAdapter<T> create(@NotNull Gson gson, @NotNull TypeToken<T> typeToken) {
        ConcurrentList<FlattenFieldInfo> flattenFields = FlattenFieldInfo.of(typeToken.getRawType());

        if (flattenFields.isEmpty())
            return null;

        return new FlattenTypeAdapter<>(
            gson.getDelegateAdapter(this, typeToken),
            gson.getAdapter(JsonElement.class),
            flattenFields
        );
    }

    @Getter
    @RequiredArgsConstructor
    private static class FlattenTypeAdapter<T> extends TypeAdapter<T> {

        private final @NotNull TypeAdapter<T> delegateAdapter;
        private final @NotNull TypeAdapter<JsonElement> jsonElementAdapter;
        private final @NotNull ConcurrentList<FlattenFieldInfo> flattenFields;

        @Override
        public void write(@NotNull JsonWriter out, @Nullable T value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            JsonElement jsonTree = this.getDelegateAdapter().toJsonTree(value);

            if (!jsonTree.isJsonObject()) {
                this.getDelegateAdapter().write(out, value);
                return;
            }

            JsonObject jsonObject = jsonTree.getAsJsonObject();

            for (FlattenFieldInfo info : this.getFlattenFields()) {
                JsonElement fieldElement = jsonObject.get(info.getSerializedName());

                if (fieldElement == null)
                    continue;

                if (fieldElement.isJsonObject()) {
                    JsonObject wrapped = new JsonObject();

                    for (Map.Entry<String, JsonElement> entry : fieldElement.getAsJsonObject().entrySet())
                        wrapped.add(entry.getKey(), wrap(entry.getValue(), info.getMember()));

                    jsonObject.add(info.getSerializedName(), wrapped);
                } else if (fieldElement.isJsonArray()) {
                    JsonArray wrapped = new JsonArray();

                    for (JsonElement element : fieldElement.getAsJsonArray())
                        wrapped.add(wrap(element, info.getMember()));

                    jsonObject.add(info.getSerializedName(), wrapped);
                }
            }

            this.getJsonElementAdapter().write(out, jsonObject);
        }

        @Override
        public @Nullable T read(@NotNull JsonReader in) throws IOException {
            JsonElement rootElement = this.getJsonElementAdapter().read(in);

            if (!rootElement.isJsonObject())
                return this.getDelegateAdapter().fromJsonTree(rootElement);

            JsonObject rootObject = rootElement.getAsJsonObject();

            for (FlattenFieldInfo info : this.getFlattenFields()) {
                JsonElement fieldElement = rootObject.get(info.getSerializedName());

                if (fieldElement == null)
                    continue;

                if (fieldElement.isJsonObject()) {
                    JsonObject collapsed = new JsonObject();

                    for (Map.Entry<String, JsonElement> entry : fieldElement.getAsJsonObject().entrySet())
                        collapsed.add(entry.getKey(), unwrap(entry.getValue(), info.getMember()));

                    rootObject.add(info.getSerializedName(), collapsed);
                } else if (fieldElement.isJsonArray()) {
                    JsonArray collapsed = new JsonArray();

                    for (JsonElement element : fieldElement.getAsJsonArray())
                        collapsed.add(unwrap(element, info.getMember()));

                    rootObject.add(info.getSerializedName(), collapsed);
                }
            }

            return this.getDelegateAdapter().fromJsonTree(rootObject);
        }

        /**
         * Takes the named member out of a wrapper, leaving an entry that is already collapsed - or
         * one whose wrapper does not carry the member - exactly as it is.
         */
        private static @NotNull JsonElement unwrap(@NotNull JsonElement element, @NotNull String member) {
            if (!element.isJsonObject())
                return element;

            JsonObject wrapper = element.getAsJsonObject();
            return wrapper.has(member) ? wrapper.get(member) : element;
        }

        /**
         * Puts a value back inside a fresh wrapper. Unlike {@code unwrap} this is unconditional, so
         * a half-wrapped document normalises to fully wrapped.
         */
        private static @NotNull JsonElement wrap(@NotNull JsonElement element, @NotNull String member) {
            if (element.isJsonNull())
                return element;

            JsonObject wrapper = new JsonObject();
            wrapper.add(member, element);

            return wrapper;
        }

    }

    @Getter
    private static final class FlattenFieldInfo {

        private final @NotNull FieldAccessor<?> accessor;
        private final @NotNull String serializedName;
        private final @NotNull String member;

        private FlattenFieldInfo(@NotNull FieldAccessor<?> accessor) {
            this.accessor = accessor;
            this.serializedName = accessor.getAnnotation(SerializedName.class)
                .map(SerializedName::value)
                .orElse(accessor.getName());
            this.member = accessor.getAnnotation(Flatten.class)
                .map(Flatten::value)
                .orElse("");
        }

        private static @NotNull ConcurrentList<FlattenFieldInfo> of(@NotNull Class<?> clazz) {
            Reflection<?> reflection = new Reflection<>(clazz);
            reflection.setProcessingSuperclass(false);
            ConcurrentList<FlattenFieldInfo> result = Concurrent.newList();

            for (FieldAccessor<?> accessor : reflection.getFields()) {
                if (Modifier.isTransient(accessor.getModifiers()))
                    continue;

                if (!accessor.hasAnnotation(Flatten.class))
                    continue;

                Class<?> rawType = accessor.getFieldType();

                // every rejection here is a declaration nobody can have written yet, so throwing
                // costs no existing consumer and closing the combination is cheaper than a
                // half-working one
                if (!Map.class.isAssignableFrom(rawType) && !Collection.class.isAssignableFrom(rawType))
                    throw new JsonException("Field '%s' carries @Flatten but is neither a Map nor a Collection", accessor.getName());

                if (accessor.hasAnnotation(Capture.class))
                    throw new JsonException("Field '%s' cannot carry both @Flatten and @Capture", accessor.getName());

                if (accessor.hasAnnotation(Lenient.class))
                    throw new JsonException("Field '%s' cannot carry both @Flatten and @Lenient", accessor.getName());

                if (accessor.hasAnnotation(SerializedPath.class))
                    throw new JsonException("Field '%s' cannot carry both @Flatten and @SerializedPath", accessor.getName());

                FlattenFieldInfo info = new FlattenFieldInfo(accessor);

                if (info.getMember().isEmpty())
                    throw new JsonException("Field '%s' carries @Flatten with an empty member name", accessor.getName());

                result.add(info);
            }

            return result;
        }

    }

}
