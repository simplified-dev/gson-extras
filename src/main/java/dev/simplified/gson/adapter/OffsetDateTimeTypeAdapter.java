package dev.simplified.gson.adapter;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.simplified.gson.exception.JsonException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gson codec for {@link OffsetDateTime} values serialized in ISO-8601 offset form.
 *
 * <p>Null-safe both directions and malformed-input safe (an unparseable value yields a
 * {@link JsonException}) via {@link SafeTypeAdapter}.
 */
public final class OffsetDateTimeTypeAdapter extends SafeTypeAdapter<OffsetDateTime> {

    /**
     * Constructs a new {@code OffsetDateTimeTypeAdapter}.
     */
    public OffsetDateTimeTypeAdapter() {
        super("OffsetDateTime");
    }

    @Override
    protected void writeValue(@NotNull JsonWriter out, @NotNull OffsetDateTime value) throws IOException {
        out.value(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value));
    }

    @Override
    protected @NotNull OffsetDateTime readValue(@NotNull JsonReader in) throws IOException {
        return OffsetDateTime.parse(in.nextString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

}
