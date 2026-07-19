package dev.simplified.gson.adapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.simplified.gson.exception.JsonException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gson {@link TypeAdapter} for {@link Instant} values serialized in RFC 822 / RFC 2822
 * date format, the canonical date representation used by RSS 2.0 {@code <pubDate>} and
 * {@code <lastBuildDate>} fields.
 * <p>
 * The format looks like {@code Sun, 05 Apr 2026 12:56:02 +0000}. Java's
 * {@link DateTimeFormatter#RFC_1123_DATE_TIME} parses both RFC 822 and its successor
 * RFC 1123/2822 forms, which covers every feed generator encountered in the wild.
 * <p>
 * Applied per-field via {@link JsonAdapter @JsonAdapter} so
 * the global {@link Gson Gson} instance can continue using its
 * default handling for every other {@link Instant} field across the codebase
 * (epoch-millis for Hypixel API responses, ISO-8601 elsewhere).
 * <p>
 * Null-safe both directions and malformed-input safe (an unparseable value yields a
 * {@link JsonException}) via {@link SafeTypeAdapter}.
 */
public final class Rfc822InstantAdapter extends SafeTypeAdapter<Instant> {

    private static final @NotNull DateTimeFormatter FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME;

    /**
     * Constructs a new {@code Rfc822InstantAdapter}.
     */
    public Rfc822InstantAdapter() {
        super("Instant (RFC-822)");
    }

    @Override
    protected void writeValue(@NotNull JsonWriter out, @NotNull Instant value) throws IOException {
        out.value(FORMAT.format(value.atOffset(ZoneOffset.UTC)));
    }

    @Override
    protected @NotNull Instant readValue(@NotNull JsonReader in) throws IOException {
        return ZonedDateTime.parse(in.nextString(), FORMAT).toInstant();
    }

}
