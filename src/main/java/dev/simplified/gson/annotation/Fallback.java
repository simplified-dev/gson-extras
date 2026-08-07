package dev.simplified.gson.annotation;

import com.google.gson.annotations.SerializedName;
import dev.simplified.gson.factory.CaseInsensitiveEnumTypeAdapterFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the enum constant an unrecognized value reads as, instead of {@code null}.
 * <p>
 * A name matching no constant is a {@code Map} miss, which returns {@code null} and throws nothing,
 * so the reflective binder writes that {@code null} over the field's initialiser - the
 * sentinel-constant-plus-default idiom does not work without this. Marking a constant makes the miss
 * resolve to it instead. A JSON null still reads as {@code null}, so absence stays distinguishable
 * from an unrecognized value.
 * <p>
 * At most one constant per enum may carry this; a second is rejected when the adapter is built.
 * Serialization is unaffected, and an enum with no marked constant behaves exactly as it did before
 * the marker existed.
 * <p>
 * <b>Never mark a constant the wire can name.</b> The rule is a property of the upstream
 * vocabulary rather than of the Java, so nothing here can check it: if a marked constant is also
 * reachable by name - through {@link Enum#name()}, {@link SerializedName#value()} or any
 * {@link SerializedName#alternate() alternate}, compared case-insensitively - then an unrecognized
 * value and a real one collapse onto the same constant, and in a map key position the unknown entry
 * silently overwrites a correct one. That is strictly worse than the {@code null} it replaces.
 * <p>
 * Marking also masks a modelling defect rather than fixing it. An enum that is missing a constant
 * the wire really sends, or that spells one wrong, should gain or correct that constant first -
 * otherwise the marker turns a visible {@code null} into a confident wrong answer.
 *
 * @see CaseInsensitiveEnumTypeAdapterFactory
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Fallback { }
