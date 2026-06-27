package com.example.newnow.util;

public final class EventTypeUtil {

    private EventTypeUtil() {}

    /** concert / Concert / Koncert → jedinstveno "Concert" */
    public static String normalize(String type) {
        if (type == null || type.isBlank()) return type;
        String trimmed = type.trim();
        if (trimmed.equalsIgnoreCase("concert") || trimmed.equalsIgnoreCase("koncert")) {
            return "Concert";
        }
        return trimmed;
    }
}
