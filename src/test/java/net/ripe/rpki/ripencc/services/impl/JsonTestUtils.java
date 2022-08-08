package net.ripe.rpki.ripencc.services.impl;

import com.google.common.io.Resources;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.SneakyThrows;

import java.nio.charset.StandardCharsets;

public final class JsonTestUtils {

    @SneakyThrows
    public static JsonElement readJsonFile(String pathToJsonFile) {
        return JsonParser.parseString(Resources.toString(JsonTestUtils.class.getResource(pathToJsonFile), StandardCharsets.UTF_8));
    }
}
