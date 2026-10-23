package dev.simplified.gson;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import dev.simplified.annotations.RequiredArgsConstructor;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.gson.ConcurrentTypeAdapterFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

public class GsonContributorTest {

    // ──── Builder#apply(GsonContributor) ────

    @Nested
    class BuilderApply {

        @Test
        public void apply_inlineLambda_registersTypeAdapter() {
            Gson gson = GsonSettings.builder()
                .apply(builder -> builder.withTypeAdapter(Marker.class, new PrefixSerializer("inline")))
                .build()
                .create();

            assertThat(gson.toJson(new Marker("hello")), is("\"inline:hello\""));
        }

    }

    // ──── defaults() ServiceLoader discovery ────

    @Nested
    class ServiceLoaderDiscovery {

        @Test
        public void defaults_discoversContributorViaServiceFile() {
            Gson gson = GsonSettings.defaults().create();

            // TestMarkerContributor is registered via
            // src/test/resources/META-INF/services/dev.simplified.gson.GsonContributor
            assertThat(gson.toJson(new TestMarker("ping")), is("\"test-marker:ping\""));
        }

        @Test
        public void defaults_appliesHigherPriorityLast_soItWins() {
            Gson gson = GsonSettings.defaults().create();

            // LowPriorityContributor (priority 0) runs before HighPriorityContributor (priority 100).
            // Both register an adapter for PriorityMarker; the later registration overwrites the
            // earlier in the typeAdapters map, so "high" wins.
            assertThat(gson.toJson(new PriorityMarker("val")), is("\"high:val\""));
        }

    }

    // ──── defaults() discovery under a foreign context class loader ────

    @Nested
    class ForeignContextClassLoader {

        @Test
        public void defaults_discoversProvidersTheContextLoaderCannotSee() throws IOException {
            try (URLClassLoader blind = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())) {
                Gson gson = withContextClassLoader(blind, () -> GsonSettings.defaults().create());

                // A plugin host leaves the context loader on its own loader, which sees none of the
                // service files shaded beside this library.
                assertThat(gson.toJson(new TestMarker("ping")), is("\"test-marker:ping\""));
                ConcurrentList<Integer> list = gson.fromJson("[1, 2]", new TypeToken<ConcurrentList<Integer>>() {}.getType());
                assertThat(list, contains(1, 2));
            }
        }

        @Test
        public void defaults_registersEachProviderOnce_whenBothLoadersSeeIt() throws IOException {
            try (URLClassLoader child = new URLClassLoader(new URL[0], GsonSettings.class.getClassLoader())) {
                GsonSettings settings = withContextClassLoader(child, GsonSettings::defaults);

                long concurrent = settings.getFactories()
                    .stream()
                    .filter(ConcurrentTypeAdapterFactory.class::isInstance)
                    .count();
                assertThat(concurrent, is(1L));
            }
        }

    }

    private static <T> T withContextClassLoader(@NotNull ClassLoader loader, @NotNull Supplier<T> action) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);

        try {
            return action.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    // ──── fixtures ────

    interface ValueCarrier {
        @NotNull String value();
    }

    public record Marker(@Override @NotNull String value) implements ValueCarrier {}

    public record TestMarker(@Override @NotNull String value) implements ValueCarrier {}

    public record PriorityMarker(@Override @NotNull String value) implements ValueCarrier {}

    @RequiredArgsConstructor
    static class PrefixSerializer implements JsonSerializer<ValueCarrier> {

        private final @NotNull String prefix;

        @Override
        public JsonElement serialize(@NotNull ValueCarrier src, @NotNull Type typeOfSrc, @NotNull JsonSerializationContext context) {
            return new JsonPrimitive(this.prefix + ":" + src.value());
        }

    }

    public static final class TestMarkerContributor implements GsonContributor {

        @Override
        public void contribute(GsonSettings.@NotNull Builder builder) {
            builder.withTypeAdapter(TestMarker.class, new PrefixSerializer("test-marker"));
        }

    }

    public static final class LowPriorityContributor implements GsonContributor {

        @Override
        public void contribute(GsonSettings.@NotNull Builder builder) {
            builder.withTypeAdapter(PriorityMarker.class, new PrefixSerializer("low"));
        }

        @Override
        public int priority() {
            return 0;
        }

    }

    public static final class HighPriorityContributor implements GsonContributor {

        @Override
        public void contribute(GsonSettings.@NotNull Builder builder) {
            builder.withTypeAdapter(PriorityMarker.class, new PrefixSerializer("high"));
        }

        @Override
        public int priority() {
            return 100;
        }

    }

}
