package dev.simplified.gson.adapter;

import dev.simplified.gson.exception.JsonException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the {@link SafeTypeAdapter} null-safety and malformed-input wrapping for the migrated
 * adapters ({@link InstantTypeAdapter}, {@link OffsetDateTimeTypeAdapter}, {@link UUIDTypeAdapter},
 * {@link Rfc822InstantAdapter}): a {@code null} token round-trips to {@code null}, and a value-level
 * parse failure surfaces a {@link JsonException} instead of a raw parse exception.
 */
@DisplayName("SafeTypeAdapter null + malformed hardening")
class SafeAdapterHardeningTest {

    @Test
    @DisplayName("InstantTypeAdapter round-trips, null-safe, wraps a non-numeric token")
    void instant() throws IOException {
        InstantTypeAdapter adapter = new InstantTypeAdapter();
        assertEquals(Instant.ofEpochMilli(1_712_345_678_000L),
            adapter.fromJson(Long.toString(1_712_345_678_000L)));
        assertEquals("1712345678000", adapter.toJson(Instant.ofEpochMilli(1_712_345_678_000L)));
        assertNull(adapter.fromJson("null"));
        assertEquals("null", adapter.toJson(null));
        assertThrows(JsonException.class, () -> adapter.fromJson("\"not-a-number\""));
    }

    @Test
    @DisplayName("OffsetDateTimeTypeAdapter round-trips, null-safe, wraps an unparseable value")
    void offsetDateTime() throws IOException {
        OffsetDateTimeTypeAdapter adapter = new OffsetDateTimeTypeAdapter();
        OffsetDateTime value = OffsetDateTime.parse("2026-04-05T12:56:02+00:00");
        assertEquals(value, adapter.fromJson(adapter.toJson(value)));
        assertNull(adapter.fromJson("null"));
        assertEquals("null", adapter.toJson(null));
        assertThrows(JsonException.class, () -> adapter.fromJson("\"not-a-date\""));
    }

    @Test
    @DisplayName("UUIDTypeAdapter round-trips, null-safe, wraps an unparseable value")
    void uuid() throws IOException {
        UUIDTypeAdapter adapter = new UUIDTypeAdapter();
        // StringUtil.toUUID only accepts version-4 UUIDs (its regex pins the version + variant nibbles).
        UUID value = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
        assertEquals(value, adapter.fromJson(adapter.toJson(value)));
        assertNull(adapter.fromJson("null"));
        assertEquals("null", adapter.toJson(null));
        assertThrows(JsonException.class, () -> adapter.fromJson("\"not-a-uuid\""));
    }

    @Test
    @DisplayName("Rfc822InstantAdapter round-trips, null-safe, wraps an unparseable value")
    void rfc822() throws IOException {
        Rfc822InstantAdapter adapter = new Rfc822InstantAdapter();
        Instant value = Instant.ofEpochSecond(1_712_321_762L);
        assertEquals(value, adapter.fromJson(adapter.toJson(value)));
        assertNull(adapter.fromJson("null"));
        assertEquals("null", adapter.toJson(null));
        assertThrows(JsonException.class, () -> adapter.fromJson("\"not-a-date\""));
    }

}
