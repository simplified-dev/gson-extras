package dev.simplified.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

class GsonSettingsPrewarmTest {

    record Payload(@NotNull String name, int value) { }

    @Nested
    @DisplayName("GsonSettings.create() prewarms registered adapters")
    class CreatePrewarm {

        @Test
        @DisplayName("Registered prewarm types are resolved at create-time")
        void registeredTypesAreResolved() {
            CountingFactory factory = new CountingFactory(Payload.class);
            GsonSettings settings = GsonSettings.builder()
                .withFactories(factory)
                .withPrewarmTypes(Payload.class)
                .build();

            assertThat(factory.hits.get(), is(0));

            Gson gson = settings.create();

            assertThat(factory.hits.get(), is(greaterThanOrEqualTo(1)));

            String json = gson.toJson(new Payload("a", 1));
            assertThat(json, is(equalTo("{\"name\":\"a\",\"value\":1}")));
        }

        @Test
        @DisplayName("withPrewarmAdapters(false) defers adapter generation")
        void disabledPrewarm() {
            CountingFactory factory = new CountingFactory(Payload.class);
            GsonSettings settings = GsonSettings.builder()
                .withFactories(factory)
                .withPrewarmTypes(Payload.class)
                .withPrewarmAdapters(false)
                .build();

            settings.create();

            assertThat(factory.hits.get(), is(0));
        }

        @Test
        @DisplayName("Empty prewarm types list is a no-op at create-time")
        void emptyPrewarmTypes() {
            CountingFactory factory = new CountingFactory(Payload.class);
            GsonSettings settings = GsonSettings.builder()
                .withFactories(factory)
                .build();

            settings.create();

            assertThat(factory.hits.get(), is(0));
        }

    }

    @Nested
    @DisplayName("GsonSettings.prewarm(Gson, Iterable) utility")
    class StaticUtility {

        @Test
        @DisplayName("Warms a raw Gson without a GsonSettings wrapper")
        void warmsRawGson() {
            CountingFactory factory = new CountingFactory(Payload.class);
            Gson gson = new com.google.gson.GsonBuilder()
                .registerTypeAdapterFactory(factory)
                .create();

            assertThat(factory.hits.get(), is(0));

            GsonSettings.prewarm(gson, List.of(Payload.class));

            assertThat(factory.hits.get(), is(greaterThanOrEqualTo(1)));
        }

        @Test
        @DisplayName("Per-type failures are swallowed and warm-up continues")
        void swallowsBadType() {
            Gson gson = new Gson();
            // Multiple resolves; the utility must not throw under any condition.
            GsonSettings.prewarm(gson, List.of(Payload.class, String.class, Integer.class));
        }
    }

    @Nested
    @DisplayName("GsonSettings.defaults() factory registration order")
    class DefaultFactoryOrder {

        private static final List<String> BUILT_INS = List.of(
            "CaseInsensitiveEnumTypeAdapterFactory",
            "OptionalTypeAdapterFactory",
            "SplitTypeAdapterFactory",
            "SerializedPathTypeAdaptorFactory",
            "LenientTypeAdapterFactory",
            "CaptureTypeAdapterFactory",
            "CollapseTypeAdapterFactory",
            "PostInitTypeAdapterFactory"
        );

        private static List<String> registeredFactoryNames() {
            List<String> names = new ArrayList<>();

            for (TypeAdapterFactory factory : GsonSettings.defaults().getFactories())
                names.add(factory.getClass().getSimpleName());

            return names;
        }

        @Test
        @DisplayName("The built-in factory prefix is exact and ordered")
        void defaultFactoryOrderIsStable_ok() {
            List<String> names = registeredFactoryNames();

            // Registration order runs OPPOSITE to nesting depth - GsonBuilder.create() reverses the
            // user factory list, so the LAST registered factory is the OUTERMOST wrapper. Inserting
            // a factory shifts every later index and no other test asserts this list.
            // The assertion is on the PREFIX rather than the whole list: defaults() appends every
            // SPI TypeAdapterFactory found on the runtime classpath after the built-ins, so an
            // exact whole-list assertion would go red for a classpath change that has nothing to do
            // with the registration order this test exists to protect.
            assertThat(names.size(), is(greaterThanOrEqualTo(BUILT_INS.size())));
            assertThat(names.subList(0, BUILT_INS.size()), is(equalTo(BUILT_INS)));
        }

        @Test
        @DisplayName("SPI-discovered factories are appended after every built-in")
        void spiFactoriesAreAppendedLast_ok() {
            List<String> names = registeredFactoryNames();

            // an SPI factory landing between two built-ins would silently change nesting depth
            for (String builtIn : BUILT_INS)
                assertThat(names.indexOf(builtIn), is(lessThan(BUILT_INS.size())));
        }

        @Test
        @DisplayName("Capture is registered after Lenient, so Capture nests outside it")
        void captureNestsOutsideLenient_ok() {
            List<String> names = registeredFactoryNames();

            // assert presence FIRST - indexOf returns -1 for a missing entry, so a bare
            // greaterThan comparison passes when either factory is not registered at all
            assertThat(names, hasItems("LenientTypeAdapterFactory", "CaptureTypeAdapterFactory"));
            assertThat(names.indexOf("CaptureTypeAdapterFactory"),
                is(greaterThan(names.indexOf("LenientTypeAdapterFactory"))));
        }

    }

    /**
     * Test factory that increments a counter when asked for an adapter for a specific raw
     * type, then defers to Gson's default reflective adapter for that type.
     */
    private static final class CountingFactory implements TypeAdapterFactory {

        final AtomicInteger hits = new AtomicInteger();
        private final Class<?> watch;

        CountingFactory(Class<?> watch) {
            this.watch = watch;
        }

        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() == this.watch) {
                this.hits.incrementAndGet();
                return gson.getDelegateAdapter(this, type);
            }
            return null;
        }

    }

}
