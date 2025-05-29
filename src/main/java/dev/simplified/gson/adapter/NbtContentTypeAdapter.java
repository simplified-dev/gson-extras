package dev.sbs.api.io.gson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.sbs.api.client.impl.hypixel.response.skyblock.implementation.island.util.NbtContent;
import dev.sbs.api.reflection.Reflection;

import java.io.IOException;

public class NbtContentTypeAdapter extends TypeAdapter<NbtContent> {

    @Override
    public void write(JsonWriter out, NbtContent value) throws IOException {
        out.beginObject()
            .name("type")
            .value(0)
            .name("data")
            .value(value.getRawData())
            .endObject();
    }

    @Override
    public NbtContent read(JsonReader in) throws IOException {
        Reflection<NbtContent> nbtContentReflection = Reflection.of(NbtContent.class);
        NbtContent nbtContent = nbtContentReflection.newInstance();
        String data;

        if (in.peek() == JsonToken.BEGIN_OBJECT) { // Auctions are bad
            in.beginObject();
            data = in.nextString();
            in.endObject();
        } else
            data = in.nextString();

        nbtContentReflection.setValue(String.class, nbtContent, data);
        return nbtContent;
    }

}
