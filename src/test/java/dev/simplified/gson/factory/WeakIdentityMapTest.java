package dev.simplified.gson.factory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class WeakIdentityMapTest {

    @Test
    public void equalKeysKeepSeparateEntries_ok() {
        WeakIdentityMap<List<String>, String> map = new WeakIdentityMap<>();
        List<String> first = new ArrayList<>(List.of("a"));
        List<String> second = new ArrayList<>(List.of("a"));

        assertThat(first, is(second));

        map.put(first, "first");
        map.put(second, "second");

        assertThat(map.size(), is(2));
        assertThat(map.get(first), is("first"));
        assertThat(map.get(second), is("second"));
    }

    @Test
    public void mutatedKeyKeepsItsEntry_ok() {
        WeakIdentityMap<List<String>, String> map = new WeakIdentityMap<>();
        List<String> key = new ArrayList<>(List.of("a"));

        map.put(key, "stored");
        key.add("b");

        assertThat(map.get(key), is("stored"));
    }

    @Test
    public void absentKeyReadsNull_ok() {
        WeakIdentityMap<List<String>, String> map = new WeakIdentityMap<>();

        assertThat(map.get(new ArrayList<>(List.of("a"))), is(nullValue()));
    }

    @Test
    public void computeIfAbsentStoresOnceThenReuses_ok() {
        WeakIdentityMap<List<String>, List<String>> map = new WeakIdentityMap<>();
        List<String> key = new ArrayList<>(List.of("a"));

        List<String> created = map.computeIfAbsent(key, ArrayList::new);
        created.add("overflow");

        assertThat(map.computeIfAbsent(key, ArrayList::new), sameInstance(created));
        assertThat(map.get(key), contains("overflow"));
        assertThat(map.size(), is(1));
    }

    @Test
    public void unreachableKeyIsEvicted_ok() throws InterruptedException {
        WeakIdentityMap<List<String>, String> map = new WeakIdentityMap<>();
        List<String> key = new ArrayList<>(List.of("a"));

        map.put(key, "stored");
        assertThat(map.size(), is(1));

        key = null;

        for (int attempt = 0; attempt < 50 && map.size() > 0; attempt++) {
            System.gc();
            Thread.sleep(20);
        }

        assertThat(map.size(), is(0));
    }

}
