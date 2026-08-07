package dev.simplified.gson.annotation;

import com.google.gson.annotations.SerializedName;
import dev.simplified.gson.factory.ExtractTypeAdapterFactory;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Matcher;

/**
 * Extracts entries a {@link Lenient @Lenient} or {@link Capture @Capture} field could not hold into
 * a typed companion field.
 * <p>
 * The source is named by <b>Java field name</b>, never by {@link SerializedName @SerializedName},
 * and it must be declared on the same class. A source that does not exist, lives on a superclass, or
 * carries neither annotation is skipped - the companion field keeps its initialiser, exactly as it
 * does when the named key is simply absent.
 * <p>
 * The dot in {@link #value()} selects the mode.
 * <ul>
 *     <li><b>Single key</b> - a dotted value such as {@code "kills.last_killed_mob"} claims that one
 *         key. {@link #filter()} must be empty.</li>
 *     <li><b>Remainder</b> - a dotless value such as {@code "questRewards"} claims everything left in
 *         that source's overflow, narrowed by {@link #filter()}. An empty filter is the catch-all,
 *         mirroring {@code @Capture}.</li>
 * </ul>
 * Example:
 * <pre>{@code
 * @Lenient
 * private ConcurrentMap<String, Integer> kills = Concurrent.newMap();
 *
 * @Extract("kills.last_killed_mob")
 * private String lastKilledMob;
 * }</pre>
 * The {@code "last_killed_mob"} entry, which was filtered from the {@code kills} map because its
 * value is a String rather than Integer, is deserialized into {@code lastKilledMob} instead, and is
 * put back inside {@code kills} when the object is serialized.
 * <p>
 * Claiming is destructive, so claims on one source run most-specific first - single key, then
 * filtered remainder, then catch-all remainder - which makes an exact claim and a catch-all coexist
 * regardless of declaration order. <b>Two filtered remainders on one source whose patterns overlap
 * are order-dependent and the order is not specified</b>; keep them disjoint. Two catch-all
 * remainders on one source is rejected outright.
 * <p>
 * Two limits worth knowing. Keys are selected but never stripped, so a claimed entry keeps the
 * spelling the wire gave it. And a source in {@code @Capture} grouping mode never produces an
 * overflow at all, so an {@code @Extract} naming one is a permanent silent no-op.
 *
 * @see Lenient
 * @see Capture
 * @see ExtractTypeAdapterFactory
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Extract {

    /**
     * The source field, and optionally the single JSON key to claim from it.
     * <p>
     * {@code "sourceField.jsonKey"} claims one named key; the key is everything after the
     * <b>first</b> dot, so a key may itself contain dots. A dotless {@code "sourceField"} claims the
     * remainder of that source instead.
     *
     * @return the extraction path
     */
    @NotNull String value();

    /**
     * Regular expression narrowing which keys a remainder claim takes, matched with
     * {@link Matcher#find() find} rather than a full match.
     * <p>
     * Empty is the catch-all. Must be empty when {@link #value()} names a single key, which is
     * rejected at adapter-build time rather than silently ignored.
     *
     * @return the key filter, or an empty string to claim every remaining entry
     */
    @NotNull String filter() default "";

}
