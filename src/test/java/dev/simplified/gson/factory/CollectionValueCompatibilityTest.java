package dev.simplified.gson.factory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.gson.GsonSettings;
import dev.simplified.gson.annotation.Capture;
import dev.simplified.gson.annotation.Lenient;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Covers array-valued entries reaching {@link Capture @Capture} maps and object-valued
 * entries being filtered out of {@link Lenient @Lenient} collection maps.
 */
public class CollectionValueCompatibilityTest {

    private static final Gson GSON = GsonSettings.defaults().create();

    @Test
    public void captureCollectsArrayValues_ok() {
        Toolkit toolkit = GSON.fromJson("""
            {
              "IS_UNLOCKED": true,
              "IN_USE": { "SUGAR_CANE": { "0": false } },
              "SUGAR_CANE": [ { "id": "cane" } ],
              "POTATO": [ { "id": "spud" }, { "id": "tater" } ]
            }
            """, Toolkit.class);

        assertThat(toolkit.isUnlocked(), is(true));
        assertThat(toolkit.getInUse(), hasKey("SUGAR_CANE"));
        assertThat(toolkit.getTools().keySet(), containsInAnyOrder("SUGAR_CANE", "POTATO"));
        assertThat(toolkit.getTools().get("SUGAR_CANE").size(), is(1));
        assertThat(toolkit.getTools().get("SUGAR_CANE").getFirst().getId(), is("cane"));
        assertThat(toolkit.getTools().get("POTATO").size(), is(2));
    }

    @Test
    public void captureCollectsEmptyArrayValues_ok() {
        Toolkit toolkit = GSON.fromJson("{ \"WHEAT\": [] }", Toolkit.class);

        assertThat(toolkit.getTools(), hasKey("WHEAT"));
        assertThat(toolkit.getTools().get("WHEAT").isEmpty(), is(true));
    }

    @Test
    public void captureSkipsObjectValueForCollectionType_ok() {
        // an unnamed object entry cannot be a list, so it must not land in the captured map
        Toolkit toolkit = GSON.fromJson("{ \"BOGUS\": { \"id\": \"nope\" } }", Toolkit.class);

        assertThat(toolkit.getTools(), not(hasKey("BOGUS")));
    }

    @Test
    public void captureSkipsArrayWithIncompatibleElements_ok() {
        Counts counts = GSON.fromJson("""
            { "good": [ 1, 2 ], "bad": [ "not-a-number" ] }
            """, Counts.class);

        assertThat(counts.getValues(), hasKey("good"));
        assertThat(counts.getValues().get("good"), contains(1, 2));
        assertThat(counts.getValues(), not(hasKey("bad")));
    }

    @Test
    public void lenientFiltersObjectEntriesFromCollectionMap_ok() {
        // the bool and the object are incompatible with ConcurrentList and must be filtered,
        // not handed to the delegate - that threw "Expected BEGIN_ARRAY but was BEGIN_OBJECT"
        LenientToolkit toolkit = GSON.fromJson("""
            {
              "tools": {
                "IS_UNLOCKED": true,
                "IN_USE": { "SUGAR_CANE": { "0": false } },
                "SUGAR_CANE": [ { "id": "cane" } ]
              }
            }
            """, LenientToolkit.class);

        assertThat(toolkit.getTools().keySet(), contains("SUGAR_CANE"));
        assertThat(toolkit.getTools().get("SUGAR_CANE").getFirst().getId(), is("cane"));
    }

    @Test
    public void lenientCollectionMapOverflowMergesBackOnWrite_ok() {
        // the write half of the case above - the two filtered entries must come back, and they
        // must come back inside the field's own sub-object rather than at the root
        LenientToolkit toolkit = GSON.fromJson("""
            {
              "tools": {
                "IS_UNLOCKED": true,
                "IN_USE": { "SUGAR_CANE": { "0": false } },
                "SUGAR_CANE": [ { "id": "cane" } ]
              }
            }
            """, LenientToolkit.class);

        JsonObject result = JsonParser.parseString(GSON.toJson(toolkit)).getAsJsonObject();
        JsonObject tools = result.getAsJsonObject("tools");

        assertThat(result.keySet(), contains("tools"));
        assertThat(tools.keySet(), containsInAnyOrder("SUGAR_CANE", "IS_UNLOCKED", "IN_USE"));
        assertThat(tools.get("IS_UNLOCKED").getAsBoolean(), is(true));
        assertThat(tools.getAsJsonObject("IN_USE").getAsJsonObject("SUGAR_CANE").get("0").getAsBoolean(), is(false));
        assertThat(tools.getAsJsonArray("SUGAR_CANE").size(), is(1));
    }

    @Getter
    public static class Toolkit {

        @SerializedName("IS_UNLOCKED")
        private boolean unlocked;
        @SerializedName("IN_USE")
        private @NotNull ConcurrentMap<String, ConcurrentMap<Integer, Boolean>> inUse = Concurrent.newMap();
        @Capture
        private @NotNull ConcurrentMap<String, ConcurrentList<Tool>> tools = Concurrent.newMap();

        public boolean isUnlocked() {
            return this.unlocked;
        }

    }

    @Getter
    public static class LenientToolkit {

        @Lenient
        private @NotNull ConcurrentMap<String, ConcurrentList<Tool>> tools = Concurrent.newMap();

    }

    @Getter
    public static class Counts {

        @Capture
        private @NotNull ConcurrentMap<String, ConcurrentList<Integer>> values = Concurrent.newMap();

    }

    @Getter
    public static class Tool {

        private @NotNull String id = "";

    }

}
