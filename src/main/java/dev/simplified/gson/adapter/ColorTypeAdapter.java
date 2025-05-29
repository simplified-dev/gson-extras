package dev.sbs.api.io.gson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.awt.*;
import java.io.IOException;

public class ColorTypeAdapter extends TypeAdapter<Color> {

    @Override
    public void write(JsonWriter out, Color value) throws IOException {
        int rgba = (value.getRed() << 24) |
            (value.getGreen() << 16) |
            (value.getBlue() << 8) |
            value.getAlpha();

        out.value(rgba);
    }

    @Override
    public Color read(JsonReader in) throws IOException {
        return new Color(in.nextInt(), true);
    }

}
