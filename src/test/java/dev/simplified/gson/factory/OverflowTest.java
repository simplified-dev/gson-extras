package dev.simplified.gson.factory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.gson.GsonSettings;
import dev.simplified.gson.annotation.Capture;
import dev.simplified.gson.annotation.Lenient;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Covers the shared overflow store directly, and the two publish policies its callers keep.
 */
public class OverflowTest {

    private static final Gson GSON = GsonSettings.defaults().create();

    private static @NotNull JsonObject objectWith(@NotNull String key, @NotNull String value) {
        JsonObject object = new JsonObject();
        object.add(key, new JsonPrimitive(value));
        return object;
    }

    @Test
    public void publishThenFindSameTarget_ok() {
        Object owner = new Object();
        JsonObject overflow = objectWith("a", "1");

        Overflow.publish(owner, Overflow.Target.FIELD_ELEMENT, overflow);

        assertThat(Overflow.find(owner, Overflow.Target.FIELD_ELEMENT), is(sameInstance(overflow)));
    }

    @Test
    public void findWithOtherTargetReturnsNull_ok() {
        Object owner = new Object();

        Overflow.publish(owner, Overflow.Target.FIELD_ELEMENT, objectWith("a", "1"));

        // the store is tagged, not a union - a @Capture merge must not pick up a @Lenient entry
        assertThat(Overflow.find(owner, Overflow.Target.SOURCE_OBJECT), is(nullValue()));
    }

    @Test
    public void findOnUnknownOwnerReturnsNull_ok() {
        assertThat(Overflow.find(new Object(), Overflow.Target.FIELD_ELEMENT), is(nullValue()));
    }

    @Test
    public void openCreatesWhenAbsent_ok() {
        Object owner = new Object();

        JsonElement opened = Overflow.open(owner, Overflow.Target.SOURCE_OBJECT, JsonObject::new);

        assertThat(opened, is(notNullValue()));
        assertThat(Overflow.find(owner, Overflow.Target.SOURCE_OBJECT), is(sameInstance(opened)));
    }

    @Test
    public void openKeepsTheFirstPublishersTarget_ok() {
        Object owner = new Object();
        JsonObject published = objectWith("a", "1");

        Overflow.publish(owner, Overflow.Target.FIELD_ELEMENT, published);

        // the producer published first, so its target stands and the supplied one is ignored
        assertThat(Overflow.open(owner, Overflow.Target.SOURCE_OBJECT, JsonObject::new), is(sameInstance(published)));
        assertThat(Overflow.find(owner, Overflow.Target.FIELD_ELEMENT), is(sameInstance(published)));
        assertThat(Overflow.find(owner, Overflow.Target.SOURCE_OBJECT), is(nullValue()));
    }

    @Test
    public void claimRemovesTheEntry_ok() {
        Object owner = new Object();
        JsonObject overflow = objectWith("a", "1");

        Overflow.publish(owner, Overflow.Target.FIELD_ELEMENT, overflow);

        // claiming is destructive, which is what stops a write emitting the key twice
        assertThat(Overflow.claim(owner, "a").getAsString(), is("1"));
        assertThat(overflow.has("a"), is(false));
        assertThat(Overflow.claim(owner, "a"), is(nullValue()));
    }

    @Test
    public void claimIgnoresTheTarget_ok() {
        Object owner = new Object();

        Overflow.publish(owner, Overflow.Target.SOURCE_OBJECT, objectWith("a", "1"));

        // a claim reaches either producer's overflow - that is the whole point of one store
        assertThat(Overflow.claim(owner, "a").getAsString(), is("1"));
    }

    @Test
    public void claimOnArrayShapedOverflowReturnsNull_ok() {
        Object owner = new Object();
        JsonArray overflow = new JsonArray();
        overflow.add("a");

        Overflow.publish(owner, Overflow.Target.FIELD_ELEMENT, overflow);

        // a collection-shaped source has no key space, so an exact-key claim stays a silent no-op
        assertThat(Overflow.claim(owner, "a"), is(nullValue()));
        assertThat(overflow.size(), is(1));
    }

    @Test
    public void restorePutsAClaimBack_ok() {
        Object owner = new Object();
        JsonObject overflow = objectWith("a", "1");

        Overflow.publish(owner, Overflow.Target.FIELD_ELEMENT, overflow);
        JsonElement claimed = Overflow.claim(owner, "a");
        Overflow.restore(owner, "a", claimed);

        assertThat(overflow.get("a").getAsString(), is("1"));
    }

    @Test
    public void restoreOnUnknownOwnerIsSilent_ok() {
        Overflow.restore(new Object(), "a", new JsonPrimitive("1"));
    }

    @Test
    public void republishReplacesTheEntry_ok() {
        Object owner = new Object();
        JsonObject second = objectWith("b", "2");

        Overflow.publish(owner, Overflow.Target.FIELD_ELEMENT, objectWith("a", "1"));
        Overflow.publish(owner, Overflow.Target.SOURCE_OBJECT, second);

        assertThat(Overflow.find(owner, Overflow.Target.FIELD_ELEMENT), is(nullValue()));
        assertThat(Overflow.find(owner, Overflow.Target.SOURCE_OBJECT), is(sameInstance(second)));
    }

    @Test
    public void owningIsByIdentityNotEquality_ok() {
        // two owners that compare equal keep separate entries, which is what a per-instance side
        // channel needs and what a plain map keyed by equals would not give
        ConcurrentMap<String, Integer> first = Concurrent.newMap();
        ConcurrentMap<String, Integer> second = Concurrent.newMap();

        assertThat(first, is(second));

        Overflow.publish(first, Overflow.Target.FIELD_ELEMENT, objectWith("a", "1"));

        assertThat(Overflow.find(first, Overflow.Target.FIELD_ELEMENT), is(notNullValue()));
        assertThat(Overflow.find(second, Overflow.Target.FIELD_ELEMENT), is(nullValue()));
    }

    // ──── the two publish policies, which the store deliberately does not unify ────

    @Getter
    public static class LenientHolder {

        @Lenient
        private @NotNull ConcurrentMap<String, Integer> stats = Concurrent.newMap();

    }

    @Getter
    public static class CaptureHolder {

        @Capture
        private @NotNull ConcurrentMap<String, Integer> stats = Concurrent.newMap();

    }

    @Test
    public void lenientPublishesOverflowEvenWhenEmpty_ok() {
        LenientHolder holder = GSON.fromJson("{ \"stats\": { \"health\": 100 } }", LenientHolder.class);

        assertThat(holder.getStats(), hasEntry("health", 100));

        // every entry was compatible, so the overflow is empty - and it is published anyway
        JsonElement overflow = Overflow.find(holder.getStats(), Overflow.Target.FIELD_ELEMENT);

        assertThat(overflow, is(notNullValue()));
        assertThat(overflow.getAsJsonObject().isEmpty(), is(true));
    }

    @Test
    public void captureDoesNotPublishAnEmptyOverflow_ok() {
        CaptureHolder holder = GSON.fromJson("{ \"health\": 100 }", CaptureHolder.class);

        assertThat(holder.getStats(), hasEntry("health", 100));

        // the mirror image of the case above, and the asymmetry is deliberate: unifying either way
        // changes what a consumer sees on a later write
        assertThat(Overflow.find(holder.getStats(), Overflow.Target.SOURCE_OBJECT), is(nullValue()));
    }

    @Test
    public void lenientPublishesUnderFieldElementNotSourceObject_ok() {
        LenientHolder holder = GSON.fromJson("{ \"stats\": { \"health\": 100, \"note\": \"x\" } }", LenientHolder.class);

        assertThat(holder.getStats(), hasEntry("health", 100));
        assertThat(Overflow.find(holder.getStats(), Overflow.Target.SOURCE_OBJECT), is(nullValue()));
        assertThat(Overflow.find(holder.getStats(), Overflow.Target.FIELD_ELEMENT).getAsJsonObject().get("note").getAsString(), is("x"));
    }

    @Test
    public void capturePublishesUnderSourceObjectNotFieldElement_ok() {
        CaptureHolder holder = GSON.fromJson("{ \"health\": 100, \"note\": \"x\" }", CaptureHolder.class);

        assertThat(holder.getStats(), hasEntry("health", 100));
        assertThat(Overflow.find(holder.getStats(), Overflow.Target.FIELD_ELEMENT), is(nullValue()));
        assertThat(Overflow.find(holder.getStats(), Overflow.Target.SOURCE_OBJECT).getAsJsonObject().get("note").getAsString(), is("x"));
    }

    @Test
    public void lenientPublishesEmptyOverflowForAnAbsentField_ok() {
        // the field is missing from the document entirely, so the filter phase never runs for it
        LenientHolder holder = GSON.fromJson("{ }", LenientHolder.class);

        assertThat(holder.getStats(), is(anEmptyMap()));
        assertThat(Overflow.find(holder.getStats(), Overflow.Target.FIELD_ELEMENT), is(nullValue()));
    }

}
