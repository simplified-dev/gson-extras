package dev.simplified.gson.adapter;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.simplified.gson.exception.JsonException;
import dev.simplified.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.io.IOException;

/**
 * Gson codec for {@link Color} sharing the {@code 0xAARRGGBB} wire format with the tree's hex channel.
 *
 * <p>Reads leniently - the inbound form is the 99% path (bundled resources, resource packs, APIs) -
 * dispatching on the JSON token:
 * <ul>
 *   <li><b>number</b> - a packed ARGB int</li>
 *   <li><b>string</b> - {@code 0x} / {@code #} / bare hex (6-or-fewer digits forced fully opaque,
 *       8 digits carry their own alpha), or a comma form {@code r,g,b} / {@code r,g,b,a}</li>
 *   <li><b>array</b> - {@code [r, g, b]} / {@code [r, g, b, a]}</li>
 *   <li><b>null</b> - {@code null} (handled by {@link SafeTypeAdapter})</li>
 * </ul>
 *
 * <p>Writes a single canonical {@code 0xAARRGGBB} form ({@code 0x%08X}, uppercase, ARGB). Malformed
 * input throws {@link JsonException} rather than substituting a default - a general codec surfaces a
 * data error and lets the caller choose a tolerant fallback.
 */
public final class ColorTypeAdapter extends SafeTypeAdapter<Color> {

    /**
     * Constructs a new {@code ColorTypeAdapter}.
     */
    public ColorTypeAdapter() {
        super("Color");
    }

    /**
     * Parses a string colour form - {@code 0x} / {@code #} / bare hex, or a comma form
     * {@code r,g,b} / {@code r,g,b,a} - into a {@link Color}.
     *
     * <p>A hex value of 6 or fewer digits is forced fully opaque (alpha {@code FF}); a longer value
     * carries its own alpha. This is the reusable entry point onto the codec's string parse for
     * callers that hold a raw hex string rather than a JSON stream.
     *
     * @param text the colour string
     * @return the parsed colour
     * @throws JsonException if {@code text} is not a valid hex or comma colour form
     */
    public static @NotNull Color parse(@NotNull String text) {
        return text.indexOf(',') >= 0 ? parseCsv(text) : parseHex(text);
    }

    @Override
    protected void writeValue(@NotNull JsonWriter out, @NotNull Color value) throws IOException {
        out.value(String.format("0x%08X", value.getRGB()));
    }

    @Override
    protected @NotNull Color readValue(@NotNull JsonReader in) throws IOException {
        return switch (in.peek()) {
            case NUMBER -> new Color(in.nextInt(), true);
            case STRING -> parse(in.nextString());
            case BEGIN_ARRAY -> readArray(in);
            default -> throw new JsonException(
                "expected a Color as a hex string, packed ARGB int, or [r,g,b(,a)] array, was %s", in.peek());
        };
    }

    private static @NotNull Color parseHex(@NotNull String text) {
        String digits = text.startsWith("0x") || text.startsWith("0X") ? text.substring(2)
            : text.startsWith("#") ? text.substring(1)
            : text;
        try {
            long value = Long.parseLong(digits, 16);
            if (digits.length() <= 6) value |= 0xFF000000L;
            return new Color((int) value, true);
        } catch (NumberFormatException ex) {
            throw new JsonException(ex, "malformed hex colour '%s' (expected 0xAARRGGBB / #RRGGBB / bare hex)", text);
        }
    }

    private static @NotNull Color parseCsv(@NotNull String text) {
        String[] parts = StringUtil.split(text, ',');
        try {
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            int a = parts.length >= 4 ? Integer.parseInt(parts[3].trim()) : 255;
            return new Color(r, g, b, a);
        } catch (RuntimeException ex) {
            throw new JsonException(ex, "malformed CSV colour '%s' (expected r,g,b or r,g,b,a)", text);
        }
    }

    private static @NotNull Color readArray(@NotNull JsonReader in) throws IOException {
        in.beginArray();
        int r = in.nextInt();
        int g = in.nextInt();
        int b = in.nextInt();
        int a = in.hasNext() ? in.nextInt() : 255;
        in.endArray();
        return new Color(r, g, b, a);
    }

}
