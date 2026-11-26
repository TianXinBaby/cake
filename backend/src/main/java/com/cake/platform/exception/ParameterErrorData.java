package com.cake.platform.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Confirmed Phase 3.1 data contract. Empty/unlocatable errors use data=null. */
public record ParameterErrorData(Map<String, List<String>> fieldErrors) {
    public ParameterErrorData { fieldErrors = copyErrors(fieldErrors); }

    static Map<String, List<String>> copyErrors(Map<String, List<String>> errors) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        if (errors != null) {
            errors.forEach((field, messages) -> {
                if (safePath(field) && messages != null) {
                    List<String> values = messages.stream().filter(java.util.Objects::nonNull)
                            .filter(message -> !message.isBlank()).distinct().toList();
                    if (!values.isEmpty()) { copy.put(field, values); }
                }
            });
        }
        return Collections.unmodifiableMap(copy);
    }

    public static boolean safeField(String field) {
        return field != null && field.length() <= 128 && field.matches("[a-z][A-Za-z0-9]*")
                && !java.util.Set.of("constructor", "prototype").contains(field);
    }

    static boolean safePath(String path) {
        if (path == null || path.length() > 512) { return false; }
        for (String part : path.split("\\.", -1)) {
            if (!part.matches("[a-z][A-Za-z0-9]*(\\[[0-9]+\\])?")) { return false; }
            if (!safeField(part.replaceFirst("\\[.*", ""))) { return false; }
        }
        return true;
    }
}
