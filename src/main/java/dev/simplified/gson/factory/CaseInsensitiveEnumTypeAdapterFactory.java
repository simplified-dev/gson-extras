package dev.simplified.gson.factory;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.simplified.gson.annotation.Fallback;
import dev.simplified.gson.exception.JsonException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link TypeAdapterFactory} that deserializes enum constants using
 * case-insensitive name matching.
 * <p>
 * On serialization, constants annotated with {@link SerializedName} use
 * their declared serialized name; all others use {@link Enum#name()}.
 * On deserialization, incoming values are matched against constant names
 * and {@link SerializedName} values (including alternates) without
 * regard to case.
 */
public final class CaseInsensitiveEnumTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> @Nullable TypeAdapter<T> create(@NotNull Gson gson, @NotNull TypeToken<T> type) {
        Class<T> rawType = (Class<T>) type.getRawType();

        if (!rawType.isEnum())
            return null;

        return (TypeAdapter<T>) new CaseInsensitiveEnumTypeAdapter(rawType);
    }

    /**
     * Returns whether a value read as the given type is that enum's fallback constant rather than a
     * declared match.
     * <p>
     * Compatibility checks elsewhere in the library treat a non-null enum read as "the wire named
     * something we know". A fallback resolves an unrecognized name, so it has to be judged the same
     * way a {@code null} was, or an entry that used to reach overflow losslessly would start binding
     * onto the marker and the original value would be lost.
     *
     * @param gson the gson instance whose adapter cache resolves the type
     * @param type the declared type the value was read as
     * @param value the value produced by reading that type
     * @return {@code true} when the value is the enum's fallback constant
     */
    public static boolean isFallback(@NotNull Gson gson, @NotNull Type type, @Nullable Object value) {
        if (value == null)
            return false;

        return gson.getAdapter(TypeToken.get(type)) instanceof CaseInsensitiveEnumTypeAdapter<?> adapter
            && value == adapter.fallback;
    }

    private static class CaseInsensitiveEnumTypeAdapter<E extends Enum<E>> extends TypeAdapter<E> {

        private final Map<String, E> nameToConstant = new HashMap<>();
        private final Map<E, String> constantToName = new HashMap<>();
        private final @Nullable E fallback;

        CaseInsensitiveEnumTypeAdapter(@NotNull Class<E> enumClass) {
            E resolved = null;

            for (E constant : enumClass.getEnumConstants()) {
                String writeName = constant.name();

                try {
                    Field field = enumClass.getField(constant.name());
                    SerializedName annotation = field.getAnnotation(SerializedName.class);

                    if (annotation != null) {
                        writeName = annotation.value();

                        for (String alternate : annotation.alternate())
                            nameToConstant.put(alternate.toUpperCase(), constant);
                    }

                    if (field.isAnnotationPresent(Fallback.class)) {
                        if (resolved != null)
                            throw new JsonException("Enum '%s' declares more than one fallback constant", enumClass.getName());

                        resolved = constant;
                    }
                } catch (NoSuchFieldException ignored) { }

                constantToName.put(constant, writeName);
                nameToConstant.put(writeName.toUpperCase(), constant);
                nameToConstant.put(constant.name().toUpperCase(), constant);
            }

            this.fallback = resolved;
        }

        @Override
        public void write(@NotNull JsonWriter out, @Nullable E value) throws IOException {
            if (value == null)
                out.nullValue();
            else
                out.value(constantToName.get(value));
        }

        @Override
        public @Nullable E read(@NotNull JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            E constant = nameToConstant.get(in.nextString().toUpperCase());
            return constant != null ? constant : this.fallback;
        }

    }

}
