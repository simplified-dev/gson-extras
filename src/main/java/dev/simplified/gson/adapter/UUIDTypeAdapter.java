package dev.simplified.gson.adapter;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.simplified.gson.exception.JsonException;
import dev.simplified.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.UUID;

/**
 * Gson codec for {@link UUID} values serialized in canonical dashed form.
 *
 * <p>Null-safe both directions and malformed-input safe (an unparseable value yields a
 * {@link JsonException}) via {@link SafeTypeAdapter}.
 */
public final class UUIDTypeAdapter extends SafeTypeAdapter<UUID> {

    /**
     * Constructs a new {@code UUIDTypeAdapter}.
     */
    public UUIDTypeAdapter() {
        super("UUID");
    }

    @Override
    protected void writeValue(@NotNull JsonWriter out, @NotNull UUID value) throws IOException {
        out.value(value.toString());
    }

    @Override
    protected @NotNull UUID readValue(@NotNull JsonReader in) throws IOException {
        return StringUtil.toUUID(in.nextString());
    }

}
