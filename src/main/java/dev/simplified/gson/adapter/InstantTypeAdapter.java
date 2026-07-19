package dev.simplified.gson.adapter;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.simplified.gson.exception.JsonException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;

/**
 * Gson codec for {@link Instant} values serialized as epoch milliseconds.
 *
 * <p>Null-safe both directions and malformed-input safe (a non-numeric token yields a
 * {@link JsonException}) via {@link SafeTypeAdapter}.
 */
public final class InstantTypeAdapter extends SafeTypeAdapter<Instant> {

    /**
     * Constructs a new {@code InstantTypeAdapter}.
     */
    public InstantTypeAdapter() {
        super("Instant");
    }

    @Override
    protected void writeValue(@NotNull JsonWriter out, @NotNull Instant value) throws IOException {
        out.value(value.toEpochMilli());
    }

    @Override
    protected @NotNull Instant readValue(@NotNull JsonReader in) throws IOException {
        return Instant.ofEpochMilli(in.nextLong());
    }

}
