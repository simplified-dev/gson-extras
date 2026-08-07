package dev.simplified.gson.annotation;

import com.google.gson.annotations.SerializedName;
import dev.simplified.gson.factory.FlattenTypeAdapterFactory;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.Map;

/**
 * Collapses a single-key wrapper object out of every entry of a {@link Map Map} or
 * {@link Collection Collection} field, so the field can be declared as the value it actually wants.
 * <p>
 * Example:
 * <pre>{@code
 * @Flatten("current")
 * private ConcurrentMap<String, Integer> essence = Concurrent.newMap();
 * }</pre>
 * Wire shape {@code {"WITHER": {"current": 1955}}} binds {@code {WITHER: 1955}} and serializes back
 * wrapped, removing the nested generic and the accessor that used to unwrap it by hand.
 * <p>
 * <b>This is a projection, not a lossless transform.</b> A wrapper carrying a sibling member reads
 * fine and is <b>lost on serialize</b>: {@code {"current": 1955, "total": 3000}} binds {@code 1955}
 * and writes back as {@code {"current": 1955}} alone. That is a declared contract rather than a
 * defect - the field's declared type has no room for the sibling - but it makes this the one
 * annotation here that does not preserve round-trip fidelity, so do not reach for it where a wrapper
 * carries more than the one member.
 * <p>
 * Two more behaviours worth knowing. An entry that is already collapsed is left alone on read, so a
 * half-wrapped document normalises to fully wrapped on write. And an entry whose wrapper is missing
 * the named member is left alone rather than failing - it reaches the delegate as the wrapper object
 * and fails there if the declared type cannot take it.
 * <p>
 * The field is addressed by {@link SerializedName @SerializedName} when it carries one, otherwise by
 * its Java field name. Combining this with {@code @Capture}, {@link Lenient @Lenient} or
 * {@link SerializedPath @SerializedPath}, or putting it on a field that is neither a map nor a
 * collection, is rejected when the adapter is built - so there is no in-annotation escape hatch for
 * a missing wrapper.
 *
 * @see FlattenTypeAdapterFactory
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Flatten {

    /**
     * Name of the wrapper member holding the value the field wants.
     *
     * @return the wrapper member name
     */
    @NotNull String value();

}
