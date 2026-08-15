package dev.simplified.gson.factory;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.NoArgsConstructor;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.gson.GsonSettings;
import dev.simplified.gson.annotation.Capture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Covers key-to-object entries reaching a grouping-mode {@link Capture @Capture} map and
 * the {@link Capture.Grouping} override that settles affix collisions.
 */
public class CaptureGroupingModeTest {

    private static final Gson GSON = GsonSettings.defaults().create();

    @Test
    public void groupingModeReadsWholeObjectEntries_ok() {
        // no key ends with _status/_progress, so affix matching finds nothing to split
        Board board = GSON.fromJson("""
            {
              "fishing": { "status": "ACTIVE", "progress": 4 },
              "dojo":    { "status": "DONE",   "progress": 9 }
            }
            """, Board.class);

        assertThat(board.getQuests().keySet(), containsInAnyOrder("fishing", "dojo"));
        assertThat(board.getQuests().get("fishing").getStatus(), is("ACTIVE"));
        assertThat(board.getQuests().get("dojo").getProgress(), is(9));
    }

    @Test
    public void groupingModeStillSplitsAffixedSiblings_ok() {
        // the historical shape must keep working unchanged
        Board board = GSON.fromJson("""
            { "raid_status": "ACTIVE", "raid_progress": 2 }
            """, Board.class);

        assertThat(board.getQuests(), hasKey("raid"));
        assertThat(board.getQuests().get("raid").getStatus(), is("ACTIVE"));
        assertThat(board.getQuests().get("raid").getProgress(), is(2));
    }

    @Test
    public void groupingModeMergesObjectEntryWithAffixedSiblings_ok() {
        Board board = GSON.fromJson("""
            { "raid": { "status": "ACTIVE" }, "raid_progress": 7 }
            """, Board.class);

        assertThat(board.getQuests(), hasKey("raid"));
        assertThat(board.getQuests().get("raid").getStatus(), is("ACTIVE"));
        assertThat(board.getQuests().get("raid").getProgress(), is(7));
    }

    @Test
    public void bareFieldTakesPrecedenceOverObjectEntry_ok() {
        // a value class with a bare field keeps its existing unmatched-key behaviour
        Levels levels = GSON.fromJson("{ \"1\": true, \"2\": false }", Levels.class);

        assertThat(levels.getClaimed().keySet(), containsInAnyOrder(1, 2));
        assertThat(levels.getClaimed().get(1).isClaimed(), is(true));
        assertThat(levels.getClaimed().get(2).isClaimed(), is(false));
    }

    @Test
    public void entryGroupingIgnoresCollidingAffix_ok() {
        // "daily_progress" ends with the auto-suffix _progress and would otherwise be split
        // into group "daily"; ENTRY reads it as a whole value instead
        ForcedEntryBoard board = GSON.fromJson("""
            { "daily_progress": { "status": "ACTIVE", "progress": 3 } }
            """, ForcedEntryBoard.class);

        assertThat(board.getQuests(), hasKey("daily_progress"));
        assertThat(board.getQuests(), not(hasKey("daily")));
        assertThat(board.getQuests().get("daily_progress").getProgress(), is(3));
    }

    @Test
    public void autoGroupingSplitsCollidingAffix_ok() {
        // the same payload under AUTO demonstrates why the override exists
        Board board = GSON.fromJson("""
            { "daily_progress": { "status": "ACTIVE", "progress": 3 } }
            """, Board.class);

        assertThat(board.getQuests(), not(hasKey("daily_progress")));
    }

    @Getter
    public static class Board {

        @Capture
        private @NotNull ConcurrentMap<String, Quest> quests = Concurrent.newMap();

    }

    @Getter
    public static class ForcedEntryBoard {

        @Capture(grouping = Capture.Grouping.ENTRY)
        private @NotNull ConcurrentMap<String, Quest> quests = Concurrent.newMap();

    }

    @Getter
    @NoArgsConstructor
    public static class Quest {

        private @NotNull String status = "";
        private int progress;

    }

    @Getter
    public static class Levels {

        @Capture
        private @NotNull ConcurrentMap<Integer, Claim> claimed = Concurrent.newMap();

    }

    @Getter
    @NoArgsConstructor
    public static class Claim {

        @SerializedName("")
        private boolean claimed;
        private boolean special;

    }

}
