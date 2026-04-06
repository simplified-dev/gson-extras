package dev.simplified.gson;

import dev.simplified.gson.factory.CollapseTypeAdapterFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field inside a value class to receive the map entry key when the enclosing
 * collection is annotated with {@link Collapse @Collapse}.
 * <p>
 * During deserialization, the {@link CollapseTypeAdapterFactory} sets this field to
 * the JSON object key that produced the value. During serialization, this field's
 * value is read to reconstruct the JSON object key; the field itself is excluded from
 * the value's serialized JSON.
 * <p>
 * The annotated field must be assignable from the map's key type (typically
 * {@link String}). At most one {@code @Key} field is allowed per class.
 * <p>
 * Example:
 * <pre>{@code
 * public class SlayerBoss {
 *     @Key
 *     private transient String id;
 *     private double xp;
 * }
 * }</pre>
 * Given JSON {@code {"zombie": {"xp": 100}}}, the {@code id} field is set to
 * {@code "zombie"}.
 *
 * @see Collapse
 * @see CollapseTypeAdapterFactory
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Key {
}
