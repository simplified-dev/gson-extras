package dev.simplified.gson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.simplified.gson.exception.JsonException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * A {@link TypeAdapter} base that centralises null-safety and malformed-input handling.
 *
 * <p>Reading a {@link JsonToken#NULL NULL} token yields {@code null} and writing a {@code null}
 * value emits {@link JsonWriter#nullValue()}, so a subclass never sees a null on either side.
 * A value-level {@link RuntimeException} thrown by {@link #readValue(JsonReader)} - a parse failure
 * such as a bad number or an unparseable string - is wrapped in a {@link JsonException} so the
 * module presents one failure vocabulary; a structural {@link IOException} and an already-thrown
 * {@link JsonException} propagate unchanged.
 *
 * <p>Subclasses implement only the non-null value logic in {@link #writeValue(JsonWriter, Object)}
 * and {@link #readValue(JsonReader)}.
 *
 * @param <T> the value type this adapter reads and writes
 */
public abstract class SafeTypeAdapter<T> extends TypeAdapter<T> {

    /**
     * Human-readable label for the value type, interpolated into the malformed-input message.
     */
    private final @NotNull String typeLabel;

    /**
     * Constructs a new {@code SafeTypeAdapter} labelled with the given type name.
     *
     * @param typeLabel the value type label used in malformed-input messages
     */
    protected SafeTypeAdapter(@NotNull String typeLabel) {
        this.typeLabel = typeLabel;
    }

    /**
     * Writes a non-null value. Never invoked with {@code null} - the base emits
     * {@link JsonWriter#nullValue()} for that case.
     *
     * @param out the writer to emit to
     * @param value the non-null value to write
     * @throws IOException if the writer fails
     */
    protected abstract void writeValue(@NotNull JsonWriter out, @NotNull T value) throws IOException;

    /**
     * Reads a non-null value. Never invoked on a {@link JsonToken#NULL NULL} token - the base
     * consumes that and returns {@code null}.
     *
     * @param in the reader positioned on the value
     * @return the decoded value
     * @throws IOException if the reader fails structurally
     */
    protected abstract @NotNull T readValue(@NotNull JsonReader in) throws IOException;

    /** {@inheritDoc} */
    @Override
    public final void write(@NotNull JsonWriter out, @Nullable T value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        this.writeValue(out, value);
    }

    /** {@inheritDoc} */
    @Override
    public final @Nullable T read(@NotNull JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        try {
            return this.readValue(in);
        } catch (IOException | JsonException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new JsonException(ex, "malformed %s value", this.typeLabel);
        }
    }

}
