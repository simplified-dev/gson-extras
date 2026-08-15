package dev.simplified.gson.factory;

import com.google.gson.Gson;
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
import dev.simplified.gson.GsonSettings;
import dev.simplified.gson.annotation.Capture;
import dev.simplified.gson.annotation.Extract;
import dev.simplified.gson.annotation.Lenient;
import dev.simplified.gson.exception.JsonException;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.accessor.FieldAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Gson {@link TypeAdapterFactory} that fills {@link Extract @Extract} fields from the overflow a
 * {@link Lenient @Lenient} or {@link Capture @Capture} field left behind.
 * <p>
 * An {@code @Extract} field names a source field on the same class and a JSON key inside that
 * source. The source's own factory filters that key out during the read and hands it to
 * {@link Overflow}; this factory claims it from there and converts it into the companion field.
 * On the write the value is handed back to the overflow before the delegate runs, so the producer
 * merges it into its own correct place, and the companion field's own serialized key is dropped from
 * the output - the extracted value belongs inside its source, not beside it.
 * <p>
 * <b>This factory must nest outside {@link CaptureTypeAdapterFactory}</b>, which means it must be
 * registered <i>after</i> it in {@link GsonSettings#defaults()} - registration order runs opposite
 * to nesting depth. Both producers publish during the delegate call, so a claim made from anywhere
 * inside {@code @Capture} would run before the container it needs exists. Nothing enforces this
 * beyond the registration list and the tests that pin it.
 * <p>
 * An {@code @Extract} whose source cannot publish an overflow - a name that matches no field on the
 * class, a field on a superclass, or a field carrying neither annotation - is skipped rather than
 * rejected, and behaves as it always has: the companion field keeps its initialiser and its own key
 * stays in the output. A source in {@code @Capture} grouping mode is a permanent no-op for the same
 * reason, because grouping mode produces no overflow at all.
 *
 * @see Extract
 * @see Overflow
 */
@NoArgsConstructor
public final class ExtractTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> @Nullable TypeAdapter<T> create(@NotNull Gson gson, @NotNull TypeToken<T> typeToken) {
        ConcurrentList<ExtractFieldInfo> extractFields = ExtractFieldInfo.of(typeToken.getRawType());

        if (extractFields.isEmpty())
            return null;

        return new ExtractTypeAdapter<>(
            gson,
            gson.getDelegateAdapter(this, typeToken),
            gson.getAdapter(JsonElement.class),
            extractFields
        );
    }

    @Getter
    @RequiredArgsConstructor
    private static class ExtractTypeAdapter<T> extends TypeAdapter<T> {

        private final @NotNull Gson gson;
        private final @NotNull TypeAdapter<T> delegateAdapter;
        private final @NotNull TypeAdapter<JsonElement> jsonElementAdapter;
        private final @NotNull ConcurrentList<ExtractFieldInfo> extractFields;

        @Override
        public void write(@NotNull JsonWriter out, @Nullable T value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            // Hand every extracted value back to its source's overflow BEFORE delegating. Both
            // producers merge back inside that call and each puts the entry in its own correct
            // place, so this half of the write cannot wait for the tree to come back.
            for (ExtractFieldInfo info : this.getExtractFields()) {
                if (!info.isMapSource())
                    continue;

                Object extractValue = info.getAccessor().get(value);
                Object owner = info.getSourceAccessor().get(value);

                if (extractValue == null || owner == null)
                    continue;

                JsonElement overflow = Overflow.open(owner, info.getTarget(), JsonObject::new);

                if (!overflow.isJsonObject())
                    continue;

                JsonObject overflowObject = overflow.getAsJsonObject();
                JsonElement tree = this.getGson().toJsonTree(extractValue);

                if (!info.isRemainder())
                    overflowObject.add(info.getJsonKey(), tree);
                else if (tree.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : tree.getAsJsonObject().entrySet())
                        overflowObject.add(entry.getKey(), entry.getValue());
                }
            }

            JsonElement jsonTree = this.getDelegateAdapter().toJsonTree(value);

            if (!jsonTree.isJsonObject()) {
                this.getDelegateAdapter().write(out, value);
                return;
            }

            // The other half is tree work and wants the opposite end of the same call - after every
            // producer has merged. Outermost is the only position offering both ends.
            JsonObject jsonObject = jsonTree.getAsJsonObject();

            for (ExtractFieldInfo info : this.getExtractFields()) {
                if (info.isMapSource())
                    jsonObject.remove(info.getSerializedName());
            }

            this.getJsonElementAdapter().write(out, jsonObject);
        }

        @Override
        public @Nullable T read(@NotNull JsonReader in) throws IOException {
            // No buffering - a claim reads the overflow, never the tree, and by the time the
            // delegate returns every producer has published against a container that now exists.
            T value = this.getDelegateAdapter().read(in);

            if (value == null)
                return null;

            for (ExtractFieldInfo info : this.getExtractFields()) {
                Object owner = info.getSourceAccessor().get(value);

                if (owner == null)
                    continue;

                if (info.isRemainder()) {
                    JsonObject claimed = Overflow.claim(owner, info::matches);

                    if (!claimed.isEmpty())
                        this.assign(value, info, owner, claimed);
                } else {
                    JsonElement claimed = Overflow.claim(owner, info.getJsonKey());

                    if (claimed != null)
                        this.assign(value, info, owner, claimed);
                }
            }

            return value;
        }

        /**
         * Converts a claimed entry into its companion field, restoring it to the overflow when the
         * conversion fails so the entry survives into the merge-back instead of being lost from the
         * object and the document both. The field keeps its initialiser either way.
         */
        private void assign(@NotNull T value, @NotNull ExtractFieldInfo info, @NotNull Object owner, @NotNull JsonElement claimed) {
            try {
                info.getAccessor().set(value, this.getGson().fromJson(claimed, info.getAccessor().getGenericType()));
            } catch (Exception ex) {
                // a remainder conversion is all-or-nothing, so every claimed entry goes back
                if (info.isRemainder() && claimed.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : claimed.getAsJsonObject().entrySet())
                        Overflow.restore(owner, entry.getKey(), entry.getValue());
                } else
                    Overflow.restore(owner, info.getJsonKey(), claimed);
            }
        }

    }

    @Getter
    @RequiredArgsConstructor
    private static final class ExtractFieldInfo {

        private final @NotNull FieldAccessor<?> accessor;
        private final @NotNull FieldAccessor<?> sourceAccessor;
        private final @NotNull Overflow.Target target;
        private final @NotNull String serializedName;
        private final @NotNull String jsonKey;
        private final boolean remainder;
        private final @Nullable Pattern pattern;

        private boolean isMapSource() {
            return Map.class.isAssignableFrom(this.getSourceAccessor().getFieldType());
        }

        /**
         * Whether a remainder claim takes this key. A null pattern is the catch-all, which is what
         * makes an empty filter claim everything left.
         */
        private boolean matches(@NotNull String key) {
            return this.getPattern() == null || this.getPattern().matcher(key).find();
        }

        /**
         * Claims run most-specific first, so an exact claim and a catch-all coexist on one source
         * whichever order they are declared in - unlike {@code @Capture}, where field order decides.
         */
        private int getClaimOrder() {
            if (!this.isRemainder())
                return 0;

            return this.getPattern() != null ? 1 : 2;
        }

        private static @NotNull ConcurrentList<ExtractFieldInfo> of(@NotNull Class<?> clazz) {
            Reflection<?> reflection = new Reflection<>(clazz);
            reflection.setProcessingSuperclass(false);
            ConcurrentList<ExtractFieldInfo> result = Concurrent.newList();

            for (FieldAccessor<?> accessor : reflection.getFields()) {
                if (Modifier.isTransient(accessor.getModifiers()))
                    continue;

                Extract extract = accessor.getAnnotation(Extract.class).orElse(null);

                if (extract == null)
                    continue;

                String path = extract.value();
                int dotIndex = path.indexOf('.');
                boolean remainder = dotIndex < 1;

                if (!remainder && !extract.filter().isEmpty())
                    throw new JsonException("@Extract on '%s.%s' names a single key, so filter() must be empty", clazz.getSimpleName(), accessor.getName());

                FieldAccessor<?> source = findField(reflection, remainder ? path : path.substring(0, dotIndex));

                if (source == null)
                    continue;

                Overflow.Target target = targetOf(source);

                if (target == null)
                    continue;

                Pattern pattern = extract.filter().isEmpty() ? null : Pattern.compile(extract.filter());

                if (remainder && pattern == null)
                    rejectSecondCatchAll(clazz, result, source, accessor);

                result.add(new ExtractFieldInfo(
                    accessor,
                    source,
                    target,
                    accessor.getAnnotation(SerializedName.class).map(SerializedName::value).orElse(accessor.getName()),
                    remainder ? "" : path.substring(dotIndex + 1),
                    remainder,
                    pattern
                ));
            }

            result.sort(Comparator.comparingInt(ExtractFieldInfo::getClaimOrder));
            return result;
        }

        /**
         * Two catch-all remainders on one source is detectable and silently lossy - the second finds
         * nothing left - so it is rejected. Two overlapping filtered remainders is not detectable and
         * is a documented hazard instead.
         */
        private static void rejectSecondCatchAll(@NotNull Class<?> clazz, @NotNull ConcurrentList<ExtractFieldInfo> resolved, @NotNull FieldAccessor<?> source, @NotNull FieldAccessor<?> accessor) {
            for (ExtractFieldInfo existing : resolved) {
                if (existing.getClaimOrder() == 2 && existing.getSourceAccessor().getName().equals(source.getName()))
                    throw new JsonException("@Extract on '%s.%s' is a second catch-all remainder over '%s', which '%s' already claims", clazz.getSimpleName(), accessor.getName(), source.getName(), existing.getAccessor().getName());
            }
        }

        /**
         * Resolves the source by <b>Java field name</b>, never by {@code @SerializedName} - four of
         * the six adoption sites name a field whose serialized name differs.
         */
        private static @Nullable FieldAccessor<?> findField(@NotNull Reflection<?> reflection, @NotNull String fieldName) {
            for (FieldAccessor<?> accessor : reflection.getFields()) {
                if (accessor.getName().equals(fieldName))
                    return accessor;
            }

            return null;
        }

        /**
         * Mirrors the producer that owns the source. {@code @Capture} wins when a field carries
         * both, because that is the field the capture factory claims.
         */
        private static @Nullable Overflow.Target targetOf(@NotNull FieldAccessor<?> source) {
            if (source.hasAnnotation(Capture.class))
                return Overflow.Target.SOURCE_OBJECT;

            if (source.hasAnnotation(Lenient.class))
                return Overflow.Target.FIELD_ELEMENT;

            return null;
        }

    }

}
