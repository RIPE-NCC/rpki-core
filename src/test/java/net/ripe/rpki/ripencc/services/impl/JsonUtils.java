package net.ripe.rpki.ripencc.services.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;

public final class JsonUtils {

    public static JsonElement readJsonFile(String pathToJsonFile) {
        return new JsonParser().parse(new InputStreamReader(JsonUtils.class.getResourceAsStream(pathToJsonFile)));
    }
}
