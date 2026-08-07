package dev.simplified.gson;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.tuple.pair.Pair;
import dev.simplified.collection.tuple.pair.PairOptional;
import dev.simplified.gson.annotation.Capture;
import dev.simplified.gson.annotation.Collapse;
import dev.simplified.gson.annotation.Extract;
import dev.simplified.gson.annotation.Key;
import dev.simplified.gson.annotation.Lenient;
import dev.simplified.gson.annotation.SerializedPath;
import dev.simplified.gson.annotation.Split;
import dev.simplified.gson.exception.JsonException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GsonFactoryTest {

    private static final Gson GSON = GsonSettings.defaults().create();

    // ──── SerializedPathTypeAdaptorFactory ────

    @Nested
    class SerializedPathTests {

        @Getter
        @NoArgsConstructor
        static class PlayerStats {

            private String name;
            @SerializedPath("stats.health")
            private int health;
            @SerializedPath("stats.defense")
            private int defense;
            @SerializedPath("stats.combat.strength")
            private int strength;
            @SerializedPath("stats.combat.crit_damage")
            private double critDamage;
            @SerializedPath("perks.double_drops")
            private int doubleDrops;

        }

        @Getter
        @NoArgsConstructor
        static class WithSerializedName {

            private String id;
            @SerializedName("display_name")
            @SerializedPath("profile.display_name")
            private String displayName;
            @SerializedPath("profile.level")
            private int level;

        }

        @Getter
        @NoArgsConstructor
        static class WithOptional {

            private String key;
            @SerializedPath("metadata.description")
            private Optional<String> description = Optional.empty();
            @SerializedPath("metadata.version")
            private Optional<Integer> version = Optional.empty();

        }

        @Test
        public void readNestedPaths_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "TestPlayer",
                    "stats": {
                        "health": 100,
                        "defense": 50,
                        "combat": {
                            "strength": 200,
                            "crit_damage": 1.5
                        }
                    },
                    "perks": {
                        "double_drops": 3
                    }
                }
                """;

            PlayerStats stats = gson.fromJson(json, PlayerStats.class);

            assertThat(stats.getName(), is("TestPlayer"));
            assertThat(stats.getHealth(), is(100));
            assertThat(stats.getDefense(), is(50));
            assertThat(stats.getStrength(), is(200));
            assertThat(stats.getCritDamage(), is(1.5));
            assertThat(stats.getDoubleDrops(), is(3));
        }

        @Test
        public void writeNestedPaths_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "TestPlayer",
                    "stats": {
                        "health": 100,
                        "defense": 50,
                        "combat": {
                            "strength": 200,
                            "crit_damage": 1.5
                        }
                    },
                    "perks": {
                        "double_drops": 3
                    }
                }
                """;

            PlayerStats stats = gson.fromJson(json, PlayerStats.class);
            String output = gson.toJson(stats);
            JsonObject result = gson.fromJson(output, JsonObject.class);

            // Verify flat fields
            assertThat(result.get("name").getAsString(), is("TestPlayer"));

            // Verify nested path fields are NOT flat
            assertThat(result.has("health"), is(false));
            assertThat(result.has("defense"), is(false));
            assertThat(result.has("strength"), is(false));
            assertThat(result.has("critDamage"), is(false));
            assertThat(result.has("doubleDrops"), is(false));

            // Verify nested structure
            JsonObject statsObj = result.getAsJsonObject("stats");
            assertThat(statsObj, is(notNullValue()));
            assertThat(statsObj.get("health").getAsInt(), is(100));
            assertThat(statsObj.get("defense").getAsInt(), is(50));

            JsonObject combatObj = statsObj.getAsJsonObject("combat");
            assertThat(combatObj, is(notNullValue()));
            assertThat(combatObj.get("strength").getAsInt(), is(200));
            assertThat(combatObj.get("crit_damage").getAsDouble(), is(1.5));

            JsonObject perksObj = result.getAsJsonObject("perks");
            assertThat(perksObj, is(notNullValue()));
            assertThat(perksObj.get("double_drops").getAsInt(), is(3));
        }

        @Test
        public void roundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "RoundTripPlayer",
                    "stats": {
                        "health": 250,
                        "defense": 120,
                        "combat": {
                            "strength": 350,
                            "crit_damage": 2.75
                        }
                    },
                    "perks": {
                        "double_drops": 5
                    }
                }
                """;

            PlayerStats first = gson.fromJson(json, PlayerStats.class);
            String serialized = gson.toJson(first);
            PlayerStats second = gson.fromJson(serialized, PlayerStats.class);

            assertThat(second.getName(), is(first.getName()));
            assertThat(second.getHealth(), is(first.getHealth()));
            assertThat(second.getDefense(), is(first.getDefense()));
            assertThat(second.getStrength(), is(first.getStrength()));
            assertThat(second.getCritDamage(), is(first.getCritDamage()));
            assertThat(second.getDoubleDrops(), is(first.getDoubleDrops()));
        }

        @Test
        public void writeWithSerializedName_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "id": "abc-123",
                    "profile": {
                        "display_name": "CoolUser",
                        "level": 42
                    }
                }
                """;

            WithSerializedName obj = gson.fromJson(json, WithSerializedName.class);
            String output = gson.toJson(obj);
            JsonObject result = gson.fromJson(output, JsonObject.class);

            // Flat key from @SerializedName should be removed
            assertThat(result.has("display_name"), is(false));
            // Field name key should also not be present
            assertThat(result.has("displayName"), is(false));

            // Verify nested structure
            JsonObject profileObj = result.getAsJsonObject("profile");
            assertThat(profileObj, is(notNullValue()));
            assertThat(profileObj.get("display_name").getAsString(), is("CoolUser"));
            assertThat(profileObj.get("level").getAsInt(), is(42));

            // Roundtrip
            WithSerializedName roundTripped = gson.fromJson(output, WithSerializedName.class);
            assertThat(roundTripped.getDisplayName(), is("CoolUser"));
            assertThat(roundTripped.getLevel(), is(42));
        }

        @Test
        public void writeWithOptional_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "key": "test-key",
                    "metadata": {
                        "description": "A test item",
                        "version": 3
                    }
                }
                """;

            WithOptional obj = gson.fromJson(json, WithOptional.class);
            String output = gson.toJson(obj);
            JsonObject result = gson.fromJson(output, JsonObject.class);

            // Verify nested structure
            JsonObject metadataObj = result.getAsJsonObject("metadata");
            assertThat(metadataObj, is(notNullValue()));
            assertThat(metadataObj.get("description").getAsString(), is("A test item"));
            assertThat(metadataObj.get("version").getAsInt(), is(3));

            // Roundtrip
            WithOptional roundTripped = gson.fromJson(output, WithOptional.class);
            assertThat(roundTripped.getDescription().orElse(null), is("A test item"));
            assertThat(roundTripped.getVersion().orElse(null), is(3));
        }

        @Test
        public void writeWithEmptyOptional_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "key": "empty-key"
                }
                """;

            WithOptional obj = gson.fromJson(json, WithOptional.class);
            String output = gson.toJson(obj);
            JsonObject result = gson.fromJson(output, JsonObject.class);

            assertThat(result.get("key").getAsString(), is("empty-key"));

            // Empty optionals should still produce the nested path since serializeNulls is on
            JsonObject metadataObj = result.getAsJsonObject("metadata");
            assertThat(metadataObj, is(notNullValue()));

            // Roundtrip preserves empty optionals
            WithOptional roundTripped = gson.fromJson(output, WithOptional.class);
            assertThat(roundTripped.getKey(), is("empty-key"));
            assertThat(roundTripped.getDescription().isPresent(), is(false));
            assertThat(roundTripped.getVersion().isPresent(), is(false));
        }

    }

    // ──── OptionalTypeAdapterFactory ────

    @Nested
    class OptionalTypeAdapterTests {

        @Getter
        @NoArgsConstructor
        static class OptionalModel {

            private String name;
            private Optional<String> nickname = Optional.empty();
            private Optional<Integer> level = Optional.empty();
            private Optional<Double> score = Optional.empty();
            private Optional<Boolean> active = Optional.empty();

        }

        @Test
        public void serializePresentOptionals_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Alice",
                    "nickname": "Ace",
                    "level": 42,
                    "score": 98.5,
                    "active": true
                }
                """;

            OptionalModel model = gson.fromJson(json, OptionalModel.class);
            String output = gson.toJson(model);
            JsonObject result = gson.fromJson(output, JsonObject.class);

            // Present optionals are unwrapped (not nested in an object)
            assertThat(result.get("name").getAsString(), is("Alice"));
            assertThat(result.get("nickname").getAsString(), is("Ace"));
            assertThat(result.get("level").getAsInt(), is(42));
            assertThat(result.get("score").getAsDouble(), is(98.5));
            assertThat(result.get("active").getAsBoolean(), is(true));
        }

        @Test
        public void serializeEmptyOptionals_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Bob"
                }
                """;

            OptionalModel model = gson.fromJson(json, OptionalModel.class);
            String output = gson.toJson(model);
            JsonObject result = gson.fromJson(output, JsonObject.class);

            assertThat(result.get("name").getAsString(), is("Bob"));
            // Empty optionals are omitted when serializeNulls is off
            assertThat(result.has("nickname"), is(false));
            assertThat(result.has("level"), is(false));
        }

        @Test
        public void deserializeNullToEmpty_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Charlie",
                    "nickname": null,
                    "level": null
                }
                """;

            OptionalModel model = gson.fromJson(json, OptionalModel.class);

            assertThat(model.getName(), is("Charlie"));
            assertThat(model.getNickname().isPresent(), is(false));
            assertThat(model.getLevel().isPresent(), is(false));
        }

        @Test
        public void deserializeMissingToEmpty_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Diana"
                }
                """;

            OptionalModel model = gson.fromJson(json, OptionalModel.class);

            assertThat(model.getName(), is("Diana"));
            assertThat(model.getNickname().isPresent(), is(false));
            assertThat(model.getLevel().isPresent(), is(false));
            assertThat(model.getScore().isPresent(), is(false));
            assertThat(model.getActive().isPresent(), is(false));
        }

        @Test
        public void roundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Eve",
                    "nickname": "E",
                    "level": 99,
                    "score": 100.0,
                    "active": false
                }
                """;

            OptionalModel first = gson.fromJson(json, OptionalModel.class);
            String serialized = gson.toJson(first);
            OptionalModel second = gson.fromJson(serialized, OptionalModel.class);

            assertThat(second.getName(), is(first.getName()));
            assertThat(second.getNickname().orElse(null), is(first.getNickname().orElse(null)));
            assertThat(second.getLevel().orElse(null), is(first.getLevel().orElse(null)));
            assertThat(second.getScore().orElse(null), is(first.getScore().orElse(null)));
            assertThat(second.getActive().orElse(null), is(first.getActive().orElse(null)));
        }

        @Test
        public void roundTripWithEmptyOptionals_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Frank"
                }
                """;

            OptionalModel first = gson.fromJson(json, OptionalModel.class);
            String serialized = gson.toJson(first);
            OptionalModel second = gson.fromJson(serialized, OptionalModel.class);

            assertThat(second.getName(), is("Frank"));
            assertThat(second.getNickname().isPresent(), is(false));
            assertThat(second.getLevel().isPresent(), is(false));
            assertThat(second.getScore().isPresent(), is(false));
            assertThat(second.getActive().isPresent(), is(false));
        }

    }

    // ──── CaptureTypeAdapterFactory ────

    @Nested
    class CaptureTests {

        @Getter
        @NoArgsConstructor
        static class SimpleCaptureModel {

            private String name;
            private int level;
            @Capture
            private ConcurrentMap<String, Integer> data = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class FilteredCaptureModel {

            private String name;
            @Capture(filter = "^stat_")
            private ConcurrentMap<String, Integer> stats = Concurrent.newMap();

        }

        enum DojoType {
            FORCE,
            STAMINA,
            MASTERY
        }

        @Getter
        @NoArgsConstructor
        static class EnumKeyCaptureModel {

            @Capture(filter = "^dojo_points_")
            private ConcurrentMap<DojoType, Integer> points = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class MultiCaptureModel {

            private String id;
            @Capture(filter = "^points_")
            private ConcurrentMap<String, Integer> points = Concurrent.newMap();
            @Capture(filter = "^wave_")
            private ConcurrentMap<String, Integer> waves = Concurrent.newMap();
            @Capture
            private ConcurrentMap<String, Integer> extras = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class CaptureWithSerializedNameModel {

            @SerializedName("display_name")
            private String displayName;
            private int level;
            @Capture
            private ConcurrentMap<String, Integer> data = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class CaptureWithPostInitModel implements PostInit {

            private String name;
            @Capture
            private ConcurrentMap<String, Integer> data = Concurrent.newMap();
            private transient int total;

            @Override
            public void postInit() {
                this.total = this.data.values().stream().mapToInt(Integer::intValue).sum();
            }

        }

        @Getter
        @NoArgsConstructor
        static class InnerSong {

            @SerializedName("best_completion")
            private int bestCompletion;
            private int completions;
            @SerializedName("perfect_completions")
            private int perfectCompletions;

        }

        @Getter
        @NoArgsConstructor
        static class GroupingModel {

            private String name;
            @Capture
            private ConcurrentMap<String, InnerSong> songs = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class FilteredGroupingModel {

            @SerializedName("claimed_talisman")
            private boolean talismanClaimed;
            @Capture(filter = "^song_")
            private ConcurrentMap<String, InnerSong> songs = Concurrent.newMap();

        }

        @Test
        public void readCapture_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Test",
                    "level": 5,
                    "extra_1": 10,
                    "extra_2": 20
                }
                """;

            SimpleCaptureModel model = gson.fromJson(json, SimpleCaptureModel.class);

            assertThat(model.getName(), is("Test"));
            assertThat(model.getLevel(), is(5));
            assertThat(model.getData(), aMapWithSize(2));
            assertThat(model.getData(), hasEntry("extra_1", 10));
            assertThat(model.getData(), hasEntry("extra_2", 20));
        }

        @Test
        public void writeCapture_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Test",
                    "level": 5,
                    "extra_1": 10,
                    "extra_2": 20
                }
                """;

            SimpleCaptureModel model = gson.fromJson(json, SimpleCaptureModel.class);
            String output = gson.toJson(model);
            JsonObject result = gson.fromJson(output, JsonObject.class);

            assertThat(result.get("name").getAsString(), is("Test"));
            assertThat(result.get("level").getAsInt(), is(5));
            assertThat(result.get("extra_1").getAsInt(), is(10));
            assertThat(result.get("extra_2").getAsInt(), is(20));
        }

        @Test
        public void roundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "RoundTrip",
                    "level": 3,
                    "a": 1,
                    "b": 2
                }
                """;

            SimpleCaptureModel first = gson.fromJson(json, SimpleCaptureModel.class);
            String serialized = gson.toJson(first);
            SimpleCaptureModel second = gson.fromJson(serialized, SimpleCaptureModel.class);

            assertThat(second.getName(), is(first.getName()));
            assertThat(second.getLevel(), is(first.getLevel()));
            assertThat(second.getData(), is(first.getData()));
        }

        @Test
        public void emptyCapture_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Empty",
                    "level": 1
                }
                """;

            SimpleCaptureModel model = gson.fromJson(json, SimpleCaptureModel.class);

            assertThat(model.getName(), is("Empty"));
            assertThat(model.getLevel(), is(1));
            assertThat(model.getData(), anEmptyMap());
        }

        @Test
        public void typeFilteredCapture_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "TypeFilter",
                    "level": 1,
                    "valid": 42,
                    "invalid": "not_an_int"
                }
                """;

            SimpleCaptureModel model = gson.fromJson(json, SimpleCaptureModel.class);

            assertThat(model.getData(), aMapWithSize(1));
            assertThat(model.getData(), hasEntry("valid", 42));
        }

        @Test
        public void captureWithSerializedName_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "display_name": "Cool",
                    "level": 5,
                    "dynamic_1": 10
                }
                """;

            CaptureWithSerializedNameModel model = gson.fromJson(json, CaptureWithSerializedNameModel.class);

            assertThat(model.getDisplayName(), is("Cool"));
            assertThat(model.getLevel(), is(5));
            assertThat(model.getData(), aMapWithSize(1));
            assertThat(model.getData(), hasEntry("dynamic_1", 10));
        }

        @Test
        public void captureWithPostInit_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "PostInit",
                    "a": 3,
                    "b": 7
                }
                """;

            CaptureWithPostInitModel model = gson.fromJson(json, CaptureWithPostInitModel.class);

            assertThat(model.getName(), is("PostInit"));
            assertThat(model.getData(), aMapWithSize(2));
            assertThat(model.getTotal(), is(10));
        }

        @Test
        public void noCaptureField_passthrough_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "key": "test",
                    "value": 42
                }
                """;

            PostInitTests.PlainModel model = gson.fromJson(json, PostInitTests.PlainModel.class);

            assertThat(model.getKey(), is("test"));
            assertThat(model.getValue(), is(42));
        }

        @Test
        public void filteredCapture_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Filtered",
                    "stat_health": 100,
                    "stat_defense": 50,
                    "unrelated": 999
                }
                """;

            FilteredCaptureModel model = gson.fromJson(json, FilteredCaptureModel.class);

            assertThat(model.getName(), is("Filtered"));
            assertThat(model.getStats(), aMapWithSize(2));
            assertThat(model.getStats(), hasEntry("health", 100));
            assertThat(model.getStats(), hasEntry("defense", 50));
        }

        @Test
        public void filterWithEnumKey_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "dojo_points_FORCE": 100,
                    "dojo_points_STAMINA": 200,
                    "dojo_points_MASTERY": 300
                }
                """;

            EnumKeyCaptureModel model = gson.fromJson(json, EnumKeyCaptureModel.class);

            assertThat(model.getPoints(), aMapWithSize(3));
            assertThat(model.getPoints(), hasEntry(DojoType.FORCE, 100));
            assertThat(model.getPoints(), hasEntry(DojoType.STAMINA, 200));
            assertThat(model.getPoints(), hasEntry(DojoType.MASTERY, 300));
        }

        @Test
        public void multipleFilteredFields_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "id": "multi",
                    "points_a": 10,
                    "points_b": 20,
                    "wave_x": 5,
                    "other": 99
                }
                """;

            MultiCaptureModel model = gson.fromJson(json, MultiCaptureModel.class);

            assertThat(model.getId(), is("multi"));
            assertThat(model.getPoints(), aMapWithSize(2));
            assertThat(model.getPoints(), hasEntry("a", 10));
            assertThat(model.getPoints(), hasEntry("b", 20));
            assertThat(model.getWaves(), aMapWithSize(1));
            assertThat(model.getWaves(), hasEntry("x", 5));
            assertThat(model.getExtras(), aMapWithSize(1));
            assertThat(model.getExtras(), hasEntry("other", 99));
        }

        @Test
        public void filteredRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "RT",
                    "stat_health": 100,
                    "stat_defense": 50
                }
                """;

            FilteredCaptureModel first = gson.fromJson(json, FilteredCaptureModel.class);
            String serialized = gson.toJson(first);
            FilteredCaptureModel second = gson.fromJson(serialized, FilteredCaptureModel.class);

            assertThat(second.getName(), is(first.getName()));
            assertThat(second.getStats(), is(first.getStats()));
        }

        @Test
        public void classValueGrouping_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Harp",
                    "hymn_joy_best_completion": 5,
                    "hymn_joy_completions": 10,
                    "hymn_joy_perfect_completions": 3,
                    "through_fire_best_completion": 2,
                    "through_fire_completions": 8,
                    "through_fire_perfect_completions": 1
                }
                """;

            GroupingModel model = gson.fromJson(json, GroupingModel.class);

            assertThat(model.getName(), is("Harp"));
            assertThat(model.getSongs(), aMapWithSize(2));
            assertThat(model.getSongs(), hasKey("hymn_joy"));
            assertThat(model.getSongs().get("hymn_joy").getBestCompletion(), is(5));
            assertThat(model.getSongs().get("hymn_joy").getCompletions(), is(10));
            assertThat(model.getSongs().get("hymn_joy").getPerfectCompletions(), is(3));
            assertThat(model.getSongs().get("through_fire").getBestCompletion(), is(2));
        }

        @Test
        public void classValueGroupingWithFilter_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "claimed_talisman": true,
                    "song_hymn_joy_best_completion": 5,
                    "song_hymn_joy_completions": 10,
                    "song_hymn_joy_perfect_completions": 3
                }
                """;

            FilteredGroupingModel model = gson.fromJson(json, FilteredGroupingModel.class);

            assertThat(model.isTalismanClaimed(), is(true));
            assertThat(model.getSongs(), aMapWithSize(1));
            assertThat(model.getSongs().get("hymn_joy").getBestCompletion(), is(5));
            assertThat(model.getSongs().get("hymn_joy").getCompletions(), is(10));
            assertThat(model.getSongs().get("hymn_joy").getPerfectCompletions(), is(3));
        }

        @Test
        public void classValueGroupingRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "claimed_talisman": true,
                    "song_hymn_joy_best_completion": 5,
                    "song_hymn_joy_completions": 10,
                    "song_hymn_joy_perfect_completions": 3
                }
                """;

            FilteredGroupingModel first = gson.fromJson(json, FilteredGroupingModel.class);
            String serialized = gson.toJson(first);
            FilteredGroupingModel second = gson.fromJson(serialized, FilteredGroupingModel.class);

            assertThat(second.isTalismanClaimed(), is(first.isTalismanClaimed()));
            assertThat(second.getSongs(), aMapWithSize(1));
            assertThat(second.getSongs().get("hymn_joy").getBestCompletion(), is(5));
            assertThat(second.getSongs().get("hymn_joy").getCompletions(), is(10));
            assertThat(second.getSongs().get("hymn_joy").getPerfectCompletions(), is(3));
        }

        @Getter
        @NoArgsConstructor
        static class BareEntryTierData {

            @SerializedName("")
            private int total;
            private int bronze;
            private int silver;
            private int gold;
            private int diamond;

        }

        @Getter
        @NoArgsConstructor
        static class BareEntryGroupingModel {

            private int count;
            @Capture
            private ConcurrentMap<String, BareEntryTierData> entries = Concurrent.newMap();

        }

        enum TrophyFish {
            BLOBFISH,
            GUSHER,
            GOLDEN_FISH
        }

        @Getter
        @NoArgsConstructor
        static class BareEntryEnumKeyModel {

            @Capture
            private ConcurrentMap<TrophyFish, BareEntryTierData> fish = Concurrent.newMap();

        }

        @Test
        public void bareEntryGrouping_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "count": 5,
                    "blobfish": 5767,
                    "blobfish_bronze": 4044,
                    "blobfish_silver": 1582,
                    "gusher": 100,
                    "gusher_bronze": 80
                }
                """;

            BareEntryGroupingModel model = gson.fromJson(json, BareEntryGroupingModel.class);

            assertThat(model.getCount(), is(5));
            assertThat(model.getEntries(), aMapWithSize(2));
            assertThat(model.getEntries(), hasKey("blobfish"));
            assertThat(model.getEntries().get("blobfish").getTotal(), is(5767));
            assertThat(model.getEntries().get("blobfish").getBronze(), is(4044));
            assertThat(model.getEntries().get("blobfish").getSilver(), is(1582));
            assertThat(model.getEntries().get("gusher").getTotal(), is(100));
            assertThat(model.getEntries().get("gusher").getBronze(), is(80));
        }

        @Test
        public void bareEntryGroupingRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "count": 1,
                    "blobfish": 100,
                    "blobfish_bronze": 50,
                    "blobfish_gold": 10
                }
                """;

            BareEntryGroupingModel first = gson.fromJson(json, BareEntryGroupingModel.class);
            String serialized = gson.toJson(first);
            BareEntryGroupingModel second = gson.fromJson(serialized, BareEntryGroupingModel.class);

            assertThat(second.getCount(), is(first.getCount()));
            assertThat(second.getEntries(), aMapWithSize(1));
            assertThat(second.getEntries().get("blobfish").getTotal(), is(100));
            assertThat(second.getEntries().get("blobfish").getBronze(), is(50));
            assertThat(second.getEntries().get("blobfish").getGold(), is(10));
        }

        @Test
        public void bareEntryGroupingWithEnumKey_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "BLOBFISH": 5767,
                    "BLOBFISH_bronze": 4044,
                    "GOLDEN_FISH": 81,
                    "GOLDEN_FISH_diamond": 5
                }
                """;

            BareEntryEnumKeyModel model = gson.fromJson(json, BareEntryEnumKeyModel.class);

            assertThat(model.getFish(), aMapWithSize(2));
            assertThat(model.getFish(), hasKey(TrophyFish.BLOBFISH));
            assertThat(model.getFish().get(TrophyFish.BLOBFISH).getTotal(), is(5767));
            assertThat(model.getFish().get(TrophyFish.BLOBFISH).getBronze(), is(4044));
            assertThat(model.getFish().get(TrophyFish.GOLDEN_FISH).getTotal(), is(81));
            assertThat(model.getFish().get(TrophyFish.GOLDEN_FISH).getDiamond(), is(5));
        }

        @Test
        public void bareEntryGroupingMixed_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "count": 0,
                    "blobfish_bronze": 50,
                    "gusher": 100,
                    "gusher_silver": 20
                }
                """;

            BareEntryGroupingModel model = gson.fromJson(json, BareEntryGroupingModel.class);

            assertThat(model.getEntries(), aMapWithSize(2));
            assertThat(model.getEntries().get("blobfish").getTotal(), is(0));
            assertThat(model.getEntries().get("blobfish").getBronze(), is(50));
            assertThat(model.getEntries().get("gusher").getTotal(), is(100));
            assertThat(model.getEntries().get("gusher").getSilver(), is(20));
        }

        @Getter
        @NoArgsConstructor
        static class MapOfMapsCaptureModel {

            private String name;
            @Capture
            private ConcurrentMap<String, ConcurrentMap<String, Object>> data = Concurrent.newMap();

        }

        @Test
        public void mapOfMapsCapture_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Raw",
                    "mining": {
                        "core_of_the_mountain": 10,
                        "toggle_core_of_the_mountain": true
                    },
                    "foraging": {
                        "center_of_the_forest": 5
                    }
                }
                """;

            MapOfMapsCaptureModel model = gson.fromJson(json, MapOfMapsCaptureModel.class);

            assertThat(model.getName(), is("Raw"));
            assertThat(model.getData(), aMapWithSize(2));
            assertThat(model.getData(), hasKey("mining"));
            assertThat(model.getData().get("mining"), hasKey("core_of_the_mountain"));
            assertThat(model.getData().get("mining"), hasKey("toggle_core_of_the_mountain"));
            assertThat(model.getData().get("foraging"), hasKey("center_of_the_forest"));
        }

        @Test
        public void mapOfMapsCaptureRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "RT",
                    "mining": {
                        "core_of_the_mountain": 10
                    }
                }
                """;

            MapOfMapsCaptureModel first = gson.fromJson(json, MapOfMapsCaptureModel.class);
            String serialized = gson.toJson(first);
            MapOfMapsCaptureModel second = gson.fromJson(serialized, MapOfMapsCaptureModel.class);

            assertThat(second.getName(), is("RT"));
            assertThat(second.getData(), aMapWithSize(1));
            assertThat(second.getData(), hasKey("mining"));
            assertThat(second.getData().get("mining"), hasKey("core_of_the_mountain"));
        }

        @Getter
        @NoArgsConstructor
        static class PrefixNode {

            @SerializedName("")
            private int level;
            @SerializedName("toggle_")
            private boolean enabled = true;

        }

        @Getter
        @NoArgsConstructor
        static class PrefixGroupingModel {

            @Capture
            private ConcurrentMap<String, PrefixNode> nodes = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class CaretPrefixNode {

            @SerializedName("")
            private int level;
            @SerializedName("^toggle_")
            private boolean enabled = true;

        }

        @Getter
        @NoArgsConstructor
        static class CaretPrefixGroupingModel {

            @Capture
            private ConcurrentMap<String, CaretPrefixNode> nodes = Concurrent.newMap();

        }

        @Test
        public void prefixGrouping_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "mining_speed": 50,
                    "toggle_mining_speed": true,
                    "fortune": 10,
                    "toggle_fortune": false
                }
                """;

            PrefixGroupingModel model = gson.fromJson(json, PrefixGroupingModel.class);

            assertThat(model.getNodes(), aMapWithSize(2));
            assertThat(model.getNodes(), hasKey("mining_speed"));
            assertThat(model.getNodes().get("mining_speed").getLevel(), is(50));
            assertThat(model.getNodes().get("mining_speed").isEnabled(), is(true));
            assertThat(model.getNodes().get("fortune").getLevel(), is(10));
            assertThat(model.getNodes().get("fortune").isEnabled(), is(false));
        }

        @Test
        public void prefixGroupingMissingToggle_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "mining_speed": 50
                }
                """;

            PrefixGroupingModel model = gson.fromJson(json, PrefixGroupingModel.class);

            assertThat(model.getNodes(), aMapWithSize(1));
            assertThat(model.getNodes().get("mining_speed").getLevel(), is(50));
            assertThat(model.getNodes().get("mining_speed").isEnabled(), is(true));
        }

        @Test
        public void prefixGroupingRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "mining_speed": 50,
                    "toggle_mining_speed": true,
                    "fortune": 10,
                    "toggle_fortune": false
                }
                """;

            PrefixGroupingModel first = gson.fromJson(json, PrefixGroupingModel.class);
            String serialized = gson.toJson(first);
            PrefixGroupingModel second = gson.fromJson(serialized, PrefixGroupingModel.class);

            assertThat(second.getNodes(), aMapWithSize(2));
            assertThat(second.getNodes().get("mining_speed").getLevel(), is(50));
            assertThat(second.getNodes().get("mining_speed").isEnabled(), is(true));
            assertThat(second.getNodes().get("fortune").getLevel(), is(10));
            assertThat(second.getNodes().get("fortune").isEnabled(), is(false));
        }

        @Test
        public void caretPrefixGrouping_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "core": 10,
                    "toggle_core": true
                }
                """;

            CaretPrefixGroupingModel model = gson.fromJson(json, CaretPrefixGroupingModel.class);

            assertThat(model.getNodes(), aMapWithSize(1));
            assertThat(model.getNodes().get("core").getLevel(), is(10));
            assertThat(model.getNodes().get("core").isEnabled(), is(true));
        }

        @Test
        public void caretPrefixGroupingRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "core": 10,
                    "toggle_core": true
                }
                """;

            CaretPrefixGroupingModel first = gson.fromJson(json, CaretPrefixGroupingModel.class);
            String serialized = gson.toJson(first);
            CaretPrefixGroupingModel second = gson.fromJson(serialized, CaretPrefixGroupingModel.class);

            assertThat(second.getNodes(), aMapWithSize(1));
            assertThat(second.getNodes().get("core").getLevel(), is(10));
            assertThat(second.getNodes().get("core").isEnabled(), is(true));
        }

        @Getter
        @NoArgsConstructor
        static class EnumValueCaptureModel {

            @Capture
            private ConcurrentMap<String, DojoType> favourites = Concurrent.newMap();

        }

        @Test
        public void captureOverflowMergesBackOnWrite_ok() {
            Gson gson = GSON;

            // the typeFilteredCapture_ok input, serialized rather than only read
            String json = """
                {
                    "name": "TypeFilter",
                    "level": 1,
                    "valid": 42,
                    "invalid": "not_an_int"
                }
                """;

            SimpleCaptureModel model = gson.fromJson(json, SimpleCaptureModel.class);
            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            // the captured entry and the overflowed entry both merge back at the ROOT
            assertThat(result.get("valid").getAsInt(), is(42));
            assertThat(result.get("invalid").getAsString(), is("not_an_int"));
            // @Capture removes its own field's serialized key from the output
            assertThat(result.has("data"), is(false));
            assertThat(result.keySet(), containsInAnyOrder("name", "level", "valid", "invalid"));
        }

        @Test
        public void enumValuedOverflowIsLossless_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "primary": "FORCE",
                    "secondary": "NOT_A_DOJO_TYPE"
                }
                """;

            EnumValueCaptureModel model = gson.fromJson(json, EnumValueCaptureModel.class);

            // an unrecognized enum VALUE is judged incompatible and diverted to overflow
            assertThat(model.getFavourites(), aMapWithSize(1));
            assertThat(model.getFavourites(), hasEntry("primary", DojoType.FORCE));
            assertThat(model.getFavourites(), not(hasKey("secondary")));

            // and it round-trips out of overflow byte-exact
            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);
            assertThat(result.keySet(), containsInAnyOrder("primary", "secondary"));
            assertThat(result.get("primary").getAsString(), is("FORCE"));
            assertThat(result.get("secondary").getAsString(), is("NOT_A_DOJO_TYPE"));
        }

        @Test
        @Disabled("""
            Currently red - "Expected: not map containing [null->ANYTHING] but: was \
            <{FORCE=100, null=2}>". An enum key matching no constant converts to null rather than \
            throwing, so it is judged compatible and every unmatched key in the field binds onto \
            the same null with last-write-wins: BRAND_NEW's 4 is overwritten by ANOTHER_NEW's 2. \
            Enable once an unmatched enum key is diverted to overflow instead.""")
        public void unmatchedEnumKeyDoesNotCollapse_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "dojo_points_FORCE": 100,
                    "dojo_points_BRAND_NEW": 4,
                    "dojo_points_ANOTHER_NEW": 2
                }
                """;

            EnumKeyCaptureModel model = gson.fromJson(json, EnumKeyCaptureModel.class);

            // the loss is N-1 values per field, not one odd key
            assertThat(model.getPoints(), not(hasKey(nullValue(DojoType.class))));
            assertThat(model.getPoints(), aMapWithSize(1));
            assertThat(model.getPoints(), hasEntry(DojoType.FORCE, 100));

            // both unmatched keys survive under their original unstripped spelling
            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);
            assertThat(result.keySet(), containsInAnyOrder("dojo_points_FORCE", "dojo_points_BRAND_NEW", "dojo_points_ANOTHER_NEW"));
            assertThat(result.get("dojo_points_BRAND_NEW").getAsInt(), is(4));
            assertThat(result.get("dojo_points_ANOTHER_NEW").getAsInt(), is(2));
        }

    }

    // ──── LenientTypeAdapterFactory - @Lenient ────

    @Nested
    class LenientTests {

        @Getter
        @NoArgsConstructor
        static class LenientStatsModel {

            private String name;
            @Lenient
            private ConcurrentMap<String, Integer> stats = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class LenientJournalModel {

            @Lenient
            private ConcurrentList<Integer> journals = Concurrent.newList();

        }

        @Getter
        @NoArgsConstructor
        static class LenientPathModel {

            @Lenient
            @SerializedPath("dungeon_journal.unlocked_journals")
            private ConcurrentList<Integer> journals = Concurrent.newList();

        }

        @Getter
        @NoArgsConstructor
        static class CaptureWinsModel {

            private String name;
            @Lenient
            @Capture
            private ConcurrentMap<String, Integer> data = Concurrent.newMap();

        }

        @Test
        public void lenientOverflowMergesBackOnWrite_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Player",
                    "stats": {
                        "health": 100,
                        "last_update": "2024-01-01"
                    }
                }
                """;

            LenientStatsModel model = gson.fromJson(json, LenientStatsModel.class);

            assertThat(model.getStats(), aMapWithSize(1));
            assertThat(model.getStats(), hasEntry("health", 100));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);
            JsonObject stats = result.getAsJsonObject("stats");

            // the overflowed entry returns to the field's OWN sub-object, never to the root
            assertThat(stats.keySet(), containsInAnyOrder("health", "last_update"));
            assertThat(stats.get("last_update").getAsString(), is("2024-01-01"));
            assertThat(result.has("last_update"), is(false));
            assertThat(result.keySet(), containsInAnyOrder("name", "stats"));
        }

        @Test
        public void lenientCollectionOverflowMergesBackOnWrite_ok() {
            Gson gson = GSON;

            // the JsonArray half of the factory - one adoption site in the whole workspace
            String json = """
                {
                    "journals": [1, 2, "expedition_volume_3"]
                }
                """;

            LenientJournalModel model = gson.fromJson(json, LenientJournalModel.class);

            assertThat(model.getJournals(), contains(1, 2));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            assertThat(result.getAsJsonArray("journals").size(), is(3));
            assertThat(result.getAsJsonArray("journals").get(2).getAsString(), is("expedition_volume_3"));
        }

        @Test
        public void serializedPathLenientSourceRoundTrip_ok() {
            Gson gson = GSON;

            // merge-back resolves through locateElement's segment branch, not root.get
            String json = """
                {
                    "dungeon_journal": {
                        "unlocked_journals": [1, "the_watcher"]
                    }
                }
                """;

            LenientPathModel model = gson.fromJson(json, LenientPathModel.class);

            assertThat(model.getJournals(), contains(1));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            assertThat(result.has("journals"), is(false));
            assertThat(result.getAsJsonObject("dungeon_journal").getAsJsonArray("unlocked_journals").size(), is(2));
            assertThat(result.getAsJsonObject("dungeon_journal").getAsJsonArray("unlocked_journals").get(1).getAsString(), is("the_watcher"));
        }

        @Test
        public void lenientFieldWithNoOverflowRoundTripsExactly_ok() {
            Gson gson = GSON;

            // Deliberately NOT named for the publish asymmetry. @Lenient publishes its overflow
            // unconditionally and @Capture only when non-empty, but an empty published overflow
            // and an absent one produce the same output - the merge loop either skips on null or
            // iterates zero entries. Adopting @Capture's policy here was applied as a mutation and
            // the whole suite stayed green, so the asymmetry has no executable guard and this test
            // does not pretend to be one. What it pins is the no-overflow output shape.
            String json = """
                {
                    "name": "Clean",
                    "stats": {
                        "health": 100,
                        "defense": 50
                    }
                }
                """;

            LenientStatsModel model = gson.fromJson(json, LenientStatsModel.class);

            assertThat(model.getStats(), aMapWithSize(2));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            assertThat(result.getAsJsonObject("stats").keySet(), containsInAnyOrder("health", "defense"));
            assertThat(result.keySet(), containsInAnyOrder("name", "stats"));
        }

        @Test
        public void lenientNonObjectRootPassesThrough_ok() {
            Gson gson = GSON;

            // A JSON null is the discriminating input: it short-circuits to the delegate and binds
            // to null. Dropping the short-circuit throws on getAsJsonObject instead, and an array
            // root alone cannot tell the two apart because gson rewraps both as JsonSyntaxException
            assertThat(gson.fromJson("null", LenientStatsModel.class), is(nullValue()));
            assertThrows(JsonSyntaxException.class, () -> gson.fromJson("[1, 2]", LenientStatsModel.class));
        }

        @Test
        public void lenientFieldShapeMismatchIsUnfiltered_ok() {
            Gson gson = GSON;

            // map field against array JSON matches neither filter branch, so the field stays in the
            // tree and is handed raw to the delegate - which means the failure has to come from the
            // delegate's own read. Asserting only the exception type would also accept a throw from
            // inside the filter phase
            JsonSyntaxException thrown = assertThrows(JsonSyntaxException.class, () -> gson.fromJson("""
                {
                    "name": "Mismatch",
                    "stats": [1, 2]
                }
                """, LenientStatsModel.class));

            assertThat(thrown.getMessage(), containsString("BEGIN_ARRAY"));
        }

        @Getter
        @NoArgsConstructor
        static class LenientEnumValueModel {

            @Lenient
            private ConcurrentMap<String, CaptureTests.DojoType> favourites = Concurrent.newMap();

        }

        @Test
        public void lenientEnumValuedOverflowIsLossless_ok() {
            Gson gson = GSON;

            // the enum-VALUE branch of isCompatibleElement, which judges a value no constant
            // matches incompatible and diverts it. It is the @Lenient twin of the @Capture guard,
            // and no other model reaches it - this is its only coverage anywhere
            String json = """
                {
                    "favourites": {
                        "primary": "FORCE",
                        "secondary": "NOT_A_DOJO_TYPE"
                    }
                }
                """;

            LenientEnumValueModel model = gson.fromJson(json, LenientEnumValueModel.class);

            assertThat(model.getFavourites(), aMapWithSize(1));
            assertThat(model.getFavourites(), hasEntry("primary", CaptureTests.DojoType.FORCE));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);
            JsonObject favourites = result.getAsJsonObject("favourites");

            assertThat(favourites.keySet(), containsInAnyOrder("primary", "secondary"));
            assertThat(favourites.get("secondary").getAsString(), is("NOT_A_DOJO_TYPE"));
            assertThat(result.keySet(), contains("favourites"));
        }

        @Test
        public void captureClaimsFieldCarryingBothAnnotations_ok() {
            Gson gson = GSON;

            // Deliberately NOT named for the @Lenient skip at LenientFieldInfo.of. Removing that
            // skip was applied as a mutation and this test stayed green: @Capture is the outer
            // adapter and strips every unknown key, so a @Lenient view of a @Capture field can
            // never be handed its own key and the skip has no observable consequence. What this
            // pins is that the field binds off the ROOT as a capture, not as a lenient sub-object
            String json = """
                {
                    "name": "Both",
                    "alpha": 1,
                    "beta": "not_an_int"
                }
                """;

            CaptureWinsModel model = gson.fromJson(json, CaptureWinsModel.class);

            assertThat(model.getData(), aMapWithSize(1));
            assertThat(model.getData(), hasEntry("alpha", 1));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            assertThat(result.has("data"), is(false));
            assertThat(result.get("alpha").getAsInt(), is(1));
            assertThat(result.get("beta").getAsString(), is("not_an_int"));
        }

    }

    // ──── LenientTypeAdapterFactory - @Extract ────

    @Nested
    class ExtractTests {

        @Getter
        @NoArgsConstructor
        static class ExtractModel {

            private String id;
            @Lenient
            private ConcurrentMap<String, Integer> kills = Concurrent.newMap();
            @Setter
            @Extract("kills.last_killed_mob")
            private String lastKilledMob;

        }

        @Getter
        @NoArgsConstructor
        static class ExtractMistypedModel {

            @Lenient
            private ConcurrentMap<String, Integer> kills = Concurrent.newMap();
            @Extract("kills.last_killed_mob")
            private Integer lastKilledMob = 42;

        }

        @Getter
        @NoArgsConstructor
        static class TwoExtractModel {

            @Lenient
            @SerializedName("armor")
            private ConcurrentMap<Integer, Integer> armorSets = Concurrent.newMap();
            @Extract("armorSets.equipped_set")
            private Optional<Integer> equippedArmorSet = Optional.empty();

            @Lenient
            @SerializedName("equipment")
            private ConcurrentMap<Integer, Integer> equipmentSets = Concurrent.newMap();
            @Extract("equipmentSets.equipped_set")
            private Optional<Integer> equippedEquipmentSet = Optional.empty();

            @Lenient
            @SerializedName("loadouts")
            private ConcurrentMap<Integer, Integer> loadouts = Concurrent.newMap();

        }

        private static final String TWO_EXTRACT_JSON = """
            {
                "armor": { "1": 11, "equipped_set": 2 },
                "equipment": { "1": 21, "equipped_set": 4 },
                "loadouts": { "1": 31 }
            }
            """;

        @Test
        public void extractReinjectsOnWrite_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "id": "player1",
                    "kills": {
                        "zombie_1": 5,
                        "last_killed_mob": "ashfang"
                    },
                    "stat_health": 100
                }
                """;

            CombinationTests.FullCombinationModel model = gson.fromJson(json, CombinationTests.FullCombinationModel.class);

            // observe the claim, not only the output - without this line the plain @Lenient merge
            // loop would put an UNCLAIMED entry back under kills with the same value and the
            // output assertions below would pass with the whole extract phase deleted
            assertThat(model.getLastKilledMob(), is("ashfang"));
            assertThat(model.getKills(), not(hasKey("last_killed_mob")));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            // the claimed entry returns to the source field's own sub-object
            assertThat(result.getAsJsonObject("kills").get("last_killed_mob").getAsString(), is("ashfang"));
            assertThat(result.getAsJsonObject("kills").get("zombie_1").getAsInt(), is(5));
        }

        @Test
        public void extractFieldIsNotEmittedAtRoot_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "id": "player1",
                    "kills": {
                        "zombie_1": 5,
                        "last_killed_mob": "ashfang"
                    },
                    "stat_health": 100
                }
                """;

            CombinationTests.FullCombinationModel model = gson.fromJson(json, CombinationTests.FullCombinationModel.class);
            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            assertThat(result.keySet(), containsInAnyOrder("id", "kills", "stat_health"));
            assertThat(result.has("lastKilledMob"), is(false));
        }

        @Test
        public void lenientExtractRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "id": "player1",
                    "kills": {
                        "zombie_1": 5,
                        "spider_2": 3,
                        "last_killed_mob": "ashfang"
                    },
                    "stat_health": 100,
                    "stat_defense": 50
                }
                """;

            CombinationTests.FullCombinationModel first = gson.fromJson(json, CombinationTests.FullCombinationModel.class);
            JsonObject serialized = gson.fromJson(gson.toJson(first), JsonObject.class);

            // The output currently carries the extracted value TWICE - inside kills and again at
            // the root under the Java field name, which is a @Capture known key and therefore binds
            // reflectively. Strip the duplicate so the second read can only reach the value through
            // the @Extract claim; once the duplicate is gone this removal is a no-op
            serialized.remove("lastKilledMob");

            CombinationTests.FullCombinationModel second = gson.fromJson(serialized, CombinationTests.FullCombinationModel.class);

            assertThat(second.getId(), is(first.getId()));
            assertThat(second.getKills(), is(first.getKills()));
            assertThat(second.getLastKilledMob(), is(first.getLastKilledMob()));
            assertThat(second.getLastKilledMob(), is("ashfang"));
            assertThat(second.getStats(), is(first.getStats()));
        }

        @Test
        public void twoExtractsReturnToOwnSources_ok() {
            Gson gson = GSON;

            TwoExtractModel model = gson.fromJson(TWO_EXTRACT_JSON, TwoExtractModel.class);

            // both keys reach overflow because the KEY fails Integer conversion, and the source
            // is resolved by Java field name - never by @SerializedName
            assertThat(model.getEquippedArmorSet().orElseThrow(), is(2));
            assertThat(model.getEquippedEquipmentSet().orElseThrow(), is(4));
            assertThat(model.getArmorSets(), aMapWithSize(1));
            assertThat(model.getArmorSets(), hasEntry(1, 11));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            // each claim returns to its own source's sub-object, and the third @Lenient field
            // carrying no @Extract is left untouched
            assertThat(result.getAsJsonObject("armor").get("equipped_set").getAsInt(), is(2));
            assertThat(result.getAsJsonObject("equipment").get("equipped_set").getAsInt(), is(4));
            assertThat(result.getAsJsonObject("armor").has("equipped_set"), is(true));
            assertThat(result.getAsJsonObject("equipment").keySet(), containsInAnyOrder("1", "equipped_set"));
            assertThat(result.getAsJsonObject("loadouts").keySet(), contains("1"));
        }

        @Test
        public void twoExtractsNotEmittedAtRoot_ok() {
            Gson gson = GSON;

            TwoExtractModel model = gson.fromJson(TWO_EXTRACT_JSON, TwoExtractModel.class);
            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            assertThat(result.keySet(), containsInAnyOrder("armor", "equipment", "loadouts"));
            assertThat(result.has("equippedArmorSet"), is(false));
            assertThat(result.has("equippedEquipmentSet"), is(false));
        }

        @Test
        public void extractOnHandBuiltObjectReachesDocument_ok() {
            Gson gson = GSON;

            // the object was never read, so nothing published an overflow for its kills map -
            // the write path installs one as a side effect and the entry still reaches the document
            ExtractModel model = new ExtractModel();
            model.setLastKilledMob("phantom");

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            assertThat(result.getAsJsonObject("kills").get("last_killed_mob").getAsString(), is("phantom"));
        }

        @Test
        public void extractMutationReachesDocument_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "id": "player1",
                    "kills": {
                        "zombie_1": 5,
                        "last_killed_mob": "ashfang"
                    }
                }
                """;

            ExtractModel model = gson.fromJson(json, ExtractModel.class);
            assertThat(model.getLastKilledMob(), is("ashfang"));

            model.setLastKilledMob("mutated");

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            assertThat(result.getAsJsonObject("kills").get("last_killed_mob").getAsString(), is("mutated"));
        }

        @Getter
        @NoArgsConstructor
        static class RemainderModel {

            @Lenient
            private ConcurrentMap<String, Integer> rewards = Concurrent.newMap();
            @Extract("rewards")
            private ConcurrentMap<String, String> items = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class FilteredRemainderModel {

            @Lenient
            private ConcurrentMap<String, Integer> rewards = Concurrent.newMap();
            @Extract(value = "rewards", filter = "^quest_")
            private ConcurrentMap<String, String> quests = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class BandedRemainderModel {

            @Lenient
            private ConcurrentMap<String, Integer> rewards = Concurrent.newMap();
            // declared catch-all FIRST, so a green result proves the band sort ran
            @Extract("rewards")
            private ConcurrentMap<String, String> rest = Concurrent.newMap();
            @Extract("rewards.named")
            private String named;

        }

        @Getter
        @NoArgsConstructor
        static class BandedRemainderMirrorModel {

            @Lenient
            private ConcurrentMap<String, Integer> rewards = Concurrent.newMap();
            // the same pair as BandedRemainderModel with the two @Extract fields swapped. One of
            // the two must see the unfavourable reflection order, so the pair catches a missing
            // band sort whichever order the JVM hands the fields back in
            @Extract("rewards.named")
            private String named;
            @Extract("rewards")
            private ConcurrentMap<String, String> rest = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class CaptureRemainderModel {

            @Capture(filter = "^tier_")
            private ConcurrentMap<String, Integer> tiers = Concurrent.newMap();
            @Extract("tiers")
            private ConcurrentMap<String, String> unknownTiers = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class ArrayRemainderModel {

            @Lenient
            private ConcurrentList<Integer> journals = Concurrent.newList();
            @Extract("journals")
            private ConcurrentMap<String, String> extras = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class RemainderConversionFailureModel {

            @Lenient
            private ConcurrentMap<String, Integer> rewards = Concurrent.newMap();
            @Extract("rewards")
            private ConcurrentMap<String, Integer> items = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class FilterOnDottedValueModel {

            @Lenient
            private ConcurrentMap<String, Integer> rewards = Concurrent.newMap();
            @Extract(value = "rewards.named", filter = "^quest_")
            private String named;

        }

        @Getter
        @NoArgsConstructor
        static class TwoCatchAllsModel {

            @Lenient
            private ConcurrentMap<String, Integer> rewards = Concurrent.newMap();
            @Extract("rewards")
            private ConcurrentMap<String, String> first = Concurrent.newMap();
            @Extract("rewards")
            private ConcurrentMap<String, String> second = Concurrent.newMap();

        }

        @Test
        public void extractRemainderFromLenientSource_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "rewards": {
                        "coins": 500,
                        "quest_a": "KADA_LEAD",
                        "quest_b": "MATRIARCH_CHUNK"
                    }
                }
                """;

            RemainderModel model = gson.fromJson(json, RemainderModel.class);

            // the integer half binds, the string half is claimed whole out of the overflow
            assertThat(model.getRewards(), aMapWithSize(1));
            assertThat(model.getRewards(), hasEntry("coins", 500));
            assertThat(model.getItems(), aMapWithSize(2));
            assertThat(model.getItems(), hasEntry("quest_a", "KADA_LEAD"));
            assertThat(model.getItems(), hasEntry("quest_b", "MATRIARCH_CHUNK"));
        }

        @Test
        public void extractRemainderKeysAreNotStripped_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "rewards": {
                        "coins": 500,
                        "quest_a": "KADA_LEAD"
                    }
                }
                """;

            FilteredRemainderModel model = gson.fromJson(json, FilteredRemainderModel.class);

            // selection without stripping - the claimed key keeps the spelling the wire gave it
            assertThat(model.getQuests(), aMapWithSize(1));
            assertThat(model.getQuests(), hasKey("quest_a"));
            assertThat(model.getQuests(), not(hasKey("a")));
        }

        @Test
        public void extractFilteredRemainder_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "rewards": {
                        "coins": 500,
                        "quest_a": "KADA_LEAD",
                        "other": "LEFT_BEHIND"
                    }
                }
                """;

            FilteredRemainderModel model = gson.fromJson(json, FilteredRemainderModel.class);

            // find(), not matches() - the same semantics @Capture uses
            assertThat(model.getQuests(), aMapWithSize(1));
            assertThat(model.getQuests(), hasEntry("quest_a", "KADA_LEAD"));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);
            JsonObject rewards = result.getAsJsonObject("rewards");

            // the unclaimed entry stays in the source's overflow and merges back with the claim
            assertThat(rewards.keySet(), containsInAnyOrder("coins", "quest_a", "other"));
            assertThat(rewards.get("other").getAsString(), is("LEFT_BEHIND"));
            assertThat(result.has("quests"), is(false));
        }

        @Test
        public void extractSingleKeyAndRemainderCoexist_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "rewards": {
                        "coins": 500,
                        "named": "TAKEN_BY_KEY",
                        "other": "TAKEN_BY_REMAINDER"
                    }
                }
                """;

            BandedRemainderModel model = gson.fromJson(json, BandedRemainderModel.class);

            // the exact claim runs first even though the catch-all is declared first
            assertThat(model.getNamed(), is("TAKEN_BY_KEY"));
            assertThat(model.getRest(), aMapWithSize(1));
            assertThat(model.getRest(), hasEntry("other", "TAKEN_BY_REMAINDER"));
            assertThat(model.getRest(), not(hasKey("named")));

            // and the same again with the two fields declared the other way round. Claiming is
            // destructive, so without the band sort whichever of the two sees the unfavourable
            // reflection order lets the catch-all swallow the named key first
            BandedRemainderMirrorModel mirror = gson.fromJson(json, BandedRemainderMirrorModel.class);

            assertThat(mirror.getNamed(), is("TAKEN_BY_KEY"));
            assertThat(mirror.getRest(), aMapWithSize(1));
            assertThat(mirror.getRest(), hasEntry("other", "TAKEN_BY_REMAINDER"));
            assertThat(mirror.getRest(), not(hasKey("named")));
        }

        @Test
        public void extractRemainderRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "rewards": {
                        "coins": 500,
                        "quest_a": "KADA_LEAD",
                        "quest_b": "MATRIARCH_CHUNK"
                    }
                }
                """;

            RemainderModel first = gson.fromJson(json, RemainderModel.class);
            JsonObject result = gson.fromJson(gson.toJson(first), JsonObject.class);

            // every claimed entry goes back into the SOURCE's own sub-object, not the root
            assertThat(result.keySet(), contains("rewards"));
            assertThat(result.getAsJsonObject("rewards").keySet(), containsInAnyOrder("coins", "quest_a", "quest_b"));

            RemainderModel second = gson.fromJson(result, RemainderModel.class);

            assertThat(second.getRewards(), is(first.getRewards()));
            assertThat(second.getItems(), is(first.getItems()));
        }

        @Test
        public void extractRemainderFromCaptureSource_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "tier_one": 1,
                    "tier_broken": "not_an_int",
                    "tier_also_broken": "nor_this"
                }
                """;

            CaptureRemainderModel model = gson.fromJson(json, CaptureRemainderModel.class);

            assertThat(model.getTiers(), aMapWithSize(1));
            assertThat(model.getTiers(), hasEntry("one", 1));
            // @Capture stores overflow under the ORIGINAL unstripped key, and the claim does not
            // normalise that - which is why no prefix is re-applied on the way back
            assertThat(model.getUnknownTiers(), aMapWithSize(2));
            assertThat(model.getUnknownTiers(), hasEntry("tier_broken", "not_an_int"));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            // the SOURCE_OBJECT target sends them back to the root, not into a sub-object
            assertThat(result.keySet(), containsInAnyOrder("tier_one", "tier_broken", "tier_also_broken"));
            assertThat(result.get("tier_broken").getAsString(), is("not_an_int"));
        }

        @Test
        public void extractRemainderConversionFailureRestoresAll_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "rewards": {
                        "coins": 500,
                        "quest_a": "not_an_int",
                        "quest_b": "nor_this"
                    }
                }
                """;

            RemainderConversionFailureModel model = gson.fromJson(json, RemainderConversionFailureModel.class);

            // conversion is all-or-nothing: the field keeps its initialiser rather than holding a
            // partial map, because a partial map with no signal is worse than an empty one
            assertThat(model.getItems(), anEmptyMap());

            // and every claimed entry goes back, so the document is intact
            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            assertThat(result.getAsJsonObject("rewards").keySet(), containsInAnyOrder("coins", "quest_a", "quest_b"));
            assertThat(result.getAsJsonObject("rewards").get("quest_a").getAsString(), is("not_an_int"));
        }

        @Test
        public void extractRemainderOverArrayOverflow_isNoOp() {
            Gson gson = GSON;

            String json = """
                {
                    "journals": [1, 2, "the_watcher"]
                }
                """;

            ArrayRemainderModel model = gson.fromJson(json, ArrayRemainderModel.class);

            // a collection-shaped source has no key space for a filter, so the claim finds nothing
            // and the overflowed element stays where it was
            assertThat(model.getJournals(), contains(1, 2));
            assertThat(model.getExtras(), anEmptyMap());

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            assertThat(result.getAsJsonArray("journals").size(), is(3));
            assertThat(result.getAsJsonArray("journals").get(2).getAsString(), is("the_watcher"));

            // and the companion field keeps its own root key, unlike a claiming @Extract. Nothing
            // can re-inject into an array-shaped overflow, so dropping the key would delete
            // whatever the field held with nowhere to put it
            assertThat(result.has("extras"), is(true));
        }

        @Test
        public void extractFilterOnDottedValue_throws() {
            Gson gson = GSON;

            JsonException thrown = assertThrows(JsonException.class,
                () -> gson.fromJson("{}", FilterOnDottedValueModel.class));

            assertThat(thrown.getMessage(), containsString("filter() must be empty"));
        }

        @Test
        public void extractTwoCatchAllRemaindersOnOneSource_throws() {
            Gson gson = GSON;

            JsonException thrown = assertThrows(JsonException.class,
                () -> gson.fromJson("{}", TwoCatchAllsModel.class));

            assertThat(thrown.getMessage(), containsString("second catch-all remainder"));
        }

        @Test
        public void extractConversionFailureLeavesInitialiser_ok() {
            Gson gson = GSON;

            // the conversion throws and is swallowed, so the field keeps its initialiser and
            // no consumer sees an exception - six live sites rely on this
            String json = """
                {
                    "kills": {
                        "zombie_1": 5,
                        "last_killed_mob": "ashfang"
                    }
                }
                """;

            ExtractMistypedModel model = gson.fromJson(json, ExtractMistypedModel.class);

            assertThat(model.getLastKilledMob(), is(42));
            assertThat(model.getKills(), aMapWithSize(1));
            assertThat(model.getKills(), hasEntry("zombie_1", 5));
        }

    }

    // ──── CollapseTypeAdapterFactory ────

    @Nested
    class CollapseTests {

        @Getter
        @NoArgsConstructor
        static class Boss {

            @Key
            private transient String id;
            private double xp;
            private int level;

        }

        @Getter
        @NoArgsConstructor
        static class MapCollapseModel {

            private String name;
            @Collapse
            @SerializedName("bosses")
            private ConcurrentMap<String, Boss> bosses = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class ListCollapseModel {

            private String name;
            @Collapse
            @SerializedName("bosses")
            private ConcurrentList<Boss> bosses = Concurrent.newList();

        }

        @Test
        public void mapCollapse_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Test",
                    "bosses": {
                        "zombie": {"xp": 100.0, "level": 5},
                        "spider": {"xp": 50.0, "level": 3}
                    }
                }
                """;

            MapCollapseModel model = gson.fromJson(json, MapCollapseModel.class);

            assertThat(model.getName(), is("Test"));
            assertThat(model.getBosses(), aMapWithSize(2));
            assertThat(model.getBosses().get("zombie").getId(), is("zombie"));
            assertThat(model.getBosses().get("zombie").getXp(), is(100.0));
            assertThat(model.getBosses().get("zombie").getLevel(), is(5));
            assertThat(model.getBosses().get("spider").getId(), is("spider"));
            assertThat(model.getBosses().get("spider").getXp(), is(50.0));
        }

        @Test
        public void listCollapse_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Test",
                    "bosses": {
                        "zombie": {"xp": 100.0, "level": 5},
                        "spider": {"xp": 50.0, "level": 3}
                    }
                }
                """;

            ListCollapseModel model = gson.fromJson(json, ListCollapseModel.class);

            assertThat(model.getName(), is("Test"));
            assertThat(model.getBosses(), hasSize(2));
            assertThat(model.getBosses().get(0).getId(), is("zombie"));
            assertThat(model.getBosses().get(0).getXp(), is(100.0));
            assertThat(model.getBosses().get(0).getLevel(), is(5));
            assertThat(model.getBosses().get(1).getId(), is("spider"));
            assertThat(model.getBosses().get(1).getXp(), is(50.0));
        }

        @Test
        public void mapCollapseRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "RT",
                    "bosses": {
                        "zombie": {"xp": 100.0, "level": 5},
                        "spider": {"xp": 50.0, "level": 3}
                    }
                }
                """;

            MapCollapseModel first = gson.fromJson(json, MapCollapseModel.class);
            String serialized = gson.toJson(first);
            MapCollapseModel second = gson.fromJson(serialized, MapCollapseModel.class);

            assertThat(second.getName(), is("RT"));
            assertThat(second.getBosses(), aMapWithSize(2));
            assertThat(second.getBosses().get("zombie").getId(), is("zombie"));
            assertThat(second.getBosses().get("zombie").getXp(), is(100.0));
            assertThat(second.getBosses().get("spider").getId(), is("spider"));
        }

        @Test
        public void listCollapseRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "RT",
                    "bosses": {
                        "zombie": {"xp": 100.0, "level": 5},
                        "spider": {"xp": 50.0, "level": 3}
                    }
                }
                """;

            ListCollapseModel first = gson.fromJson(json, ListCollapseModel.class);
            String serialized = gson.toJson(first);

            // Round-trip through list produces a JSON object (keyed by @Key field)
            ListCollapseModel second = gson.fromJson(serialized, ListCollapseModel.class);

            assertThat(second.getBosses(), hasSize(2));
            assertThat(second.getBosses().get(0).getId(), is("zombie"));
            assertThat(second.getBosses().get(0).getXp(), is(100.0));
            assertThat(second.getBosses().get(1).getId(), is("spider"));
        }

        @Test
        public void emptyCollapse_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Empty",
                    "bosses": {}
                }
                """;

            MapCollapseModel model = gson.fromJson(json, MapCollapseModel.class);

            assertThat(model.getName(), is("Empty"));
            assertThat(model.getBosses(), anEmptyMap());
        }

        @Test
        public void mapAndListProduceSameValues_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Same",
                    "bosses": {
                        "enderman": {"xp": 200.0, "level": 7}
                    }
                }
                """;

            MapCollapseModel mapModel = gson.fromJson(json, MapCollapseModel.class);
            ListCollapseModel listModel = gson.fromJson(json, ListCollapseModel.class);

            Boss fromMap = mapModel.getBosses().get("enderman");
            Boss fromList = listModel.getBosses().get(0);

            assertThat(fromMap.getId(), is(fromList.getId()));
            assertThat(fromMap.getXp(), is(fromList.getXp()));
            assertThat(fromMap.getLevel(), is(fromList.getLevel()));
        }

        @Getter
        @NoArgsConstructor
        static class NoKeyBoss {

            private double xp;
            private int level;

        }

        @Getter
        @NoArgsConstructor
        static class NoKeyMapModel {

            @Collapse
            @SerializedName("bosses")
            private ConcurrentMap<String, NoKeyBoss> bosses = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class NoKeyListModel {

            @Collapse
            @SerializedName("bosses")
            private ConcurrentList<NoKeyBoss> bosses = Concurrent.newList();

        }

        @Test
        public void mapCollapseNoKey_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "bosses": {
                        "zombie": {"xp": 100.0, "level": 5},
                        "spider": {"xp": 50.0, "level": 3}
                    }
                }
                """;

            NoKeyMapModel model = gson.fromJson(json, NoKeyMapModel.class);

            assertThat(model.getBosses(), aMapWithSize(2));
            assertThat(model.getBosses().get("zombie").getXp(), is(100.0));
            assertThat(model.getBosses().get("spider").getLevel(), is(3));
        }

        @Test
        public void mapCollapseNoKeyRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "bosses": {
                        "zombie": {"xp": 100.0, "level": 5}
                    }
                }
                """;

            NoKeyMapModel first = gson.fromJson(json, NoKeyMapModel.class);
            String serialized = gson.toJson(first);
            NoKeyMapModel second = gson.fromJson(serialized, NoKeyMapModel.class);

            assertThat(second.getBosses(), aMapWithSize(1));
            assertThat(second.getBosses().get("zombie").getXp(), is(100.0));
            assertThat(second.getBosses().get("zombie").getLevel(), is(5));
        }

        @Test
        public void listCollapseNoKey_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "bosses": {
                        "zombie": {"xp": 100.0, "level": 5},
                        "spider": {"xp": 50.0, "level": 3}
                    }
                }
                """;

            NoKeyListModel model = gson.fromJson(json, NoKeyListModel.class);

            assertThat(model.getBosses(), hasSize(2));
            assertThat(model.getBosses().get(0).getXp(), is(100.0));
            assertThat(model.getBosses().get(1).getLevel(), is(3));
        }

        @Test
        public void listCollapseNoKeyRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "bosses": {
                        "zombie": {"xp": 100.0, "level": 5},
                        "spider": {"xp": 50.0, "level": 3}
                    }
                }
                """;

            NoKeyListModel first = gson.fromJson(json, NoKeyListModel.class);
            String serialized = gson.toJson(first);
            NoKeyListModel second = gson.fromJson(serialized, NoKeyListModel.class);

            assertThat(second.getBosses(), hasSize(2));
            assertThat(second.getBosses().get(0).getXp(), is(100.0));
            assertThat(second.getBosses().get(0).getLevel(), is(5));
            assertThat(second.getBosses().get(1).getXp(), is(50.0));
            assertThat(second.getBosses().get(1).getLevel(), is(3));
        }

        enum Tier { BRONZE, SILVER }

        @Getter
        @NoArgsConstructor
        @EqualsAndHashCode
        static class TieredBoss {

            @Key
            private Tier tier;
            private int level;

        }

        @Getter
        @NoArgsConstructor
        static class TieredListModel {

            @Collapse
            @SerializedName("bosses")
            private ConcurrentList<TieredBoss> bosses = Concurrent.newList();

        }

        @Getter
        @NoArgsConstructor
        @EqualsAndHashCode
        static class ValueBoss {

            private int level;

        }

        @Getter
        @NoArgsConstructor
        static class ValueListModel {

            @Collapse
            @SerializedName("bosses")
            private ConcurrentList<ValueBoss> bosses = Concurrent.newList();

        }

        private static JsonObject collapsedBosses(String json) {
            return JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("bosses");
        }

        @Test
        public void listCollapseKeyOrderSurvivesKeyInjection_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "bosses": {
                        "bronze": {"level": 5},
                        "silver": {"level": 3}
                    }
                }
                """;

            TieredListModel model = gson.fromJson(json, TieredListModel.class);

            assertThat(model.getBosses(), hasSize(2));
            assertThat(model.getBosses().get(0).getTier(), is(Tier.BRONZE));
            assertThat(model.getBosses().get(1).getTier(), is(Tier.SILVER));

            // Deserialization injects the @Key field into every element, which changes what
            // the tracked list is worth by value. The original key spelling must survive that,
            // rather than being re-derived from the injected constant as "BRONZE"/"SILVER".
            assertThat(collapsedBosses(gson.toJson(model)).keySet(), contains("bronze", "silver"));
        }

        @Test
        public void listCollapseKeyOrderIsPerListInstance_ok() {
            Gson gson = GSON;

            ValueListModel first = gson.fromJson("{\"bosses\":{\"alpha\":{\"level\":5}}}", ValueListModel.class);
            ValueListModel second = gson.fromJson("{\"bosses\":{\"beta\":{\"level\":5}}}", ValueListModel.class);

            // Two lists holding equal values are still two lists, each with its own key order.
            assertThat(collapsedBosses(gson.toJson(first)).keySet(), contains("alpha"));
            assertThat(collapsedBosses(gson.toJson(second)).keySet(), contains("beta"));
        }

    }

    // ──── SplitTypeAdapterFactory ────

    @Nested
    class SplitTests {

        enum Animal { CAT, DOG, FISH }
        enum Color { RED, BLUE, GREEN }

        @Getter
        @NoArgsConstructor
        static class SplitPairOptionalModel {

            private String name;
            @Split("/")
            @SerializedName("combo")
            private PairOptional<Animal, Color> combo = PairOptional.empty();

        }

        @Getter
        @NoArgsConstructor
        static class SplitPairModel {

            private String id;
            @Split(":")
            private Pair<String, Integer> range;

        }

        @Test
        public void readSplit_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "test",
                    "combo": "cat/red"
                }
                """;

            SplitPairOptionalModel model = gson.fromJson(json, SplitPairOptionalModel.class);

            assertThat(model.getName(), is("test"));
            assertThat(model.getCombo().isPresent(), is(true));
            assertThat(model.getCombo().left(), is(Animal.CAT));
            assertThat(model.getCombo().right(), is(Color.RED));
        }

        @Test
        public void readSplitMissing_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "test"
                }
                """;

            SplitPairOptionalModel model = gson.fromJson(json, SplitPairOptionalModel.class);

            assertThat(model.getName(), is("test"));
            assertThat(model.getCombo().isEmpty(), is(true));
        }

        @Test
        public void readSplitNull_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "test",
                    "combo": null
                }
                """;

            SplitPairOptionalModel model = gson.fromJson(json, SplitPairOptionalModel.class);

            assertThat(model.getName(), is("test"));
            assertThat(model.getCombo().isEmpty(), is(true));
        }

        @Test
        public void writeSplit_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "test",
                    "combo": "cat/red"
                }
                """;

            SplitPairOptionalModel model = gson.fromJson(json, SplitPairOptionalModel.class);
            String output = gson.toJson(model);
            JsonObject result = gson.fromJson(output, JsonObject.class);

            assertThat(result.get("name").getAsString(), is("test"));
            assertThat(result.get("combo").getAsString(), is("CAT/RED"));
        }

        @Test
        public void splitRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "rt",
                    "combo": "DOG/BLUE"
                }
                """;

            SplitPairOptionalModel first = gson.fromJson(json, SplitPairOptionalModel.class);
            String serialized = gson.toJson(first);
            SplitPairOptionalModel second = gson.fromJson(serialized, SplitPairOptionalModel.class);

            assertThat(second.getName(), is(first.getName()));
            assertThat(second.getCombo().isPresent(), is(true));
            assertThat(second.getCombo().left(), is(Animal.DOG));
            assertThat(second.getCombo().right(), is(Color.BLUE));
        }

        @Test
        public void splitPair_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "id": "test",
                    "range": "hello:42"
                }
                """;

            SplitPairModel model = gson.fromJson(json, SplitPairModel.class);

            assertThat(model.getId(), is("test"));
            assertThat(model.getRange(), notNullValue());
            assertThat(model.getRange().left(), is("hello"));
            assertThat(model.getRange().right(), is(42));
        }

    }

    // ──── PostInitTypeAdapterFactory ────

    @Nested
    class PostInitTests {

        @Getter
        @NoArgsConstructor
        static class PostInitModel implements PostInit {

            private String firstName;
            private String lastName;
            private transient String fullName;

            @Override
            public void postInit() {
                this.fullName = this.firstName + " " + this.lastName;
            }

        }

        @Getter
        @NoArgsConstructor
        static class PlainModel {

            private String key;
            private int value;

        }

        @Getter
        @NoArgsConstructor
        static class FailingPostInitModel implements PostInit {

            private String data;
            private transient boolean postInitCalled;

            @Override
            public void postInit() {
                this.postInitCalled = true;
                throw new RuntimeException("Intentional failure");
            }

        }

        @Test
        public void postInitCalledOnDeserialize_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "firstName": "John",
                    "lastName": "Doe"
                }
                """;

            PostInitModel model = gson.fromJson(json, PostInitModel.class);

            assertThat(model.getFirstName(), is("John"));
            assertThat(model.getLastName(), is("Doe"));
            assertThat(model.getFullName(), is("John Doe"));
        }

        @Test
        public void postInitNotCalledOnSerialize_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "firstName": "Jane",
                    "lastName": "Doe"
                }
                """;

            PostInitModel model = gson.fromJson(json, PostInitModel.class);
            assertThat(model.getFullName(), is("Jane Doe"));

            // Transient field should not appear in output
            String output = gson.toJson(model);
            JsonObject result = gson.fromJson(output, JsonObject.class);

            assertThat(result.get("firstName").getAsString(), is("Jane"));
            assertThat(result.get("lastName").getAsString(), is("Doe"));
            assertThat(result.has("fullName"), is(false));
        }

        @Test
        public void nonPostInitTypeUnaffected_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "key": "test",
                    "value": 42
                }
                """;

            PlainModel model = gson.fromJson(json, PlainModel.class);

            assertThat(model.getKey(), is("test"));
            assertThat(model.getValue(), is(42));
        }

        @Test
        public void postInitExceptionSwallowed_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "data": "some data"
                }
                """;

            // Should not throw despite postInit failing
            FailingPostInitModel model = gson.fromJson(json, FailingPostInitModel.class);

            assertThat(model.getData(), is("some data"));
            assertThat(model.isPostInitCalled(), is(true));
        }

        @Test
        public void roundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "firstName": "Alice",
                    "lastName": "Smith"
                }
                """;

            PostInitModel first = gson.fromJson(json, PostInitModel.class);
            String serialized = gson.toJson(first);
            PostInitModel second = gson.fromJson(serialized, PostInitModel.class);

            assertThat(second.getFirstName(), is(first.getFirstName()));
            assertThat(second.getLastName(), is(first.getLastName()));
            assertThat(second.getFullName(), is("Alice Smith"));
        }

    }

    // ──── Cross-Annotation Combination Tests ────

    @Nested
    class CombinationTests {

        // --- @Capture(descend = true) with grouping ---

        @Getter
        @NoArgsConstructor
        static class ClaimedLevel {

            @SerializedName("")
            private boolean claimed;
            private boolean special;

        }

        @Getter
        @NoArgsConstructor
        static class DescendCaptureModel {

            private double xp;
            @Capture(filter = "^level_", descend = true)
            @SerializedName("claimed_levels")
            private ConcurrentMap<Integer, ClaimedLevel> claimedLevels = Concurrent.newMap();

        }

        @Test
        public void descendCapture_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "xp": 100.0,
                    "claimed_levels": {
                        "level_1": true,
                        "level_2": true,
                        "level_8": true,
                        "level_8_special": true
                    }
                }
                """;

            DescendCaptureModel model = gson.fromJson(json, DescendCaptureModel.class);

            assertThat(model.getXp(), is(100.0));
            assertThat(model.getClaimedLevels(), aMapWithSize(3));
            assertThat(model.getClaimedLevels().get(1).isClaimed(), is(true));
            assertThat(model.getClaimedLevels().get(1).isSpecial(), is(false));
            assertThat(model.getClaimedLevels().get(2).isClaimed(), is(true));
            assertThat(model.getClaimedLevels().get(8).isClaimed(), is(true));
            assertThat(model.getClaimedLevels().get(8).isSpecial(), is(true));
        }

        @Test
        public void descendCaptureRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "xp": 100.0,
                    "claimed_levels": {
                        "level_1": true,
                        "level_8": true,
                        "level_8_special": true
                    }
                }
                """;

            DescendCaptureModel first = gson.fromJson(json, DescendCaptureModel.class);
            String serialized = gson.toJson(first);
            DescendCaptureModel second = gson.fromJson(serialized, DescendCaptureModel.class);

            assertThat(second.getXp(), is(100.0));
            assertThat(second.getClaimedLevels(), aMapWithSize(2));
            assertThat(second.getClaimedLevels().get(1).isClaimed(), is(true));
            assertThat(second.getClaimedLevels().get(8).isClaimed(), is(true));
            assertThat(second.getClaimedLevels().get(8).isSpecial(), is(true));
        }

        @Test
        public void descendCaptureSimple_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "xp": 50.0,
                    "claimed_levels": {
                        "level_1": true,
                        "level_3": true
                    }
                }
                """;

            DescendCaptureModel model = gson.fromJson(json, DescendCaptureModel.class);

            assertThat(model.getClaimedLevels(), aMapWithSize(2));
            assertThat(model.getClaimedLevels().get(1).isClaimed(), is(true));
            assertThat(model.getClaimedLevels().get(1).isSpecial(), is(false));
            assertThat(model.getClaimedLevels().get(3).isClaimed(), is(true));
        }

        // --- @Collapse + @Capture on inner type ---

        @Getter
        @NoArgsConstructor
        static class InnerBoss {

            @Key
            private transient String id;
            @SerializedName("xp")
            private double experience;
            @Capture(filter = "^boss_kills_tier_")
            private ConcurrentMap<Integer, Integer> kills = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class CollapseWithCaptureModel {

            private String name;
            @Collapse
            @SerializedName("bosses")
            private ConcurrentList<InnerBoss> bosses = Concurrent.newList();

        }

        @Test
        public void collapseWithCapture_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Test",
                    "bosses": {
                        "zombie": {
                            "xp": 100.0,
                            "boss_kills_tier_0": 10,
                            "boss_kills_tier_1": 5
                        },
                        "spider": {
                            "xp": 50.0,
                            "boss_kills_tier_0": 3
                        }
                    }
                }
                """;

            CollapseWithCaptureModel model = gson.fromJson(json, CollapseWithCaptureModel.class);

            assertThat(model.getName(), is("Test"));
            assertThat(model.getBosses(), hasSize(2));

            InnerBoss zombie = model.getBosses().get(0);
            assertThat(zombie.getId(), is("zombie"));
            assertThat(zombie.getExperience(), is(100.0));
            assertThat(zombie.getKills(), aMapWithSize(2));
            assertThat(zombie.getKills(), hasEntry(0, 10));
            assertThat(zombie.getKills(), hasEntry(1, 5));

            InnerBoss spider = model.getBosses().get(1);
            assertThat(spider.getId(), is("spider"));
            assertThat(spider.getExperience(), is(50.0));
            assertThat(spider.getKills(), aMapWithSize(1));
            assertThat(spider.getKills(), hasEntry(0, 3));
        }

        @Test
        public void collapseWithCaptureRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "RT",
                    "bosses": {
                        "zombie": {
                            "xp": 100.0,
                            "boss_kills_tier_0": 10
                        }
                    }
                }
                """;

            CollapseWithCaptureModel first = gson.fromJson(json, CollapseWithCaptureModel.class);
            String serialized = gson.toJson(first);
            CollapseWithCaptureModel second = gson.fromJson(serialized, CollapseWithCaptureModel.class);

            assertThat(second.getBosses(), hasSize(1));
            assertThat(second.getBosses().get(0).getId(), is("zombie"));
            assertThat(second.getBosses().get(0).getExperience(), is(100.0));
            assertThat(second.getBosses().get(0).getKills(), hasEntry(0, 10));
        }

        // --- @Collapse + @Capture(descend=true) on inner type ---

        @Getter
        @NoArgsConstructor
        static class FullBoss {

            @Key
            private transient String id;
            @SerializedName("xp")
            private double experience;
            @Capture(filter = "^boss_kills_tier_")
            private ConcurrentMap<Integer, Integer> kills = Concurrent.newMap();
            @Capture(filter = "^level_", descend = true)
            @SerializedName("claimed_levels")
            private ConcurrentMap<Integer, ClaimedLevel> claimedLevels = Concurrent.newMap();

        }

        @Getter
        @NoArgsConstructor
        static class FullSlayersModel {

            @Collapse
            @SerializedName("slayer_bosses")
            private ConcurrentList<FullBoss> bosses = Concurrent.newList();

        }

        @Test
        public void collapseWithCaptureAndDescend_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "slayer_bosses": {
                        "zombie": {
                            "xp": 2000.0,
                            "boss_kills_tier_0": 18,
                            "boss_kills_tier_3": 100,
                            "claimed_levels": {
                                "level_1": true,
                                "level_5": true,
                                "level_5_special": true
                            }
                        }
                    }
                }
                """;

            FullSlayersModel model = gson.fromJson(json, FullSlayersModel.class);

            assertThat(model.getBosses(), hasSize(1));

            FullBoss zombie = model.getBosses().get(0);
            assertThat(zombie.getId(), is("zombie"));
            assertThat(zombie.getExperience(), is(2000.0));
            assertThat(zombie.getKills(), hasEntry(0, 18));
            assertThat(zombie.getKills(), hasEntry(3, 100));
            assertThat(zombie.getClaimedLevels(), aMapWithSize(2));
            assertThat(zombie.getClaimedLevels().get(1).isClaimed(), is(true));
            assertThat(zombie.getClaimedLevels().get(5).isClaimed(), is(true));
            assertThat(zombie.getClaimedLevels().get(5).isSpecial(), is(true));
        }

        @Test
        public void collapseWithCaptureAndDescendRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "slayer_bosses": {
                        "zombie": {
                            "xp": 2000.0,
                            "boss_kills_tier_0": 18,
                            "claimed_levels": {
                                "level_1": true,
                                "level_5": true,
                                "level_5_special": true
                            }
                        }
                    }
                }
                """;

            FullSlayersModel first = gson.fromJson(json, FullSlayersModel.class);
            String serialized = gson.toJson(first);
            FullSlayersModel second = gson.fromJson(serialized, FullSlayersModel.class);

            FullBoss zombie = second.getBosses().get(0);
            assertThat(zombie.getId(), is("zombie"));
            assertThat(zombie.getExperience(), is(2000.0));
            assertThat(zombie.getKills(), hasEntry(0, 18));
            assertThat(zombie.getClaimedLevels().get(1).isClaimed(), is(true));
            assertThat(zombie.getClaimedLevels().get(5).isClaimed(), is(true));
            assertThat(zombie.getClaimedLevels().get(5).isSpecial(), is(true));
        }

        // --- @Lenient + @Capture on same class ---

        @Getter
        @NoArgsConstructor
        static class LenientWithCaptureModel {

            private String name;
            @Lenient
            private ConcurrentMap<String, Integer> stats = Concurrent.newMap();
            @Capture(filter = "^bonus_")
            private ConcurrentMap<String, Integer> bonuses = Concurrent.newMap();

        }

        @Test
        public void lenientWithCapture_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Player",
                    "stats": {
                        "health": 100,
                        "defense": 50,
                        "last_update": "2024-01-01"
                    },
                    "bonus_health": 10,
                    "bonus_defense": 5
                }
                """;

            LenientWithCaptureModel model = gson.fromJson(json, LenientWithCaptureModel.class);

            assertThat(model.getName(), is("Player"));
            assertThat(model.getStats(), aMapWithSize(2));
            assertThat(model.getStats(), hasEntry("health", 100));
            assertThat(model.getStats(), hasEntry("defense", 50));
            assertThat(model.getBonuses(), aMapWithSize(2));
            assertThat(model.getBonuses(), hasEntry("health", 10));
            assertThat(model.getBonuses(), hasEntry("defense", 5));
        }

        // --- @Lenient + @Extract + @Capture on same class ---

        @Getter
        @NoArgsConstructor
        static class FullCombinationModel {

            private String id;
            @Lenient
            private ConcurrentMap<String, Integer> kills = Concurrent.newMap();
            @Extract("kills.last_killed_mob")
            private String lastKilledMob;
            @Capture(filter = "^stat_")
            private ConcurrentMap<String, Integer> stats = Concurrent.newMap();

        }

        @Test
        public void lenientExtractCapture_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "id": "player1",
                    "kills": {
                        "zombie_1": 5,
                        "spider_2": 3,
                        "last_killed_mob": "ashfang"
                    },
                    "stat_health": 100,
                    "stat_defense": 50
                }
                """;

            FullCombinationModel model = gson.fromJson(json, FullCombinationModel.class);

            assertThat(model.getId(), is("player1"));
            assertThat(model.getKills(), aMapWithSize(2));
            assertThat(model.getKills(), hasEntry("zombie_1", 5));
            assertThat(model.getKills(), hasEntry("spider_2", 3));
            assertThat(model.getLastKilledMob(), is("ashfang"));
            assertThat(model.getStats(), aMapWithSize(2));
            assertThat(model.getStats(), hasEntry("health", 100));
            assertThat(model.getStats(), hasEntry("defense", 50));
        }

        @Test
        public void lenientAndCaptureOverflowGoToDifferentTargets_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Player",
                    "stats": {
                        "health": 100,
                        "last_update": "2024-01-01"
                    },
                    "bonus_health": 10,
                    "bonus_broken": "not_an_int"
                }
                """;

            LenientWithCaptureModel model = gson.fromJson(json, LenientWithCaptureModel.class);

            assertThat(model.getStats(), aMapWithSize(1));
            assertThat(model.getStats(), hasEntry("health", 100));
            assertThat(model.getBonuses(), aMapWithSize(1));
            assertThat(model.getBonuses(), hasEntry("health", 10));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            // the @Lenient entry merges back into the field's own sub-object
            assertThat(result.getAsJsonObject("stats").get("last_update").getAsString(), is("2024-01-01"));
            // the @Capture entry merges back at the ROOT, under its original unstripped key
            assertThat(result.get("bonus_broken").getAsString(), is("not_an_int"));
            assertThat(result.get("bonus_health").getAsInt(), is(10));

            // and neither lands in the other's target
            assertThat(result.has("last_update"), is(false));
            assertThat(result.getAsJsonObject("stats").has("bonus_broken"), is(false));
            assertThat(result.has("bonuses"), is(false));
            assertThat(result.keySet(), containsInAnyOrder("name", "stats", "bonus_health", "bonus_broken"));
        }

        @Getter
        @NoArgsConstructor
        static class ExtractOverCaptureModel {

            private String name;
            @Capture(filter = "^tier_")
            private ConcurrentMap<String, Integer> tiers = Concurrent.newMap();
            @Extract("tiers.tier_broken")
            private String brokenTier;

        }

        @Test
        public void extractOverCaptureSource_ok() {
            Gson gson = GSON;

            // No @Extract in the workspace names a @Capture field, because until the claim moved
            // outside @Capture doing so was a silent no-op - the captured map that keys the store
            // is built fourteen source lines after the old claim ran.
            String json = """
                {
                    "name": "Player",
                    "tier_one": 1,
                    "tier_broken": "not_an_int"
                }
                """;

            ExtractOverCaptureModel model = gson.fromJson(json, ExtractOverCaptureModel.class);

            assertThat(model.getTiers(), aMapWithSize(1));
            assertThat(model.getTiers(), hasEntry("one", 1));
            assertThat(model.getBrokenTier(), is("not_an_int"));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);

            // the claim goes back the @Capture way - to the ROOT, under the original unstripped
            // key - and the companion field's own key is gone
            assertThat(result.keySet(), containsInAnyOrder("name", "tier_one", "tier_broken"));
            assertThat(result.get("tier_broken").getAsString(), is("not_an_int"));
            assertThat(result.has("brokenTier"), is(false));
        }

        @Test
        public void extractOverCaptureRoundTrip_ok() {
            Gson gson = GSON;

            String json = """
                {
                    "name": "Player",
                    "tier_one": 1,
                    "tier_broken": "not_an_int"
                }
                """;

            ExtractOverCaptureModel first = gson.fromJson(json, ExtractOverCaptureModel.class);
            ExtractOverCaptureModel second = gson.fromJson(gson.toJson(first), ExtractOverCaptureModel.class);

            assertThat(second.getName(), is(first.getName()));
            assertThat(second.getTiers(), is(first.getTiers()));
            assertThat(second.getBrokenTier(), is("not_an_int"));
        }

        @Getter
        @NoArgsConstructor
        static class ExtractInsideCaptureValue {

            @Lenient
            private ConcurrentMap<String, Integer> counts = Concurrent.newMap();
            @Extract("counts.label")
            private String label;

        }

        @Getter
        @NoArgsConstructor
        static class ExtractNestedInsideCaptureModel {

            // ENTRY mode, so each value is read and written whole rather than affix-split - the
            // point here is the value's own adapter stack, not @Capture's grouping
            @Capture(grouping = Capture.Grouping.ENTRY)
            private ConcurrentMap<String, ExtractInsideCaptureValue> nodes = Concurrent.newMap();

        }

        @Test
        public void extractNestedInsideCapture_ok() {
            Gson gson = GSON;

            // a @Capture map's value class is deserialized with a fresh top-of-chain lookup, so the
            // value's own @Extract runs in its own adapter stack rather than the enclosing one
            String json = """
                {
                    "alpha": {
                        "counts": { "hits": 3, "label": "first" }
                    }
                }
                """;

            ExtractNestedInsideCaptureModel model = gson.fromJson(json, ExtractNestedInsideCaptureModel.class);

            assertThat(model.getNodes(), aMapWithSize(1));
            assertThat(model.getNodes().get("alpha").getCounts(), hasEntry("hits", 3));
            assertThat(model.getNodes().get("alpha").getLabel(), is("first"));

            JsonObject result = gson.fromJson(gson.toJson(model), JsonObject.class);
            JsonObject alpha = result.getAsJsonObject("alpha");

            assertThat(alpha.keySet(), contains("counts"));
            assertThat(alpha.getAsJsonObject("counts").get("label").getAsString(), is("first"));
        }

    }

    // ──── HTML escaping toggle ────

    @Nested
    class HtmlEscapingTests {

        @Test
        public void defaultEscapesHtml_ok() {
            Gson gson = GsonSettings.defaults().create();

            // '=' is HTML-escaped to its unicode form by default
            assertThat(gson.toJson("a=b"), is("\"a\\u003db\""));
        }

        @Test
        public void disabledEmitsRaw_ok() {
            Gson gson = GsonSettings.defaults()
                .mutate()
                .isHtmlEscaping(false)
                .build()
                .create();

            assertThat(gson.toJson("a=b"), is("\"a=b\""));
        }

        @Test
        public void mutateCarriesFlag_ok() {
            GsonSettings settings = GsonSettings.defaults()
                .mutate()
                .isHtmlEscaping(false)
                .build();

            assertThat(settings.isHtmlEscaping(), is(false));
            // defaults() leaves escaping on
            assertThat(GsonSettings.defaults().isHtmlEscaping(), is(true));
        }

    }

}
