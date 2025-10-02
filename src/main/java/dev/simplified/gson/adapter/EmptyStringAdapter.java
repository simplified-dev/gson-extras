package dev.sbs.api.io.gson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.sbs.api.util.StringUtil;

import java.io.IOException;

public class EmptyStringAdapter extends TypeAdapter<String> {

    @Override
    public void write(JsonWriter out, String value) throws IOException {
        out.value(StringUtil.isEmpty(value) ? "" : value);
    }

    @Override
    public String read(JsonReader in) throws IOException {
        String value = in.nextString();
        return StringUtil.isEmpty(value) ? "" : value;
    }

}
