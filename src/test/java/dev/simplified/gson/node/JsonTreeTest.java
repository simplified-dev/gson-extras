package dev.simplified.gson.node;

import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import dev.simplified.gson.GsonSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins for the {@link JsonTree} contracts: insertion-order preservation, the Float-vs-Double
 * serialisation difference motivating the float-only rule (a permanent dependency-bump tripwire),
 * the total read surface, and the {@code write} newline contract.
 */
@DisplayName("JsonTree build/read/io contracts")
class JsonTreeTest {

    private static final @NotNull Gson COMPACT = GsonSettings.defaults().create();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("member order is insertion order - the byte-stability contract")
    void insertionOrder() {
        JsonTree node = JsonTree.object()
            .put("zebra", "z")
            .putInt("alpha", 1)
            .put("mid", true)
            .put("nested", JsonTree.object().put("b", "1").put("a", "2"));
        assertEquals("{\"zebra\":\"z\",\"alpha\":1,\"mid\":true,\"nested\":{\"b\":\"1\",\"a\":\"2\"}}",
            COMPACT.toJson(node.toGson()));
    }

    @Test
    @DisplayName("Gson canary: Float and Double serialise DIFFERENTLY - the float-only motivation")
    void floatVsDoubleCanary() {
        // The executable fact behind the float-only rule (a permanent tripwire): a Gson major
        // bump changing Number.toString routing surfaces HERE, not as a mystery parity failure
        // downstream.
        assertEquals("0.1", COMPACT.toJson(new JsonPrimitive(0.1f)));
        assertEquals("0.10000000149011612", COMPACT.toJson(new JsonPrimitive((double) 0.1f)));
        assertEquals("0.001", COMPACT.toJson(new JsonPrimitive(0.001f)));
        // and the JsonTree float channel routes through the Float path
        assertEquals("{\"v\":0.1}", COMPACT.toJson(JsonTree.object().put("v", 0.1f).toGson()));
    }

    @Test
    @DisplayName("conditional puts follow the empty-vs-absent rule; putUnless omits at default")
    void conditionalPuts() {
        JsonTree node = JsonTree.object()
            .putIf("absent", (String) null)
            .putIf("present", "x")
            .putIf("absentNode", (JsonTree) null)
            .putUnless("atDefault", 1.0f, 1.0f)
            .putUnless("offDefault", 0.5f, 1.0f)
            .putHex("color", 0xFFE6E6E6);
        assertEquals("{\"present\":\"x\",\"offDefault\":0.5,\"color\":\"0xFFE6E6E6\"}",
            COMPACT.toJson(node.toGson()));
    }

    @Test
    @DisplayName("child/childArray open-or-create; arrays append in order")
    void childrenAndArrays() {
        JsonTree root = JsonTree.object();
        root.child("families").put("a", "1");
        root.child("families").put("b", "2");                 // reuses, never clobbers
        root.childArray("list").add("x").add(2.5f).add(JsonTree.object().putInt("i", 3));
        assertEquals("{\"families\":{\"a\":\"1\",\"b\":\"2\"},\"list\":[\"x\",2.5,{\"i\":3}]}",
            COMPACT.toJson(root.toGson()));
        assertEquals("{\"f\":[1.0,2.5],\"i\":[1,2],\"s\":[\"a\",\"b\"]}",
            COMPACT.toJson(JsonTree.object()
                .putFloats("f", 1.0f, 2.5f).putInts("i", 1, 2).putStrings("s", "a", "b").toGson()));
    }

    @Test
    @DisplayName("read side is null-safe with defaults; members walk in order")
    void readSide() {
        JsonTree node = JsonTree.parse("{\"s\":\"v\",\"f\":1.5,\"i\":7,\"b\":true,\"o\":{\"k\":\"w\"},\"a\":[\"x\",\"y\"]}"
            .getBytes(StandardCharsets.UTF_8));
        assertEquals("v", node.findString("s").orElseThrow());
        assertTrue(node.findString("missing").isEmpty());
        assertEquals("dflt", node.getString("missing", "dflt"));
        assertEquals(1.5f, node.getFloat("f", 0f));
        assertEquals(0.25f, node.getFloat("missing", 0.25f));
        assertEquals(7, node.getInt("i", 0));
        assertTrue(node.getBoolean("b", false));
        assertTrue(node.find("missing").isEmpty());
        assertEquals("w", node.find("o").orElseThrow().findString("k").orElseThrow());
        List<String> keys = new ArrayList<>();
        node.members().forEach((key, value) -> keys.add(key));
        assertEquals(List.of("s", "f", "i", "b", "o", "a"), keys);
        List<String> values = new ArrayList<>();
        node.find("a").orElseThrow().elements().forEach(element -> values.add(element.toGson().getAsString()));
        assertEquals(List.of("x", "y"), values);
    }

    @Test
    @DisplayName("findAt bounds-checks arrays; asFloat() defaults on any non-number")
    void arrayIndexAndFloatValue() {
        JsonTree node = JsonTree.parse("{\"a\":[30,45.5,\"roll\"],\"s\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        JsonTree array = node.find("a").orElseThrow();
        assertEquals(30f, array.getFloat(0, 0f));
        assertEquals(45.5f, array.getFloat(1, 0f));
        assertEquals(9f, array.getFloat(2, 9f));               // string primitive -> default, never a parse throw
        assertTrue(array.findAt(3).isEmpty());
        assertTrue(array.findAt(-1).isEmpty());
        assertTrue(node.find("s").orElseThrow().findAt(0).isEmpty());  // non-array -> empty
        assertEquals(9f, node.find("s").orElseThrow().asFloat(9f));
        assertEquals(9f, node.find("a").orElseThrow().asFloat(9f));    // non-primitive -> default
    }

    @Test
    @DisplayName("write emits pretty JSON terminated by exactly one platform newline")
    void writeNewlineContract() throws IOException {
        Path out = tempDir.resolve("nested/out.json");
        JsonTree.object().put("k", "v").write(out);
        String written = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(written.endsWith(System.lineSeparator()), "platform newline at EOF");
        assertTrue(written.contains("\"k\": \"v\""), "pretty-printed");
        assertEquals(written.length() - System.lineSeparator().length(),
            written.lastIndexOf(System.lineSeparator()), "exactly one trailing newline");
    }

    @Test
    @DisplayName("kind predicates + size classify object / array / primitive")
    void kindPredicates() {
        JsonTree node = JsonTree.parse("{\"o\":{\"a\":1},\"arr\":[1,2,3],\"s\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        assertTrue(node.isObject());
        assertFalse(node.isArray());
        assertEquals(3, node.size());
        assertTrue(node.has("o"));
        assertFalse(node.has("missing"));
        assertTrue(node.find("arr").orElseThrow().isArray());
        assertEquals(3, node.find("arr").orElseThrow().size());
        assertTrue(node.find("s").orElseThrow().isPrimitive());
        assertEquals(0, node.find("s").orElseThrow().size());
    }

    @Test
    @DisplayName("find* reads are total: absent or wrong-kind yields empty, never a throw")
    void totalFindReads() {
        JsonTree node = JsonTree.parse("{\"s\":\"v\",\"i\":7,\"f\":1.5,\"b\":true,\"o\":{\"k\":1},\"a\":[1,2]}"
            .getBytes(StandardCharsets.UTF_8));
        assertEquals("v", node.findString("s").orElseThrow());
        assertEquals(7, node.findInt("i").orElseThrow());
        assertEquals(1.5f, node.findFloat("f").orElseThrow());
        assertTrue(node.findBoolean("b").orElseThrow());
        assertTrue(node.findObject("o").isPresent());
        assertTrue(node.findArray("a").isPresent());
        assertTrue(node.findInt("s").isEmpty());        // string is not a number
        assertTrue(node.findObject("a").isEmpty());     // array is not an object
        assertTrue(node.findString("missing").isEmpty());
        assertTrue(node.find("missing").isEmpty());
        assertTrue(node.find("s").orElseThrow().findString("anything").isEmpty());   // total even on a non-object node
    }

    @Test
    @DisplayName("self-value coercions return empty on kind mismatch")
    void selfValueReads() {
        JsonTree node = JsonTree.parse("{\"s\":\"v\",\"i\":7,\"b\":true}".getBytes(StandardCharsets.UTF_8));
        assertEquals("v", node.find("s").orElseThrow().asString().orElseThrow());
        assertEquals(7, node.find("i").orElseThrow().asInt().orElseThrow());
        assertTrue(node.find("b").orElseThrow().asBool().orElseThrow());
        assertTrue(node.find("s").orElseThrow().asInt().isEmpty());     // string primitive is not a number
        assertTrue(node.find("i").orElseThrow().asBool().isEmpty());
    }

    @Test
    @DisplayName("primitive-array streams skip mismatched entries; streams are empty on wrong kind")
    void arraysAndStreams() {
        JsonTree node = JsonTree.parse("{\"ints\":[1,2,3],\"floats\":[0.5,1.5],\"strs\":[\"a\",\"b\"],\"mixed\":[1,\"x\",true]}"
            .getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of(1, 2, 3), node.ints("ints").toList());
        assertEquals(List.of(0.5f, 1.5f), node.floats("floats").toList());
        assertEquals(List.of("a", "b"), node.strings("strs").toList());
        assertEquals(List.of(1), node.ints("mixed").toList());            // non-numbers skipped
        assertTrue(node.ints("missing").toList().isEmpty());
        assertEquals(List.of("ints", "floats", "strs", "mixed"), node.keys().toList());
        assertEquals(3, node.find("ints").orElseThrow().elements().count());
        assertTrue(node.find("ints").orElseThrow().keys().toList().isEmpty());     // array has no keys
        assertTrue(node.find("strs").orElseThrow().members().toList().isEmpty());
    }

    @Test
    @DisplayName("putAll deep-copies later-wins; deepCopy is independent")
    void mergeAndCopy() {
        JsonTree a = JsonTree.object().put("x", "1").put("y", "2");
        JsonTree b = JsonTree.object().put("y", "9").put("z", "3");
        a.putAll(b);
        assertEquals("{\"x\":\"1\",\"y\":\"9\",\"z\":\"3\"}", COMPACT.toJson(a.toGson()));
        JsonTree src = JsonTree.object().put("k", "orig");
        JsonTree copy = src.deepCopy();
        src.put("k", "changed");
        assertEquals("{\"k\":\"orig\"}", COMPACT.toJson(copy.toGson()));   // deep copy is independent
    }

    @Test
    @DisplayName("as() deserialises the whole node into a typed DTO")
    void asDeserialises() {
        JsonTree node = JsonTree.parse("{\"name\":\"grass\",\"count\":4}".getBytes(StandardCharsets.UTF_8));
        Sample sample = node.as(Sample.class);
        assertEquals("grass", sample.name);
        assertEquals(4, sample.count);
    }

    private static final class Sample {
        String name;
        int count;
    }

}
