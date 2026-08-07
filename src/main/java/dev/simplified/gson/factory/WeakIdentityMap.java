package dev.simplified.gson.factory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Side-channel store that attaches bookkeeping to one particular object for as long as
 * that object stays reachable, without keeping it reachable.
 * <p>
 * Entries are matched by reference identity rather than by {@link Object#equals}, which is
 * what a per-instance side channel needs and what {@link WeakHashMap} does not
 * give. Two distinct objects that happen to compare equal keep separate entries, and
 * mutating a stored key cannot orphan its own entry by changing the key's hash.
 * <p>
 * Keys are held through a {@link WeakReference}, so an entry disappears once nothing else
 * refers to its key. Cleared keys are swept on the next access.
 * <p>
 * Every operation is safe for concurrent use, though only {@link #put} is atomic.
 *
 * @param <K> the key type, matched by reference identity
 * @param <V> the stored value type
 */
final class WeakIdentityMap<K, V> {

    private final ReferenceQueue<K> cleared = new ReferenceQueue<>();
    private final Map<IdentityKey<K>, V> entries = new ConcurrentHashMap<>();

    /**
     * Returns the value stored against the given key, or {@code null} when it holds none.
     *
     * @param key the object the entry belongs to
     * @return the stored value, or {@code null} if the key holds no entry
     */
    @Nullable V get(@NotNull K key) {
        this.sweep();
        return this.entries.get(new IdentityKey<>(key));
    }

    /**
     * Stores a value against the given key, replacing whatever it held before.
     *
     * @param key the object the entry belongs to
     * @param value the value to store
     */
    void put(@NotNull K key, @NotNull V value) {
        this.sweep();
        this.entries.put(new IdentityKey<>(key, this.cleared), value);
    }

    /**
     * Returns the value stored against the given key, storing and returning a supplied one
     * when it holds none.
     * <p>
     * Concurrent callers may each run the supplier, but only one of the produced values is
     * ever stored and every caller is handed that one.
     *
     * @param key the object the entry belongs to
     * @param supplier produces the value to store when the key holds no entry
     * @return the value the key now holds
     */
    @NotNull V computeIfAbsent(@NotNull K key, @NotNull Supplier<? extends V> supplier) {
        V existing = this.get(key);

        if (existing != null)
            return existing;

        V created = supplier.get();
        V previous = this.entries.putIfAbsent(new IdentityKey<>(key, this.cleared), created);
        return previous != null ? previous : created;
    }

    /**
     * Returns the number of entries whose key is still reachable.
     *
     * @return the live entry count
     */
    int size() {
        this.sweep();
        return this.entries.size();
    }

    /**
     * Drops every entry whose key has been collected.
     * <p>
     * The queue only says that something died, not which entry to drop - a lookup key and
     * the key actually stored may be separate references to the same referent, and once both
     * are cleared neither can find the other. Draining therefore triggers a scan rather than
     * a targeted removal.
     */
    private void sweep() {
        boolean stale = false;

        while (this.cleared.poll() != null)
            stale = true;

        if (stale)
            this.entries.keySet().removeIf(key -> key.get() == null);
    }

    /**
     * Weak map key that matches its referent by reference identity.
     *
     * @param <K> the referent type
     */
    private static final class IdentityKey<K> extends WeakReference<K> {

        private final int identityHash;

        /**
         * Constructs an unregistered key for lookups only - registering a key that is never
         * stored would enqueue a reference no entry corresponds to.
         *
         * @param referent the object this key stands for
         */
        private IdentityKey(@NotNull K referent) {
            this(referent, null);
        }

        /**
         * Constructs a key to be stored, registered so its death is reported.
         *
         * @param referent the object this key stands for
         * @param queue the queue notified once the referent is collected
         */
        private IdentityKey(@NotNull K referent, @Nullable ReferenceQueue<? super K> queue) {
            super(referent, queue);
            this.identityHash = System.identityHashCode(referent);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return this.identityHash;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;

            if (!(other instanceof IdentityKey<?> that))
                return false;

            Object referent = this.get();
            return referent != null && referent == that.get();
        }

    }

}
