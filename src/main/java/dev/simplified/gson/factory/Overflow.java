package dev.simplified.gson.factory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.simplified.annotations.UtilityClass;
import dev.simplified.collection.Concurrent;
import dev.simplified.gson.annotation.Capture;
import dev.simplified.gson.annotation.Extract;
import dev.simplified.gson.annotation.Lenient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Side channel holding the JSON entries a bound container could not take, so a later write of that
 * same container can put them back where they came from.
 * <p>
 * Both {@link Lenient @Lenient} and {@link Capture @Capture} fill it, and {@link Extract @Extract}
 * reads from either without knowing which. Entries are keyed by the identity of the container the
 * bind produced, so a caller that mutates that container afterwards still finds its overflow, and an
 * entry disappears once its container does.
 * <p>
 * Each entry carries its own write target, because the two producers merge back into different
 * places - {@code @Lenient} into the owning field's own JSON element, {@code @Capture} into the
 * object the entries were classified out of - and a claim has to be returnable to the one it came
 * from. That is why this is a tagged store rather than a union of the two it replaces.
 *
 * @see WeakIdentityMap
 */
@UtilityClass
final class Overflow {

    private static final WeakIdentityMap<Object, Entry> ENTRIES = new WeakIdentityMap<>();

    /**
     * Where a write puts an overflow back.
     */
    enum Target {

        /**
         * The owning field's own JSON element, located by serialized name or serialized path.
         */
        FIELD_ELEMENT,
        /**
         * The object the entries were classified out of - the enclosing object, or the nested node
         * a descending capture reads.
         */
        SOURCE_OBJECT

    }

    /**
     * One container's overflow together with the target that produced it.
     *
     * @param target where a write merges this overflow back
     * @param element the entries themselves - a JSON object for a map-shaped owner, a JSON array for
     *     a collection-shaped one
     */
    record Entry(@NotNull Target target, @NotNull JsonElement element) { }

    /**
     * Stores the overflow belonging to a bound container, replacing whatever it held before.
     *
     * @param owner the container the bind produced
     * @param target where a write merges this overflow back
     * @param element the overflowed entries
     */
    static void publish(@NotNull Object owner, @NotNull Target target, @NotNull JsonElement element) {
        ENTRIES.put(owner, new Entry(target, element));
    }

    /**
     * Returns the overflow a container holds for the given target, or {@code null} when it holds
     * none or holds one another producer put there.
     *
     * @param owner the container to look up
     * @param target the target the caller merges into
     * @return the overflowed entries, or {@code null} if the container holds none for that target
     */
    static @Nullable JsonElement find(@NotNull Object owner, @NotNull Target target) {
        Entry entry = ENTRIES.get(owner);
        return entry != null && entry.target() == target ? entry.element() : null;
    }

    /**
     * Returns the overflow a container holds, storing and returning a supplied empty one when it
     * holds none.
     * <p>
     * The first publisher decides the target - a container already holding an overflow keeps it, and
     * the supplied target is ignored rather than overwriting one a producer is relying on.
     *
     * @param owner the container to look up
     * @param target the target to record if nothing is stored yet
     * @param ifAbsent produces the empty container to store when nothing is stored yet
     * @return the overflowed entries the container now holds
     */
    static @NotNull JsonElement open(@NotNull Object owner, @NotNull Target target, @NotNull Supplier<JsonElement> ifAbsent) {
        return ENTRIES.computeIfAbsent(owner, () -> new Entry(target, ifAbsent.get())).element();
    }

    /**
     * Removes and returns the overflowed entry stored under one key.
     * <p>
     * Claiming is destructive, which is what stops a write emitting the key twice - once from the
     * claiming field's own re-injection and once from the producer's merge-back.
     *
     * @param owner the container to claim from
     * @param key the JSON key to claim
     * @return the claimed element, or {@code null} if the container holds no object-shaped overflow
     *     or no entry under that key
     */
    static @Nullable JsonElement claim(@NotNull Object owner, @NotNull String key) {
        Entry entry = ENTRIES.get(owner);

        if (entry == null || !entry.element().isJsonObject())
            return null;

        return entry.element().getAsJsonObject().remove(key);
    }

    /**
     * Removes and returns every overflowed entry whose key the given filter accepts.
     *
     * @param owner the container to claim from
     * @param filter decides which keys to claim
     * @return the claimed entries under their original keys, empty when nothing matched
     */
    static @NotNull JsonObject claim(@NotNull Object owner, @NotNull Predicate<String> filter) {
        JsonObject claimed = new JsonObject();
        Entry entry = ENTRIES.get(owner);

        if (entry == null || !entry.element().isJsonObject())
            return claimed;

        JsonObject overflow = entry.element().getAsJsonObject();

        // snapshot the keys before removing - iterating entrySet while removing from the same
        // JsonObject is a ConcurrentModificationException
        for (String key : Concurrent.newList(overflow.keySet())) {
            if (filter.test(key))
                claimed.add(key, overflow.remove(key));
        }

        return claimed;
    }

    /**
     * Puts a claimed entry back, for a claim that could not be converted into its target field.
     *
     * @param owner the container to restore into
     * @param key the JSON key the entry was claimed under
     * @param element the claimed element
     */
    static void restore(@NotNull Object owner, @NotNull String key, @NotNull JsonElement element) {
        Entry entry = ENTRIES.get(owner);

        if (entry != null && entry.element().isJsonObject())
            entry.element().getAsJsonObject().add(key, element);
    }

    /**
     * Returns the number of containers still holding an overflow.
     *
     * @return the live entry count
     */
    static int size() {
        return ENTRIES.size();
    }

}
