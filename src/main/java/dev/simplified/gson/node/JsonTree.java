package dev.simplified.gson.node;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.tuple.pair.PairOptional;
import dev.simplified.collection.tuple.pair.PairStream;
import dev.simplified.collection.tuple.single.SingleStream;
import dev.simplified.gson.GsonSettings;
import dev.simplified.gson.exception.JsonException;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The unified JSON self-builder - ONE type for building (append-as-you-go; insertion order IS the
 * byte-stability contract), null-safe total reading, and the single write path. A self-contained
 * home for JSON handling: build a tree and emit it, or parse bytes and read them back.
 *
 * <p><b>Float-only rule</b>: every fractional number is a Java {@code float} written via
 * {@link #put(String, float)} - NO {@code double}/{@code Number} overload exists, because a
 * {@code Float} and a {@code Double} serialise differently under Gson. Deliberately-integral
 * fields use the explicit {@link #putInt} channel; packed colours are {@code "0xAARRGGBB"}
 * strings via {@link #putHex} (Gson cannot round-trip {@code 0x80000000}-class ints).
 *
 * <p>Reads are TOTAL: an absent key or a wrong-kind node yields an empty {@link Optional}, an empty
 * stream, or the supplied default - never a throw. {@link #toGson()} is the sole escape hatch for
 * consumers that still need a raw Gson element; {@link #as(Class)} deserialises the whole node into
 * a typed DTO through the bound default Gson.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "element")
public final class JsonTree {

    /**
     * The bound default {@link Codec}, derived from {@link GsonSettings#defaults()}. Every no-arg
     * static factory and every bare instance decode/write delegates to it; a consumer needing its own
     * escaping / null-policy / adapters obtains its own {@link Codec} via {@link #using(GsonSettings)}.
     */
    public static final @NotNull Codec DEFAULT = Codec.of(GsonSettings.defaults());

    private final @NotNull JsonElement element;

    // ------------------------------------------------------------------------------------
    // build
    // ------------------------------------------------------------------------------------

    /**
     * A fresh object node.
     */
    public static @NotNull JsonTree object() {
        return new JsonTree(new JsonObject());
    }

    /**
     * A fresh array node.
     */
    public static @NotNull JsonTree array() {
        return new JsonTree(new JsonArray());
    }

    /**
     * Adds a string member.
     *
     * @param key the member name
     * @param value the string value
     * @return this node
     */
    public @NotNull JsonTree put(@NotNull String key, @NotNull String value) {
        asObject().addProperty(key, value);
        return this;
    }

    /**
     * Adds a float member - the ONLY fractional channel (float-only rule).
     *
     * @param key the member name
     * @param value the float value
     * @return this node
     */
    public @NotNull JsonTree put(@NotNull String key, float value) {
        asObject().addProperty(key, value);
        return this;
    }

    /**
     * Adds a boolean member.
     *
     * @param key the member name
     * @param value the boolean value
     * @return this node
     */
    public @NotNull JsonTree put(@NotNull String key, boolean value) {
        asObject().addProperty(key, value);
        return this;
    }

    /**
     * Adds a nested node member.
     *
     * @param key the member name
     * @param value the node to nest
     * @return this node
     */
    public @NotNull JsonTree put(@NotNull String key, @NotNull JsonTree value) {
        asObject().add(key, value.element);
        return this;
    }

    /**
     * Adds a deliberately-integral member (uv, texture_size, offsets, rotation, layer_index,
     * format, atlas ints).
     *
     * @param key the member name
     * @param value the int value
     * @return this node
     */
    public @NotNull JsonTree putInt(@NotNull String key, int value) {
        asObject().addProperty(key, value);
        return this;
    }

    /**
     * Adds a packed ARGB colour as the {@code 0xAARRGGBB} uppercase-hex string.
     *
     * @param key the member name
     * @param argb the packed ARGB colour
     * @return this node
     */
    public @NotNull JsonTree putHex(@NotNull String key, int argb) {
        asObject().addProperty(key, String.format("0x%08X", argb));
        return this;
    }

    /**
     * Adds a nested node member only when {@code value} is non-null (the empty-vs-absent
     * rule: null means the key is omitted).
     *
     * @param key the member name
     * @param value the node, or {@code null} to omit
     * @return this node
     */
    public @NotNull JsonTree putIf(@NotNull String key, @Nullable JsonTree value) {
        if (value != null) asObject().add(key, value.element);
        return this;
    }

    /**
     * Adds a string member only when {@code value} is non-null.
     *
     * @param key the member name
     * @param value the string, or {@code null} to omit
     * @return this node
     */
    public @NotNull JsonTree putIf(@NotNull String key, @Nullable String value) {
        if (value != null) asObject().addProperty(key, value);
        return this;
    }

    /**
     * Adds a float member only when it differs from its default (mirror / grow / scale
     * omit-at-default emission).
     *
     * @param key the member name
     * @param value the float value
     * @param dflt the default the member is omitted at
     * @return this node
     */
    public @NotNull JsonTree putUnless(@NotNull String key, float value, float dflt) {
        if (value != dflt) asObject().addProperty(key, value);
        return this;
    }

    /**
     * Adds a float array member.
     *
     * @param key the member name
     * @param values the float values in order
     * @return this node
     */
    public @NotNull JsonTree putFloats(@NotNull String key, float @NotNull ... values) {
        JsonArray array = new JsonArray(values.length);
        for (float value : values) array.add(value);
        asObject().add(key, array);
        return this;
    }

    /**
     * Adds an int array member (the integral channel's array form).
     *
     * @param key the member name
     * @param values the int values in order
     * @return this node
     */
    public @NotNull JsonTree putInts(@NotNull String key, int @NotNull ... values) {
        JsonArray array = new JsonArray(values.length);
        for (int value : values) array.add(value);
        asObject().add(key, array);
        return this;
    }

    /**
     * Adds a string array member.
     *
     * @param key the member name
     * @param values the string values in order
     * @return this node
     */
    public @NotNull JsonTree putStrings(@NotNull String key, @NotNull String @NotNull ... values) {
        JsonArray array = new JsonArray(values.length);
        for (String value : values) array.add(value);
        asObject().add(key, array);
        return this;
    }

    /**
     * Opens the nested object under {@code key}, creating it on first use - the
     * append-as-you-go hook.
     *
     * @param key the member name
     * @return the nested object node
     */
    public @NotNull JsonTree child(@NotNull String key) {
        JsonObject object = asObject();
        JsonElement existing = object.get(key);
        if (existing == null) {
            JsonObject created = new JsonObject();
            object.add(key, created);
            return new JsonTree(created);
        }
        return new JsonTree(existing);
    }

    /**
     * Opens the nested array under {@code key}, creating it on first use.
     *
     * @param key the member name
     * @return the nested array node
     */
    public @NotNull JsonTree childArray(@NotNull String key) {
        JsonObject object = asObject();
        JsonElement existing = object.get(key);
        if (existing == null) {
            JsonArray created = new JsonArray();
            object.add(key, created);
            return new JsonTree(created);
        }
        return new JsonTree(existing);
    }

    /**
     * Appends a node to this array node.
     *
     * @param entry the node to append
     * @return this node
     */
    public @NotNull JsonTree add(@NotNull JsonTree entry) {
        asArray().add(entry.element);
        return this;
    }

    /**
     * Appends a string to this array node.
     *
     * @param entry the string to append
     * @return this node
     */
    public @NotNull JsonTree add(@NotNull String entry) {
        asArray().add(entry);
        return this;
    }

    /**
     * Appends a float to this array node.
     *
     * @param entry the float to append
     * @return this node
     */
    public @NotNull JsonTree add(float entry) {
        asArray().add(entry);
        return this;
    }

    /**
     * Shallow-merges another object node's members into this object - each top-level key is added
     * later-wins, with the source value deep-copied so the two trees never alias.
     *
     * @param other the object node whose members to merge in
     * @return this node
     */
    public @NotNull JsonTree putAll(@NotNull JsonTree other) {
        JsonObject target = asObject();
        for (Map.Entry<String, JsonElement> entry : other.asObject().entrySet())
            target.add(entry.getKey(), entry.getValue().deepCopy());
        return this;
    }

    /**
     * A deep copy of this node, structurally independent of the original.
     *
     * @return the deep-copied node
     */
    public @NotNull JsonTree deepCopy() {
        return new JsonTree(this.element.deepCopy());
    }

    // -- factories: scoped, leaf, one-shot --

    /**
     * Builds an object node inline, passing the fresh node to {@code body}.
     *
     * @param body the builder run on the new object node
     * @return the built object node
     */
    public static @NotNull JsonTree object(@NotNull Consumer<JsonTree> body) {
        JsonTree node = object();
        body.accept(node);
        return node;
    }

    /**
     * Builds an array node inline, passing the fresh node to {@code body}.
     *
     * @param body the builder run on the new array node
     * @return the built array node
     */
    public static @NotNull JsonTree array(@NotNull Consumer<JsonTree> body) {
        JsonTree node = array();
        body.accept(node);
        return node;
    }

    /**
     * A primitive leaf node holding a string.
     *
     * @param value the string value
     * @return the leaf node
     */
    public static @NotNull JsonTree of(@NotNull String value) {
        return new JsonTree(new JsonPrimitive(value));
    }

    /**
     * A primitive leaf node holding an int.
     *
     * @param value the int value
     * @return the leaf node
     */
    public static @NotNull JsonTree of(int value) {
        return new JsonTree(new JsonPrimitive(value));
    }

    /**
     * A primitive leaf node holding a float - the canonical fractional channel.
     *
     * @param value the float value
     * @return the leaf node
     */
    public static @NotNull JsonTree of(float value) {
        return new JsonTree(new JsonPrimitive(value));
    }

    /**
     * A primitive leaf node holding a boolean.
     *
     * @param value the boolean value
     * @return the leaf node
     */
    public static @NotNull JsonTree of(boolean value) {
        return new JsonTree(new JsonPrimitive(value));
    }

    /**
     * An array node holding the given strings in order.
     *
     * @param values the string values
     * @return the array node
     */
    public static @NotNull JsonTree arrayOf(@NotNull String @NotNull ... values) {
        return array().addStrings(List.of(values));
    }

    /**
     * An array node holding the given node entries in order.
     *
     * @param entries the node entries
     * @return the array node
     */
    public static @NotNull JsonTree arrayOf(@NotNull Iterable<JsonTree> entries) {
        return array().addAll(entries);
    }

    /**
     * An object node holding the given members in iteration order.
     *
     * @param members the member map
     * @return the object node
     */
    public static @NotNull JsonTree objectOf(@NotNull Map<String, JsonTree> members) {
        JsonTree node = object();
        members.forEach(node::put);
        return node;
    }

    /**
     * An explicit JSON-null node, distinct from an absent member.
     *
     * @return the null node
     */
    public static @NotNull JsonTree nullNode() {
        return new JsonTree(JsonNull.INSTANCE);
    }

    // -- put: null, first-wins, wide-number opt-in, conditional, bulk --

    /**
     * Adds an explicit JSON-null member (distinct from omitting the key).
     *
     * @param key the member name
     * @return this node
     */
    public @NotNull JsonTree putNull(@NotNull String key) {
        asObject().add(key, JsonNull.INSTANCE);
        return this;
    }

    /**
     * Adds a nested-node member only when {@code key} is not already present (first-wins).
     *
     * @param key the member name
     * @param value the node to nest when the key is absent
     * @return this node
     */
    public @NotNull JsonTree putIfAbsent(@NotNull String key, @NotNull JsonTree value) {
        JsonObject object = asObject();
        if (!object.has(key)) object.add(key, value.element);
        return this;
    }

    /**
     * Adds a double member - an opt-in wide channel OFF the byte-stable canonical path.
     *
     * <p>{@link #put(String, float)} stays THE fractional channel; a {@code double} and a
     * {@code float} of the same logical value serialise to different bytes, so reach for this only
     * when a 64-bit width is deliberately wanted.
     *
     * @param key the member name
     * @param value the double value
     * @return this node
     */
    public @NotNull JsonTree putDouble(@NotNull String key, double value) {
        asObject().addProperty(key, value);
        return this;
    }

    /**
     * Adds a 64-bit integral member - an opt-in wide channel.
     *
     * @param key the member name
     * @param value the long value
     * @return this node
     */
    public @NotNull JsonTree putLong(@NotNull String key, long value) {
        asObject().addProperty(key, value);
        return this;
    }

    /**
     * Adds an arbitrary {@link Number} member - an opt-in wide channel.
     *
     * @param key the member name
     * @param value the number value
     * @return this node
     */
    public @NotNull JsonTree putNumber(@NotNull String key, @NotNull Number value) {
        asObject().addProperty(key, value);
        return this;
    }

    /**
     * Adds a string member only when {@code cond} holds, staying in the fluent chain.
     *
     * @param cond whether to add the member
     * @param key the member name
     * @param value the string value
     * @return this node
     */
    public @NotNull JsonTree putWhen(boolean cond, @NotNull String key, @NotNull String value) {
        if (cond) asObject().addProperty(key, value);
        return this;
    }

    /**
     * Adds a node member only when {@code cond} holds, deferring value construction to the supplier.
     *
     * @param cond whether to add the member
     * @param key the member name
     * @param value supplies the node when {@code cond} holds
     * @return this node
     */
    public @NotNull JsonTree putWhen(boolean cond, @NotNull String key, @NotNull Supplier<JsonTree> value) {
        if (cond) asObject().add(key, value.get().element);
        return this;
    }

    /**
     * Adds a string-array member from a collection.
     *
     * @param key the member name
     * @param values the string values in order
     * @return this node
     */
    public @NotNull JsonTree put(@NotNull String key, @NotNull Collection<String> values) {
        JsonArray array = new JsonArray(values.size());
        for (String value : values) array.add(value);
        asObject().add(key, array);
        return this;
    }

    /**
     * Adds an array member from node entries.
     *
     * @param key the member name
     * @param entries the node entries in order
     * @return this node
     */
    public @NotNull JsonTree put(@NotNull String key, @NotNull Iterable<JsonTree> entries) {
        JsonArray array = new JsonArray();
        for (JsonTree entry : entries) array.add(entry.element);
        asObject().add(key, array);
        return this;
    }

    /**
     * Adds a nested-object member from a member map.
     *
     * @param key the member name
     * @param members the nested members
     * @return this node
     */
    public @NotNull JsonTree put(@NotNull String key, @NotNull Map<String, JsonTree> members) {
        JsonObject object = new JsonObject();
        members.forEach((k, v) -> object.add(k, v.element));
        asObject().add(key, object);
        return this;
    }

    /**
     * Splices a member stream in, preserving its encounter order.
     *
     * @param members the member stream to splice
     * @return this node
     */
    public @NotNull JsonTree putEach(@NotNull PairStream<String, JsonTree> members) {
        JsonObject object = asObject();
        members.forEach((k, v) -> object.add(k, v.element));
        return this;
    }

    // -- nested builders filled inline --

    /**
     * Opens the nested object under {@code key} and fills it inline via {@code body}, returning this
     * node for continued chaining.
     *
     * @param key the member name
     * @param body the builder run on the nested object
     * @return this node
     */
    public @NotNull JsonTree child(@NotNull String key, @NotNull Consumer<JsonTree> body) {
        body.accept(child(key));
        return this;
    }

    /**
     * Opens the nested array under {@code key} and fills it inline via {@code body}, returning this
     * node for continued chaining.
     *
     * @param key the member name
     * @param body the builder run on the nested array
     * @return this node
     */
    public @NotNull JsonTree childArray(@NotNull String key, @NotNull Consumer<JsonTree> body) {
        body.accept(childArray(key));
        return this;
    }

    // -- array append: bulk --

    /**
     * Appends several nodes to this array.
     *
     * @param entries the nodes to append
     * @return this node
     */
    public @NotNull JsonTree add(@NotNull JsonTree @NotNull ... entries) {
        JsonArray array = asArray();
        for (JsonTree entry : entries) array.add(entry.element);
        return this;
    }

    /**
     * Appends all node entries to this array.
     *
     * @param entries the node entries to append
     * @return this node
     */
    public @NotNull JsonTree addAll(@NotNull Iterable<? extends JsonTree> entries) {
        JsonArray array = asArray();
        for (JsonTree entry : entries) array.add(entry.element);
        return this;
    }

    /**
     * Appends a stream of nodes to this array - a {@link SingleStream} flows through this overload.
     *
     * @param entries the node stream to append
     * @return this node
     */
    public @NotNull JsonTree addAll(@NotNull Stream<? extends JsonTree> entries) {
        JsonArray array = asArray();
        entries.forEach(entry -> array.add(entry.toGson()));
        return this;
    }

    /**
     * Appends all strings to this array.
     *
     * @param values the strings to append
     * @return this node
     */
    public @NotNull JsonTree addStrings(@NotNull Collection<String> values) {
        JsonArray array = asArray();
        for (String value : values) array.add(value);
        return this;
    }

    /**
     * Appends all ints to this array.
     *
     * @param values the ints to append
     * @return this node
     */
    public @NotNull JsonTree addInts(int @NotNull ... values) {
        JsonArray array = asArray();
        for (int value : values) array.add(value);
        return this;
    }

    /**
     * Appends all floats to this array.
     *
     * @param values the floats to append
     * @return this node
     */
    public @NotNull JsonTree addFloats(float @NotNull ... values) {
        JsonArray array = asArray();
        for (float value : values) array.add(value);
        return this;
    }

    /**
     * Appends an explicit JSON null to this array.
     *
     * @return this node
     */
    public @NotNull JsonTree addNull() {
        asArray().add(JsonNull.INSTANCE);
        return this;
    }

    // -- stream collectors (the emit-side mirror of members()/elements()) --

    /**
     * A collector gathering a stream of nodes into an insertion-ordered array node.
     *
     * @return the array-node collector
     */
    public static @NotNull Collector<JsonTree, ?, JsonTree> toArray() {
        return Collector.of(JsonTree::array,
            (acc, node) -> acc.asArray().add(node.element),
            (a, b) -> {
                b.asArray().forEach(e -> a.asArray().add(e));
                return a;
            });
    }

    /**
     * A collector gathering a stream of (key, node) entries into an insertion-ordered object node.
     *
     * @return the object-node collector
     */
    public static @NotNull Collector<Map.Entry<String, JsonTree>, ?, JsonTree> toObject() {
        return Collector.of(JsonTree::object,
            (acc, entry) -> acc.asObject().add(entry.getKey(), entry.getValue().element),
            (a, b) -> {
                b.asObject().entrySet().forEach(e -> a.asObject().add(e.getKey(), e.getValue()));
                return a;
            });
    }

    // -- mutate: shrink + overlay --

    /**
     * Recursively deep-merges another object node into this one: matching object-valued keys descend,
     * every other key is added later-wins (the config-overlay merge).
     *
     * @param other the object node to merge in
     * @return this node
     */
    public @NotNull JsonTree merge(@NotNull JsonTree other) {
        JsonObject target = asObject();
        for (Map.Entry<String, JsonElement> entry : other.asObject().entrySet()) {
            JsonElement incoming = entry.getValue();
            JsonElement existing = target.get(entry.getKey());
            if (existing instanceof JsonObject && incoming instanceof JsonObject)
                new JsonTree(existing).merge(new JsonTree(incoming));
            else
                target.add(entry.getKey(), incoming.deepCopy());
        }
        return this;
    }

    /**
     * Removes the member under {@code key}, returning the removed node.
     *
     * @param key the member name
     * @return the removed node, or empty when absent or this is not an object
     */
    public @NotNull Optional<JsonTree> remove(@NotNull String key) {
        if (!(this.element instanceof JsonObject object)) return Optional.empty();
        JsonElement removed = object.remove(key);
        return removed == null ? Optional.empty() : Optional.of(new JsonTree(removed));
    }

    /**
     * Removes the array element at {@code index}, returning the removed node.
     *
     * @param index the zero-based element index
     * @return the removed node, or empty when out of range or this is not an array
     */
    public @NotNull Optional<JsonTree> removeAt(int index) {
        if (!(this.element instanceof JsonArray array) || index < 0 || index >= array.size()) return Optional.empty();
        return Optional.of(new JsonTree(array.remove(index)));
    }

    /**
     * Drops all members or elements from this container, returning this node.
     *
     * @return this node
     */
    public @NotNull JsonTree clear() {
        if (this.element instanceof JsonObject object)
            new ArrayList<>(object.keySet()).forEach(object::remove);
        else if (this.element instanceof JsonArray array)
            while (!array.isEmpty()) array.remove(0);
        return this;
    }

    // ------------------------------------------------------------------------------------
    // read - total: absent / wrong-kind yields empty or the supplied default, never a throw
    // ------------------------------------------------------------------------------------

    /**
     * Wraps an existing Gson element as a node - the sanctioned bridge for callers that assemble
     * Gson trees directly (the geometry parser's output edge).
     *
     * @param element the element to wrap
     * @return the wrapping node
     */
    public static @NotNull JsonTree wrap(@NotNull JsonElement element) {
        return new JsonTree(element);
    }

    /**
     * Parses UTF-8 JSON bytes into a node (member order preserved).
     *
     * @param utf8 the raw JSON bytes
     * @return the parsed node
     * @throws JsonException if the bytes are not valid JSON
     */
    public static @NotNull JsonTree parse(byte @NotNull [] utf8) {
        return parse(utf8, DEFAULT.read);
    }

    /**
     * Whether this object node carries a member under {@code key}.
     *
     * @param key the member name
     * @return {@code true} when this is an object with that member
     */
    public boolean has(@NotNull String key) {
        return this.element instanceof JsonObject object && object.has(key);
    }

    /** Whether this node is a JSON object. */
    public boolean isObject() {
        return this.element instanceof JsonObject;
    }

    /** Whether this node is a JSON array. */
    public boolean isArray() {
        return this.element instanceof JsonArray;
    }

    /** Whether this node is a JSON primitive (string, number, or boolean). */
    public boolean isPrimitive() {
        return this.element.isJsonPrimitive();
    }

    /**
     * The element count - an array's length or an object's member count, {@code 0} for a primitive.
     *
     * @return the child count
     */
    public int size() {
        if (this.element instanceof JsonArray array) return array.size();
        if (this.element instanceof JsonObject object) return object.size();
        return 0;
    }

    /**
     * The string member under {@code key}, or {@code dflt} when absent or not a primitive.
     *
     * @param key the member name
     * @param dflt the default
     * @return the string value, or {@code dflt}
     */
    public @NotNull String getString(@NotNull String key, @NotNull String dflt) {
        return findString(key).orElse(dflt);
    }

    /**
     * The float member under {@code key}, or {@code dflt} when absent.
     *
     * @param key the member name
     * @param dflt the default
     * @return the float value, or {@code dflt}
     */
    public float getFloat(@NotNull String key, float dflt) {
        JsonElement value = asObject().get(key);
        return value == null || !value.isJsonPrimitive() ? dflt : value.getAsFloat();
    }

    /**
     * The int member under {@code key}, or {@code dflt} when absent.
     *
     * @param key the member name
     * @param dflt the default
     * @return the int value, or {@code dflt}
     */
    public int getInt(@NotNull String key, int dflt) {
        JsonElement value = asObject().get(key);
        return value == null || !value.isJsonPrimitive() ? dflt : value.getAsInt();
    }

    /**
     * The member under {@code key} as a node, present only when this is an object carrying it.
     *
     * @param key the member name
     * @return the member node, or empty
     */
    public @NotNull Optional<JsonTree> find(@NotNull String key) {
        JsonElement member = memberOrNull(key);
        return member == null ? Optional.empty() : Optional.of(new JsonTree(member));
    }

    /**
     * The member under {@code key} as a node, present only when it is itself an object.
     *
     * @param key the member name
     * @return the object member node, or empty
     */
    public @NotNull Optional<JsonTree> findObject(@NotNull String key) {
        JsonElement member = memberOrNull(key);
        return member instanceof JsonObject ? Optional.of(new JsonTree(member)) : Optional.empty();
    }

    /**
     * The member under {@code key} as a node, present only when it is itself an array.
     *
     * @param key the member name
     * @return the array member node, or empty
     */
    public @NotNull Optional<JsonTree> findArray(@NotNull String key) {
        JsonElement member = memberOrNull(key);
        return member instanceof JsonArray ? Optional.of(new JsonTree(member)) : Optional.empty();
    }

    /**
     * The string member under {@code key}, present only when it is a primitive.
     *
     * @param key the member name
     * @return the string value, or empty
     */
    public @NotNull Optional<String> findString(@NotNull String key) {
        JsonElement member = memberOrNull(key);
        return member != null && member.isJsonPrimitive() ? Optional.of(member.getAsString()) : Optional.empty();
    }

    /**
     * The float member under {@code key}, present only when it is a numeric primitive.
     *
     * @param key the member name
     * @return the float value, or empty
     */
    public @NotNull Optional<Float> findFloat(@NotNull String key) {
        JsonElement member = memberOrNull(key);
        return isNumber(member) ? Optional.of(member.getAsFloat()) : Optional.empty();
    }

    /**
     * The int member under {@code key}, present only when it is a numeric primitive.
     *
     * @param key the member name
     * @return the int value, or empty
     */
    public @NotNull Optional<Integer> findInt(@NotNull String key) {
        JsonElement member = memberOrNull(key);
        return isNumber(member) ? Optional.of(member.getAsInt()) : Optional.empty();
    }

    /**
     * The boolean member under {@code key}, present only when it is a boolean primitive.
     *
     * @param key the member name
     * @return the boolean value, or empty
     */
    public @NotNull Optional<Boolean> findBoolean(@NotNull String key) {
        JsonElement member = memberOrNull(key);
        return member != null && member.isJsonPrimitive() && member.getAsJsonPrimitive().isBoolean()
            ? Optional.of(member.getAsBoolean()) : Optional.empty();
    }

    /**
     * This array node's elements, in order, empty on a non-array (skip-not-abort). Subsumes the old
     * {@code elements()} + {@code stream()} pair.
     *
     * @return the element nodes
     */
    public @NotNull SingleStream<JsonTree> elements() {
        return SingleStream.of(streamElements());
    }

    /**
     * This object node's members as (key, node) pairs, in insertion order, empty on a non-object.
     * Subsumes the old {@code members()} + {@code memberStream()} pair.
     *
     * @return the member pairs
     */
    public @NotNull PairStream<String, JsonTree> members() {
        return PairStream.of(streamMembers());
    }

    /**
     * This object node's keys, in insertion order, empty on a non-object.
     *
     * @return the member keys
     */
    public @NotNull SingleStream<String> keys() {
        if (!(this.element instanceof JsonObject object)) return SingleStream.empty();
        return SingleStream.of(new ArrayList<>(object.keySet()).stream());
    }

    // -- read additive: predicates, wide/hex/positional find, get(index), require* --

    /** Whether this node is an explicit JSON null. */
    public boolean isNull() {
        return this.element instanceof JsonNull;
    }

    /** Whether this container has no members or elements ({@code false} for a primitive). */
    public boolean isEmpty() {
        if (this.element instanceof JsonArray array) return array.isEmpty();
        if (this.element instanceof JsonObject object) return object.size() == 0;
        return false;
    }

    /**
     * The long member under {@code key}, present only when it is a numeric primitive.
     *
     * @param key the member name
     * @return the long value, or empty
     */
    public @NotNull Optional<Long> findLong(@NotNull String key) {
        JsonElement member = memberOrNull(key);
        return isNumber(member) ? Optional.of(member.getAsLong()) : Optional.empty();
    }

    /**
     * The member under {@code key} parsed from its {@code "0xAARRGGBB"} hex string (the
     * {@link #putHex} reader).
     *
     * @param key the member name
     * @return the packed int, or empty when absent or not a hex string
     */
    public @NotNull Optional<Integer> findHex(@NotNull String key) {
        return findString(key).flatMap(JsonTree::parseHex);
    }

    /**
     * The array element at {@code index} as a node.
     *
     * @param index the zero-based element index
     * @return the element node, or empty when out of range or this is not an array
     */
    public @NotNull Optional<JsonTree> findAt(int index) {
        if (!(this.element instanceof JsonArray array) || index < 0 || index >= array.size()) return Optional.empty();
        return Optional.of(new JsonTree(array.get(index)));
    }

    /**
     * The array element at {@code index} as a string.
     *
     * @param index the zero-based element index
     * @return the string value, or empty
     */
    public @NotNull Optional<String> findString(int index) {
        return findAt(index).flatMap(JsonTree::asString);
    }

    /**
     * The array element at {@code index} as an int.
     *
     * @param index the zero-based element index
     * @return the int value, or empty
     */
    public @NotNull Optional<Integer> findInt(int index) {
        return findAt(index).flatMap(JsonTree::asInt);
    }

    /**
     * The array element at {@code index} as a float.
     *
     * @param index the zero-based element index
     * @return the float value, or empty
     */
    public @NotNull Optional<Float> findFloat(int index) {
        return findAt(index).flatMap(JsonTree::asFloat);
    }

    /**
     * The long member under {@code key}, or {@code dflt} when absent or not numeric.
     *
     * @param key the member name
     * @param dflt the default
     * @return the long value, or {@code dflt}
     */
    public long getLong(@NotNull String key, long dflt) {
        JsonElement value = memberOrNull(key);
        return isNumber(value) ? value.getAsLong() : dflt;
    }

    /**
     * The boolean member under {@code key}, or {@code dflt} when absent.
     *
     * @param key the member name
     * @param dflt the default
     * @return the boolean value, or {@code dflt}
     */
    public boolean getBoolean(@NotNull String key, boolean dflt) {
        JsonElement value = memberOrNull(key);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : dflt;
    }

    /**
     * The {@code "0xAARRGGBB"} hex member under {@code key}, or {@code dflt} when absent or malformed.
     *
     * @param key the member name
     * @param dflt the default
     * @return the packed int, or {@code dflt}
     */
    public int getHex(@NotNull String key, int dflt) {
        return findHex(key).orElse(dflt);
    }

    /**
     * The array element at {@code index} as an int, or {@code dflt}.
     *
     * @param index the zero-based element index
     * @param dflt the default
     * @return the int value, or {@code dflt}
     */
    public int getInt(int index, int dflt) {
        return findInt(index).orElse(dflt);
    }

    /**
     * The array element at {@code index} as a float, or {@code dflt}.
     *
     * @param index the zero-based element index
     * @param dflt the default
     * @return the float value, or {@code dflt}
     */
    public float getFloat(int index, float dflt) {
        return findFloat(index).orElse(dflt);
    }

    /**
     * The object member under {@code key}, or a fresh empty object node - chain without an unwrap.
     *
     * @param key the member name
     * @return the object member, or a fresh empty object
     */
    public @NotNull JsonTree getObject(@NotNull String key) {
        return findObject(key).orElseGet(JsonTree::object);
    }

    /**
     * The array member under {@code key}, or a fresh empty array node - chain without an unwrap.
     *
     * @param key the member name
     * @return the array member, or a fresh empty array
     */
    public @NotNull JsonTree getArray(@NotNull String key) {
        return findArray(key).orElseGet(JsonTree::array);
    }

    /**
     * The mandatory string member under {@code key}.
     *
     * @param key the member name
     * @return the string value
     * @throws JsonException when the member is absent or not a string
     */
    public @NotNull String requireString(@NotNull String key) {
        return findString(key).orElseThrow(() -> missing(key, "string"));
    }

    /**
     * The mandatory int member under {@code key}.
     *
     * @param key the member name
     * @return the int value
     * @throws JsonException when the member is absent or not a number
     */
    public int requireInt(@NotNull String key) {
        return findInt(key).orElseThrow(() -> missing(key, "int"));
    }

    /**
     * The mandatory float member under {@code key}.
     *
     * @param key the member name
     * @return the float value
     * @throws JsonException when the member is absent or not a number
     */
    public float requireFloat(@NotNull String key) {
        return findFloat(key).orElseThrow(() -> missing(key, "float"));
    }

    /**
     * The mandatory object member under {@code key}.
     *
     * @param key the member name
     * @return the object member
     * @throws JsonException when the member is absent or not an object
     */
    public @NotNull JsonTree requireObject(@NotNull String key) {
        return findObject(key).orElseThrow(() -> missing(key, "object"));
    }

    /**
     * The mandatory array member under {@code key}.
     *
     * @param key the member name
     * @return the array member
     * @throws JsonException when the member is absent or not an array
     */
    public @NotNull JsonTree requireArray(@NotNull String key) {
        return findArray(key).orElseThrow(() -> missing(key, "array"));
    }

    // -- query: deep path, first-present, recursive streams, predicate lookup --

    /**
     * A total null-safe descent through the given segments - each an object key or a numeric array
     * index. Empty if any segment misses.
     *
     * @param segments the path segments
     * @return the reached node, or empty
     */
    public @NotNull Optional<JsonTree> findPath(@NotNull String @NotNull ... segments) {
        JsonTree current = this;
        for (String segment : segments) {
            Optional<JsonTree> next = current.step(segment);
            if (next.isEmpty()) return Optional.empty();
            current = next.get();
        }
        return Optional.of(current);
    }

    /**
     * The fail-loud twin of {@link #findPath} - descends the path, throwing at the first missing
     * segment.
     *
     * @param path the path segments
     * @return the reached node
     * @throws JsonException naming the first missing segment
     */
    public @NotNull JsonTree require(@NotNull String @NotNull ... path) {
        JsonTree current = this;
        for (String segment : path)
            current = current.step(segment)
                .orElseThrow(() -> new JsonException("Missing required path segment '%s'", segment));
        return current;
    }

    /**
     * A deep presence test without materialising the reached node.
     *
     * @param path the path segments
     * @return whether the full path resolves
     */
    public boolean contains(@NotNull String @NotNull ... path) {
        return findPath(path).isPresent();
    }

    /**
     * The first of {@code keys} that exists, as a node - for fields renamed across format versions.
     *
     * @param keys the candidate keys in preference order
     * @return the first present member, or empty
     */
    public @NotNull Optional<JsonTree> findFirst(@NotNull String @NotNull ... keys) {
        for (String key : keys) {
            Optional<JsonTree> found = find(key);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    /**
     * The first of {@code keys} that exists, as a string.
     *
     * @param keys the candidate keys in preference order
     * @return the first present string member, or empty
     */
    public @NotNull Optional<String> findFirstString(@NotNull String @NotNull ... keys) {
        for (String key : keys) {
            Optional<String> found = findString(key);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    /**
     * A pre-order stream of every nested node (self excluded).
     *
     * @return the descendant nodes in pre-order
     */
    public @NotNull SingleStream<JsonTree> descendants() {
        List<JsonTree> out = new ArrayList<>();
        collectDescendants(this, out);
        return SingleStream.of(out.stream());
    }

    /**
     * A pre-order stream of ({@code json-pointer}, node) pairs, self included - filter by path prefix
     * to scope a subtree.
     *
     * @return the (pointer, node) pairs in pre-order
     */
    public @NotNull PairStream<String, JsonTree> walk() {
        List<Map.Entry<String, JsonTree>> out = new ArrayList<>();
        walkPointer(this, "", out);
        return PairStream.of(out.stream());
    }

    /**
     * The first member matching a key+value predicate, both halves together.
     *
     * @param match the key+value predicate
     * @return the matching entry, or empty
     */
    public @NotNull PairOptional<String, JsonTree> findEntryPair(@NotNull BiPredicate<? super String, ? super JsonTree> match) {
        return PairStream.of(streamMembers()).filter(match).findFirstPair();
    }

    // -- stream additive: value/object projections, typed scalar-array streams --

    /**
     * This object node's values without keys, in insertion order, empty on a non-object.
     *
     * @return the member value nodes
     */
    public @NotNull SingleStream<JsonTree> values() {
        return SingleStream.of(streamMembers().map(Map.Entry::getValue));
    }

    /**
     * This array node's elements that are themselves objects, skipping primitives and nulls.
     *
     * @return the object elements in order
     */
    public @NotNull SingleStream<JsonTree> objects() {
        return SingleStream.of(streamElements().filter(JsonTree::isObject));
    }

    /**
     * The string elements of the array member under {@code key}, non-primitives skipped, as a stream.
     *
     * @param key the member name
     * @return the string elements in order
     */
    public @NotNull SingleStream<String> strings(@NotNull String key) {
        return SingleStream.of(arrayMemberElements(key).filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString));
    }

    /**
     * The int elements of the array member under {@code key}, non-numerics skipped, as a stream.
     *
     * @param key the member name
     * @return the int elements in order
     */
    public @NotNull SingleStream<Integer> ints(@NotNull String key) {
        return SingleStream.of(arrayMemberElements(key).filter(JsonTree::isNumber).map(JsonElement::getAsInt));
    }

    /**
     * The float elements of the array member under {@code key}, non-numerics skipped, as a stream.
     *
     * @param key the member name
     * @return the float elements in order
     */
    public @NotNull SingleStream<Float> floats(@NotNull String key) {
        return SingleStream.of(arrayMemberElements(key).filter(JsonTree::isNumber).map(JsonElement::getAsFloat));
    }

    // -- typed: whole-node decode, self coercions, enum, streaming + eager terminals --

    /**
     * Decodes this whole node into a parameterised shape ({@code List<Foo>}, {@code Map<String, Bar>})
     * - the erasure-safe decode.
     *
     * @param type the parameterised type token
     * @param <T> the decoded type
     * @return the decoded value
     */
    public <T> T as(@NotNull TypeToken<T> type) {
        return DEFAULT.read.fromJson(this.element, type.getType());
    }

    /**
     * A total whole-node decode into a DTO - empty on absent or malformed input.
     *
     * @param type the DTO class
     * @param <T> the DTO type
     * @return the decoded DTO, or empty
     */
    public <T> @NotNull Optional<T> asOptional(@NotNull Class<T> type) {
        try {
            return Optional.ofNullable(as(type));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /**
     * A whole-node decode into a DTO, or {@code dflt} on absent or malformed input.
     *
     * @param type the DTO class
     * @param dflt the default
     * @param <T> the DTO type
     * @return the decoded DTO, or {@code dflt}
     */
    public <T> T as(@NotNull Class<T> type, T dflt) {
        return asOptional(type).orElse(dflt);
    }

    /**
     * Decodes the member under {@code key} to a DTO, present only when the member exists.
     *
     * @param key the member name
     * @param type the DTO class
     * @param <T> the DTO type
     * @return the decoded DTO, or empty
     */
    public <T> @NotNull Optional<T> findAs(@NotNull String key, @NotNull Class<T> type) {
        return find(key).map(node -> node.as(type));
    }

    /** This primitive node as a string, present only when this is a primitive. */
    public @NotNull Optional<String> asString() {
        return this.element.isJsonPrimitive() ? Optional.of(this.element.getAsString()) : Optional.empty();
    }

    /** This primitive node as an int, present only when this is a numeric primitive. */
    public @NotNull Optional<Integer> asInt() {
        return isNumber(this.element) ? Optional.of(this.element.getAsInt()) : Optional.empty();
    }

    /** This primitive node as a boolean, present only when this is a boolean primitive. */
    public @NotNull Optional<Boolean> asBool() {
        return this.element.isJsonPrimitive() && this.element.getAsJsonPrimitive().isBoolean()
            ? Optional.of(this.element.getAsBoolean()) : Optional.empty();
    }

    /** This primitive node as a float, present only when this is a numeric primitive. */
    public @NotNull Optional<Float> asFloat() {
        return isNumber(this.element) ? Optional.of(this.element.getAsFloat()) : Optional.empty();
    }

    /**
     * This primitive node as a float, or {@code dflt} when it is not a JSON number.
     *
     * @param dflt the default
     * @return the float value, or {@code dflt}
     */
    public float asFloat(float dflt) {
        return isNumber(this.element) ? this.element.getAsFloat() : dflt;
    }

    /**
     * The member under {@code key} as an enum constant, case-tolerant via the house
     * case-insensitive enum factory.
     *
     * @param key the member name
     * @param type the enum class
     * @param <E> the enum type
     * @return the enum constant, or empty
     */
    public <E extends Enum<E>> @NotNull Optional<E> findEnum(@NotNull String key, @NotNull Class<E> type) {
        return find(key).flatMap(node -> node.asEnum(type));
    }

    /**
     * The member under {@code key} as an enum constant, or {@code dflt}.
     *
     * @param key the member name
     * @param dflt the default enum constant
     * @param <E> the enum type
     * @return the enum constant, or {@code dflt}
     */
    public <E extends Enum<E>> E getEnum(@NotNull String key, @NotNull E dflt) {
        return findEnum(key, dflt.getDeclaringClass()).orElse(dflt);
    }

    /**
     * This primitive node as an enum constant, case-tolerant.
     *
     * @param type the enum class
     * @param <E> the enum type
     * @return the enum constant, or empty
     */
    public <E extends Enum<E>> @NotNull Optional<E> asEnum(@NotNull Class<E> type) {
        if (!this.element.isJsonPrimitive()) return Optional.empty();
        try {
            return Optional.ofNullable(DEFAULT.read.fromJson(this.element, type));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /**
     * This array node's elements decoded to DTOs, lazily.
     *
     * @param type the DTO class
     * @param <T> the DTO type
     * @return the decoded elements
     */
    public <T> @NotNull SingleStream<T> elementsAs(@NotNull Class<T> type) {
        return SingleStream.of(streamElements().map(node -> node.as(type)));
    }

    /**
     * This object node's members decoded to (key, DTO) pairs, keys kept, in insertion order.
     *
     * @param type the DTO class
     * @param <V> the DTO type
     * @return the decoded members
     */
    public <V> @NotNull PairStream<String, V> membersAs(@NotNull Class<V> type) {
        return PairStream.of(streamMembers().map(entry ->
            new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue().as(type))));
    }

    /**
     * This whole array node decoded to a {@code List<DTO>}.
     *
     * @param type the DTO class
     * @param <E> the DTO type
     * @return the decoded list
     */
    public <E> @NotNull ConcurrentList<E> asList(@NotNull Class<E> type) {
        return elementsAs(type).toList();
    }

    /**
     * The array member under {@code key} decoded to a {@code List<DTO>}, empty on absent or not-array.
     *
     * @param key the member name
     * @param type the DTO class
     * @param <E> the DTO type
     * @return the decoded list, or empty
     */
    public <E> @NotNull List<E> getList(@NotNull String key, @NotNull Class<E> type) {
        Optional<JsonTree> array = findArray(key);
        return array.isPresent() ? array.get().asList(type) : List.of();
    }

    /**
     * The array member under {@code key} decoded to a {@code List<DTO>}, distinguishing absent from
     * empty.
     *
     * @param key the member name
     * @param type the DTO class
     * @param <E> the DTO type
     * @return the decoded list, present only when the member is an array
     */
    public <E> @NotNull Optional<List<E>> findList(@NotNull String key, @NotNull Class<E> type) {
        return findArray(key).<List<E>>map(node -> node.asList(type));
    }

    /**
     * This whole object node decoded to an insertion-ordered {@code id -> DTO} map.
     *
     * @param type the DTO class
     * @param <V> the DTO type
     * @return the decoded map
     */
    public <V> @NotNull ConcurrentMap<String, V> asMap(@NotNull Class<V> type) {
        return membersAs(type).toLinkedMap();
    }

    /**
     * The object member under {@code key} decoded to an {@code id -> DTO} map, empty on absent or
     * not-object.
     *
     * @param key the member name
     * @param type the DTO class
     * @param <V> the DTO type
     * @return the decoded map, or empty
     */
    public <V> @NotNull Map<String, V> getMap(@NotNull String key, @NotNull Class<V> type) {
        Optional<JsonTree> object = findObject(key);
        return object.isPresent() ? object.get().asMap(type) : Map.of();
    }

    /**
     * The object member under {@code key} decoded to a map, distinguishing absent from empty.
     *
     * @param key the member name
     * @param type the DTO class
     * @param <V> the DTO type
     * @return the decoded map, present only when the member is an object
     */
    public <V> @NotNull Optional<Map<String, V>> findMap(@NotNull String key, @NotNull Class<V> type) {
        return findObject(key).<Map<String, V>>map(node -> node.asMap(type));
    }

    // -- io additive: read-from-source, render-to-string, extra write sinks --

    /**
     * Parses a JSON string into a node.
     *
     * @param json the JSON text
     * @return the parsed node
     * @throws JsonException if the text is not valid JSON
     */
    public static @NotNull JsonTree parse(@NotNull String json) {
        return parse(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses JSON from a reader into a node.
     *
     * @param reader the reader to parse
     * @return the parsed node
     * @throws JsonException if the content is not valid JSON
     */
    public static @NotNull JsonTree parse(@NotNull Reader reader) {
        try {
            return new JsonTree(DEFAULT.read.fromJson(reader, JsonElement.class));
        } catch (RuntimeException ex) {
            throw new JsonException(ex, "Failed to parse JSON from reader");
        }
    }

    /**
     * Parses JSON from an input stream (UTF-8) into a node.
     *
     * @param in the input stream to parse
     * @return the parsed node
     * @throws JsonException if the stream cannot be read or is not valid JSON
     */
    public static @NotNull JsonTree parse(@NotNull InputStream in) {
        try {
            return parse(in.readAllBytes());
        } catch (IOException ex) {
            throw new JsonException(ex, "Failed to read JSON from stream");
        }
    }

    /**
     * Reads and parses a file into a node - the {@link #write(Path)} twin.
     *
     * @param file the file to read
     * @return the parsed node
     * @throws JsonException if the file cannot be read or parsed
     */
    public static @NotNull JsonTree read(@NotNull Path file) {
        return DEFAULT.read(file);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String toString() {
        return DEFAULT.read.toJson(this.element);
    }

    /**
     * This node serialised compactly.
     *
     * @return the compact JSON
     */
    public @NotNull String toJson() {
        return DEFAULT.read.toJson(this.element);
    }

    /**
     * This node serialised pretty (HTML escaping off) - the on-disk render.
     *
     * @return the pretty JSON
     */
    public @NotNull String toPrettyJson() {
        return DEFAULT.pretty.toJson(this.element);
    }

    /**
     * Writes this node compact or pretty, terminated by one platform newline, parent dirs created.
     *
     * @param file the output path
     * @param pretty {@code true} for the pretty on-disk form, {@code false} for compact
     * @throws JsonException if the file cannot be written
     */
    public void write(@NotNull Path file, boolean pretty) {
        if (pretty) {
            write(file);
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, DEFAULT.read.toJson(this.element) + System.lineSeparator());
        } catch (IOException ex) {
            throw new JsonException(ex, "Failed to write '%s'", file);
        }
    }

    /**
     * Writes this node (UTF-8, pretty) into an output stream - a zip entry, an HTTP response.
     *
     * @param out the stream to write into
     * @throws JsonException if the stream cannot be written
     */
    public void writeTo(@NotNull OutputStream out) {
        try {
            out.write((DEFAULT.pretty.toJson(this.element)).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new JsonException(ex, "Failed to write JSON to stream");
        }
    }

    /**
     * Writes this node (pretty) into a writer.
     *
     * @param writer the writer to write into
     */
    public void writeTo(@NotNull Writer writer) {
        DEFAULT.pretty.toJson(this.element, writer);
    }

    /**
     * This node as UTF-8 bytes (compact) - the {@link #parse(byte[])} symmetry partner.
     *
     * @return the UTF-8 JSON bytes
     */
    public byte @NotNull [] toBytes() {
        return DEFAULT.read.toJson(this.element).getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------------------------
    // io
    // ------------------------------------------------------------------------------------

    /**
     * Writes this node to {@code file} - THE single write path for every emitted JSON: shared PRETTY
     * Gson (HTML escaping off) terminated with the platform line separator, parent directories
     * created. Callers that want a wrote-log emit it themselves.
     *
     * @param file the output path
     * @throws JsonException if the directory or file cannot be written
     */
    public void write(@NotNull Path file) {
        DEFAULT.write(this, file);
    }

    /**
     * Deserialises this whole node into a typed DTO through the project Gson.
     *
     * @param type the DTO class
     * @param <T> the DTO type
     * @return the deserialised DTO
     */
    public <T> T as(@NotNull Class<T> type) {
        return DEFAULT.read.fromJson(this.element, type);
    }

    /**
     * The wrapped Gson element - the escape hatch for consumers that need Gson types directly.
     */
    public @NotNull JsonElement toGson() {
        return this.element;
    }

    // ------------------------------------------------------------------------------------
    // meta - Codec injection, POJO encode, per-call Gson seams, type discriminator
    // ------------------------------------------------------------------------------------

    /**
     * A {@link Codec} bound to caller-supplied settings, for a subsystem needing its own escaping,
     * null policy, or adapters distinct from {@link #DEFAULT}.
     *
     * @param settings the settings to bind
     * @return the bound codec
     */
    public static @NotNull Codec using(@NotNull GsonSettings settings) {
        return Codec.of(settings);
    }

    /**
     * Encodes any POJO / record / DTO into a tree through {@link #DEFAULT}, for post-editing before
     * emission.
     *
     * @param pojo the object to encode
     * @return the encoded tree
     */
    public static @NotNull JsonTree from(@NotNull Object pojo) {
        return new JsonTree(DEFAULT.read.toJsonTree(pojo));
    }

    /**
     * Seeds an object node from a map, each value routed through {@link #DEFAULT}.
     *
     * @param members the member map
     * @return the seeded object node
     */
    public static @NotNull JsonTree from(@NotNull Map<String, ?> members) {
        JsonObject object = new JsonObject();
        members.forEach((key, value) -> object.add(key, DEFAULT.read.toJsonTree(value)));
        return new JsonTree(object);
    }

    /**
     * Seeds an array node from a collection, each value routed through {@link #DEFAULT}.
     *
     * @param values the values to encode
     * @return the seeded array node
     */
    public static @NotNull JsonTree from(@NotNull Iterable<?> values) {
        JsonArray array = new JsonArray();
        for (Object value : values) array.add(DEFAULT.read.toJsonTree(value));
        return new JsonTree(array);
    }

    /**
     * Decodes this node through a supplied Gson - a one-site override without a bound {@link Codec}.
     *
     * @param type the DTO class
     * @param gson the Gson to decode through
     * @param <T> the DTO type
     * @return the decoded DTO
     */
    public <T> T as(@NotNull Class<T> type, @NotNull Gson gson) {
        return gson.fromJson(this.element, type);
    }

    /**
     * Parses UTF-8 JSON bytes through a supplied Gson.
     *
     * @param utf8 the raw JSON bytes
     * @param gson the Gson to parse through
     * @return the parsed node
     * @throws JsonException if the bytes are not valid JSON
     */
    public static @NotNull JsonTree parse(byte @NotNull [] utf8, @NotNull Gson gson) {
        try {
            return new JsonTree(gson.fromJson(new String(utf8, StandardCharsets.UTF_8), JsonElement.class));
        } catch (RuntimeException ex) {
            throw new JsonException(ex, "Failed to parse JSON (%d bytes)", utf8.length);
        }
    }

    /**
     * Writes this node through the supplied settings (pretty, HTML-escaping off).
     *
     * @param file the output path
     * @param settings the settings to write through
     * @throws JsonException if the file cannot be written
     */
    public void write(@NotNull Path file, @NotNull GsonSettings settings) {
        Codec.of(settings).write(this, file);
    }

    /**
     * The exhaustive JSON kind of this node - drives an exhaustive {@code switch}.
     *
     * @return this node's type
     */
    public @NotNull JsonType type() {
        if (this.element instanceof JsonObject) return JsonType.OBJECT;
        if (this.element instanceof JsonArray) return JsonType.ARRAY;
        if (this.element instanceof JsonNull) return JsonType.NULL;
        JsonPrimitive primitive = this.element.getAsJsonPrimitive();
        if (primitive.isString()) return JsonType.STRING;
        if (primitive.isBoolean()) return JsonType.BOOLEAN;
        return JsonType.NUMBER;
    }

    /**
     * The exhaustive JSON kinds - the range of {@link #type()}.
     */
    public enum JsonType {
        /** A JSON object. */
        OBJECT,
        /** A JSON array. */
        ARRAY,
        /** A string primitive. */
        STRING,
        /** A numeric primitive. */
        NUMBER,
        /** A boolean primitive. */
        BOOLEAN,
        /** An explicit JSON null. */
        NULL
    }

    /**
     * A bound read/pretty {@link Gson} pair - the injectable replacement for hardcoded static Gson.
     * {@link JsonTree#DEFAULT} binds one to {@link GsonSettings#defaults()}; obtain another via
     * {@link JsonTree#using(GsonSettings)}. The read Gson parses and DTO-decodes (full adapter stack);
     * the pretty Gson (HTML-escaping off) is the write path.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Codec {

        private final @NotNull Gson read;
        private final @NotNull Gson pretty;

        private static @NotNull Codec of(@NotNull GsonSettings settings) {
            return new Codec(settings.create(),
                settings.mutate().isPrettyPrint().isHtmlEscaping(false).build().create());
        }

        /**
         * A fresh object node; decode or write it through this codec's {@link #as(JsonTree, Class)} /
         * {@link #write(JsonTree, Path)}.
         *
         * @return a fresh object node
         */
        public @NotNull JsonTree object() {
            return JsonTree.object();
        }

        /**
         * A fresh array node.
         *
         * @return a fresh array node
         */
        public @NotNull JsonTree array() {
            return JsonTree.array();
        }

        /**
         * Parses UTF-8 JSON bytes through this codec's read Gson.
         *
         * @param utf8 the raw JSON bytes
         * @return the parsed node
         * @throws JsonException if the bytes are not valid JSON
         */
        public @NotNull JsonTree parse(byte @NotNull [] utf8) {
            return JsonTree.parse(utf8, this.read);
        }

        /**
         * Parses a JSON string through this codec's read Gson.
         *
         * @param json the JSON text
         * @return the parsed node
         * @throws JsonException if the text is not valid JSON
         */
        public @NotNull JsonTree parse(@NotNull String json) {
            return JsonTree.parse(json.getBytes(StandardCharsets.UTF_8), this.read);
        }

        /**
         * Reads and parses a file through this codec's read Gson.
         *
         * @param file the file to read
         * @return the parsed node
         * @throws JsonException if the file cannot be read or parsed
         */
        public @NotNull JsonTree read(@NotNull Path file) {
            try {
                return this.parse(Files.readAllBytes(file));
            } catch (IOException ex) {
                throw new JsonException(ex, "Failed to read '%s'", file);
            }
        }

        /**
         * Encodes a POJO into a tree through this codec's read Gson.
         *
         * @param pojo the object to encode
         * @return the encoded tree
         */
        public @NotNull JsonTree from(@NotNull Object pojo) {
            return new JsonTree(this.read.toJsonTree(pojo));
        }

        /**
         * Decodes a node into a DTO through this codec's read Gson.
         *
         * @param node the node to decode
         * @param type the DTO class
         * @param <T> the DTO type
         * @return the decoded DTO
         */
        public <T> T as(@NotNull JsonTree node, @NotNull Class<T> type) {
            return this.read.fromJson(node.element, type);
        }

        /**
         * Pretty-writes a node through this codec, terminated by one platform newline; parent
         * directories are created when the path carries a parent.
         *
         * @param node the node to write
         * @param file the output path
         * @throws JsonException if the file cannot be written
         */
        public void write(@NotNull JsonTree node, @NotNull Path file) {
            try {
                Path parent = file.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.writeString(file, this.pretty.toJson(node.element) + System.lineSeparator());
            } catch (IOException ex) {
                throw new JsonException(ex, "Failed to write '%s'", file);
            }
        }
    }

    private @Nullable JsonElement memberOrNull(@NotNull String key) {
        return this.element instanceof JsonObject object ? object.get(key) : null;
    }

    private static boolean isNumber(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    private @NotNull JsonObject asObject() {
        if (!(this.element instanceof JsonObject object))
            throw new IllegalStateException("Not an object node: " + this.element.getClass().getSimpleName());
        return object;
    }

    private @NotNull JsonArray asArray() {
        if (!(this.element instanceof JsonArray array))
            throw new IllegalStateException("Not an array node: " + this.element.getClass().getSimpleName());
        return array;
    }

    private @NotNull Optional<JsonTree> step(@NotNull String segment) {
        if (this.element instanceof JsonObject) return find(segment);
        if (this.element instanceof JsonArray array) {
            try {
                int index = Integer.parseInt(segment);
                if (index < 0 || index >= array.size()) return Optional.empty();
                return Optional.of(new JsonTree(array.get(index)));
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private @NotNull Stream<JsonTree> streamElements() {
        if (!(this.element instanceof JsonArray array)) return Stream.empty();
        return StreamSupport.stream(array.spliterator(), false).map(JsonTree::new);
    }

    private @NotNull Stream<Map.Entry<String, JsonTree>> streamMembers() {
        if (!(this.element instanceof JsonObject object)) return Stream.empty();
        return object.entrySet().stream()
            .map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), new JsonTree(entry.getValue())));
    }

    private @NotNull Stream<JsonElement> arrayMemberElements(@NotNull String key) {
        return memberOrNull(key) instanceof JsonArray array
            ? StreamSupport.stream(array.spliterator(), false) : Stream.empty();
    }

    private static void collectDescendants(@NotNull JsonTree node, @NotNull List<JsonTree> out) {
        if (node.element instanceof JsonObject object)
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonTree child = new JsonTree(entry.getValue());
                out.add(child);
                collectDescendants(child, out);
            }
        else if (node.element instanceof JsonArray array)
            for (JsonElement entry : array) {
                JsonTree child = new JsonTree(entry);
                out.add(child);
                collectDescendants(child, out);
            }
    }

    private static void walkPointer(@NotNull JsonTree node, @NotNull String pointer,
                                    @NotNull List<Map.Entry<String, JsonTree>> out) {
        out.add(new AbstractMap.SimpleImmutableEntry<>(pointer.isEmpty() ? "/" : pointer, node));
        if (node.element instanceof JsonObject object)
            for (Map.Entry<String, JsonElement> entry : object.entrySet())
                walkPointer(new JsonTree(entry.getValue()), pointer + "/" + entry.getKey(), out);
        else if (node.element instanceof JsonArray array)
            for (int i = 0; i < array.size(); i++)
                walkPointer(new JsonTree(array.get(i)), pointer + "/" + i, out);
    }

    private static @NotNull Optional<Integer> parseHex(@NotNull String text) {
        String digits = text.startsWith("0x") || text.startsWith("0X") ? text.substring(2)
            : text.startsWith("#") ? text.substring(1) : text;
        try {
            return Optional.of((int) Long.parseLong(digits, 16));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static @NotNull JsonException missing(@NotNull String key, @NotNull String kind) {
        return new JsonException("Missing required %s member '%s'", kind, key);
    }

}
