package net.ripe.rpki.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class Sanitize {

    public static String forLogging(String data) {
        return data.replaceAll("[\\r\\n\\t]", "_");
    }
}
