package dev.simplified.gson;

import com.google.gson.reflect.TypeToken;
import dev.simplified.collection.tuple.pair.PairOptional;
import dev.simplified.gson.exception.JsonException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the additive {@link JsonTree} redesign surface (P2): scoped builders and collectors, the
 * wide-number opt-in channels, the {@code find}/{@code get}/{@code require} taxonomy, deep-path
 * query, the tuple-stream and typed-decode families, the {@link JsonTree.Codec} injection, and IO
 * symmetry. The KEPT-method byte-stability contracts stay pinned by {@code JsonTreeTest}.
 */
@DisplayName("JsonTree additive redesign surface")
class JsonTreeAdditiveTest {

    private record Foo(String name, int n) {}

    private enum Facing { NORTH, SOUTH }

    @Test
    @DisplayName("scoped builders + collectors compose into insertion-ordered JSON")
    void scopedBuildersAndCollectors() {
        JsonTree model = JsonTree.object(root -> root
            .put("name", "wolf")
            .child("geometry", geo -> geo.putInt("format", 2))
            .put("bones", Stream.of("head", "body").map(JsonTree::of).collect(JsonTree.toArray())));
        assertEquals("{\"name\":\"wolf\",\"geometry\":{\"format\":2},\"bones\":[\"head\",\"body\"]}", model.toJson());

        JsonTree obj = Stream.of(Map.entry("a", JsonTree.of("1")), Map.entry("b", JsonTree.of(2)))
            .collect(JsonTree.toObject());
        assertEquals("{\"a\":\"1\",\"b\":2}", obj.toJson());
    }

    @Test
    @DisplayName("leaf + one-shot factories")
    void factories() {
        assertEquals("\"x\"", JsonTree.of("x").toJson());
        assertEquals("[\"a\",\"b\"]", JsonTree.arrayOf("a", "b").toJson());
        assertEquals("{\"k\":\"v\"}", JsonTree.objectOf(Map.of("k", JsonTree.of("v"))).toJson());
        assertEquals("null", JsonTree.nullNode().toJson());
        assertTrue(JsonTree.nullNode().isNull());
    }

    @Test
    @DisplayName("wide-number opt-in channels, first-wins, conditional, bulk puts")
    void puts() {
        assertEquals("{\"d\":0.5,\"l\":5000000000,\"n\":42}",
            JsonTree.object().putDouble("d", 0.5).putLong("l", 5_000_000_000L).putNumber("n", 42).toJson());
        assertEquals("{\"k\":\"first\"}",
            JsonTree.object().putIfAbsent("k", JsonTree.of("first")).putIfAbsent("k", JsonTree.of("second")).toJson());
        assertEquals("{\"on\":\"y\"}",
            JsonTree.object().putWhen(false, "off", "n").putWhen(true, "on", "y").toJson());
        assertEquals("{\"s\":[\"a\",\"b\"],\"o\":{\"x\":\"1\"}}",
            JsonTree.object().put("s", List.of("a", "b")).put("o", Map.of("x", JsonTree.of("1"))).toJson());
    }

    @Test
    @DisplayName("array bulk append")
    void arrayAppend() {
        assertEquals("[\"a\",\"b\",1,2,0.5,null]",
            JsonTree.array().addStrings(List.of("a", "b")).addInts(1, 2).addFloats(0.5f).addNull().toJson());
        assertEquals("[{\"i\":1},{\"i\":2}]",
            JsonTree.array().addAll(Stream.of(1, 2).map(i -> JsonTree.object().putInt("i", i))).toJson());
    }

    @Test
    @DisplayName("recursive merge descends matching object keys; remove/clear shrink")
    void mergeAndShrink() {
        JsonTree base = JsonTree.object()
            .put("a", JsonTree.object().put("x", "1").put("y", "2")).put("k", "base");
        JsonTree overlay = JsonTree.object()
            .put("a", JsonTree.object().put("y", "9").put("z", "3")).put("m", "over");
        base.merge(overlay);
        assertEquals("{\"a\":{\"x\":\"1\",\"y\":\"9\",\"z\":\"3\"},\"k\":\"base\",\"m\":\"over\"}", base.toJson());

        JsonTree node = JsonTree.object().put("keep", "1").put("drop", "2");
        assertEquals("2", node.remove("drop").orElseThrow().asString().orElseThrow());
        assertEquals("{\"keep\":\"1\"}", node.toJson());
        assertTrue(node.clear().isEmpty());
    }

    @Test
    @DisplayName("find/get/require taxonomy + hex + positional")
    void readTaxonomy() {
        JsonTree node = JsonTree.parse("{\"i\":7,\"c\":\"0xFFAABBCC\",\"a\":[10,20]}");
        assertEquals(7L, node.findLong("i").orElseThrow());
        assertEquals(0xFFAABBCC, node.findHex("c").orElseThrow());
        assertEquals(0xFFAABBCC, node.getHex("c", 0));
        assertEquals(20, node.getArray("a").getInt(1, 0));
        assertTrue(node.getObject("missing").isEmpty());
        assertEquals(7, node.requireInt("i"));
        assertThrows(JsonException.class, () -> node.requireString("missing"));
    }

    @Test
    @DisplayName("deep-path query, require path, first-present")
    void query() {
        JsonTree root = JsonTree.parse("{\"meta\":{\"frames\":[{\"id\":\"f0\"}]}}");
        assertEquals("f0", root.findPath("meta", "frames", "0", "id").orElseThrow().asString().orElseThrow());
        assertTrue(root.contains("meta", "frames", "0"));
        assertFalse(root.contains("meta", "frames", "9"));
        assertEquals("f0", root.require("meta", "frames", "0", "id").asString().orElseThrow());
        assertThrows(JsonException.class, () -> root.require("meta", "nope"));

        JsonTree aliased = JsonTree.parse("{\"new_name\":\"v\"}");
        assertEquals("v", aliased.findFirstString("old_name", "new_name").orElseThrow());
    }

    @Test
    @DisplayName("tuple-stream projections, typed streams, descendants/walk, entry lookup")
    void streaming() {
        JsonTree obj = JsonTree.parse("{\"a\":{\"t\":\"x\"},\"b\":{\"t\":\"y\"},\"s\":\"scalar\"}");
        assertEquals(3, obj.values().count());                   // three member values
        assertEquals(2, JsonTree.parse("[{\"t\":\"x\"},5,{\"t\":\"y\"}]").objects().count()); // object elements only
        assertEquals(List.of(10, 20), JsonTree.parse("{\"a\":[10,20]}").ints("a").toList());

        JsonTree tree = JsonTree.parse("{\"a\":{\"b\":1},\"c\":[2,3]}");
        assertEquals(5, tree.descendants().count());
        assertEquals(6, tree.walk().count());

        PairOptional<String, JsonTree> found =
            obj.findEntryPair((k, v) -> v.isObject() && "y".equals(v.getString("t", "")));
        assertTrue(found.isPresent());
        assertEquals("b", found.getKey());
    }

    @Test
    @DisplayName("typed decode: TypeToken, list/map terminals, enum, total asOptional")
    void typedDecode() {
        List<Foo> foos = JsonTree.parse("[{\"name\":\"a\",\"n\":1},{\"name\":\"b\",\"n\":2}]")
            .as(new TypeToken<List<Foo>>() {});
        assertEquals(2, foos.size());
        assertEquals("a", foos.getFirst().name());

        Map<String, Foo> map = JsonTree.parse("{\"x\":{\"name\":\"a\",\"n\":1}}").asMap(Foo.class);
        assertEquals(1, map.get("x").n());
        assertEquals(List.of(1, 2), JsonTree.parse("{\"ns\":[{\"name\":\"a\",\"n\":1},{\"name\":\"b\",\"n\":2}]}")
            .getList("ns", Foo.class).stream().map(Foo::n).toList());

        JsonTree facing = JsonTree.object().put("facing", "north");
        assertEquals(Facing.NORTH, facing.findEnum("facing", Facing.class).orElseThrow());
        assertEquals(Facing.SOUTH, facing.getEnum("missing", Facing.SOUTH));

        assertTrue(JsonTree.of("not-a-foo").asOptional(Foo.class).isEmpty());   // malformed -> empty, no throw
    }

    @Test
    @DisplayName("type() is exhaustive")
    void typeDiscriminator() {
        assertEquals(JsonTree.JsonType.OBJECT, JsonTree.object().type());
        assertEquals(JsonTree.JsonType.ARRAY, JsonTree.array().type());
        assertEquals(JsonTree.JsonType.STRING, JsonTree.of("x").type());
        assertEquals(JsonTree.JsonType.NUMBER, JsonTree.of(1).type());
        assertEquals(JsonTree.JsonType.BOOLEAN, JsonTree.of(true).type());
        assertEquals(JsonTree.JsonType.NULL, JsonTree.nullNode().type());
    }

    @Test
    @DisplayName("Codec injection + POJO encode")
    void codecAndFrom() {
        JsonTree.Codec codec = JsonTree.using(GsonSettings.defaults());
        assertEquals("v", codec.parse("{\"k\":\"v\"}").getString("k", ""));
        assertEquals("z", codec.as(JsonTree.parse("{\"name\":\"z\",\"n\":9}"), Foo.class).name());

        JsonTree encoded = JsonTree.from(new Foo("q", 7));
        assertEquals("q", encoded.getString("name", ""));
        assertEquals(7, encoded.getInt("n", 0));
    }

    @Test
    @DisplayName("IO symmetry + value semantics + null-parent write guard")
    void ioAndValueSemantics(@TempDir Path dir) {
        JsonTree original = JsonTree.object().put("a", "1").putInt("b", 2);
        assertEquals(original, JsonTree.parse(original.toBytes()));
        assertEquals(original, JsonTree.parse(new String(original.toBytes(), StandardCharsets.UTF_8)));
        // value semantics: two identically-built nodes are equal and hash equally
        JsonTree twin = JsonTree.object().put("a", "1").putInt("b", 2);
        assertEquals(original, twin);
        assertEquals(original.hashCode(), twin.hashCode());

        Path file = dir.resolve("nested/tree.json");
        original.write(file, false);                              // compact
        assertEquals(original, JsonTree.read(file));
        assertEquals("{\"a\":\"1\",\"b\":2}", JsonTree.read(file).toJson());
    }
}
