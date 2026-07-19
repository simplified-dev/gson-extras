package dev.simplified.gson.adapter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.simplified.gson.GsonSettings;
import dev.simplified.gson.exception.JsonException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins for the unified {@link ColorTypeAdapter}: canonical {@code 0xAARRGGBB} write, lenient
 * multi-form read (packed ARGB int / hex / CSV / array / null), and malformed input surfacing a
 * {@link JsonException}.
 */
@DisplayName("ColorTypeAdapter unified codec")
class ColorTypeAdapterTest {

    private static final ColorTypeAdapter ADAPTER = new ColorTypeAdapter();

    @Test
    @DisplayName("writes the canonical 0xAARRGGBB uppercase form (ARGB order)")
    void writesCanonicalArgb() throws IOException {
        assertEquals("\"0xFF2552A5\"", ADAPTER.toJson(new Color(0xFF2552A5, true)));
        assertEquals("\"0x7F00FF00\"", ADAPTER.toJson(new Color(0x7F00FF00, true)));
    }

    @Test
    @DisplayName("write/read round-trips through the same ARGB channel (Bug 1 fix)")
    void roundTripsArgb() throws IOException {
        Color color = new Color(0x0A141E28, true);
        assertEquals(color, ADAPTER.fromJson(ADAPTER.toJson(color)));
    }

    @Test
    @DisplayName("reads an 8-digit hex string keeping its own alpha, forces FF on 6-or-fewer")
    void readsHexString() throws IOException {
        assertEquals(new Color(0xFF2552A5, true), ADAPTER.fromJson("\"0xFF2552A5\""));
        assertEquals(new Color(0xFFFF00FF, true), ADAPTER.fromJson("\"FF00FF\""));
        assertEquals(new Color(0xFFFF00FF, true), ADAPTER.fromJson("\"#FF00FF\""));
        assertEquals(new Color(0x7F00FF00, true), ADAPTER.fromJson("\"0x7F00FF00\""));
    }

    @Test
    @DisplayName("reads a packed ARGB int, a CSV form, and an array form")
    void readsOtherForms() throws IOException {
        int packed = 0xFF2552A5;
        assertEquals(new Color(packed, true), ADAPTER.fromJson(Integer.toString(packed)));
        assertEquals(new Color(37, 82, 165, 255), ADAPTER.fromJson("\"37,82,165\""));
        assertEquals(new Color(37, 82, 165, 128), ADAPTER.fromJson("\"37,82,165,128\""));
        assertEquals(new Color(37, 82, 165, 255), ADAPTER.fromJson("[37, 82, 165]"));
        assertEquals(new Color(37, 82, 165, 128), ADAPTER.fromJson("[37, 82, 165, 128]"));
    }

    @Test
    @DisplayName("null is safe both directions")
    void nullSafe() throws IOException {
        assertNull(ADAPTER.fromJson("null"));
        assertEquals("null", ADAPTER.toJson(null));
    }

    @Test
    @DisplayName("malformed input surfaces a JsonException, not a raw parse failure")
    void malformedThrows() {
        assertThrows(JsonException.class, () -> ADAPTER.fromJson("\"zzz\""));
        assertThrows(JsonException.class, () -> ADAPTER.fromJson("\"0x\""));
        assertThrows(JsonException.class, () -> ADAPTER.fromJson("\"#nothex\""));
        assertThrows(JsonException.class, () -> ADAPTER.fromJson("true"));
    }

    @Test
    @DisplayName("the static parse throws JsonException on malformed hex - the asset ArgbHex seam")
    void staticParse() {
        assertEquals(new Color(0xFFFF00FF, true), ColorTypeAdapter.parse("0xFF00FF"));
        assertEquals(new Color(0xFF000000, true), ColorTypeAdapter.parse("000000"));
        assertThrows(JsonException.class, () -> ColorTypeAdapter.parse("zzz"));
    }

    @Test
    @DisplayName("resolves through GsonSettings.defaults() - reflective scalar and Map value")
    void reflectsThroughDefaults() {
        Gson gson = GsonSettings.defaults().create();

        assertEquals(new Color(0xFF2552A5, true), gson.fromJson("\"0xFF2552A5\"", Color.class));

        Map<String, Color> effects = gson.fromJson(
            "{\"minecraft:absorption\":\"0xFF2552A5\",\"minecraft:blindness\":\"0xFF1F1F23\"}",
            new TypeToken<Map<String, Color>>() {}.getType());
        assertEquals(new Color(0xFF2552A5, true), effects.get("minecraft:absorption"));
        assertEquals(new Color(0xFF1F1F23, true), effects.get("minecraft:blindness"));
    }

}
