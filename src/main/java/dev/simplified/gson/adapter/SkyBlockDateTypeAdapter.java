package dev.sbs.api.io.gson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.sbs.api.client.impl.hypixel.response.skyblock.implementation.SkyBlockDate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;

@NoArgsConstructor(access = AccessLevel.NONE)
public class SkyBlockDateTypeAdapter {

    public static class RealTime extends TypeAdapter<SkyBlockDate.RealTime> {

        @Override
        public void write(JsonWriter out, SkyBlockDate.RealTime value) throws IOException {
            out.value(value.getRealTime());
        }

        @Override
        public SkyBlockDate.RealTime read(JsonReader in) throws IOException {
            return new SkyBlockDate.RealTime(in.nextLong());
        }

    }

    public static class SkyBlockTime extends TypeAdapter<SkyBlockDate.SkyBlockTime> {

        @Override
        public void write(JsonWriter out, SkyBlockDate.SkyBlockTime value) throws IOException {
            out.value(value.getRealTime());
        }

        @Override
        public SkyBlockDate.SkyBlockTime read(JsonReader in) throws IOException {
            return new SkyBlockDate.SkyBlockTime(in.nextLong());
        }

    }

}
