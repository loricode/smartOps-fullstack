package com.smartops.batch.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Separador CSV mínimo (RFC 4180): admite campos entre comillas con comas
 * y comillas escapadas (""). Lanza {@link IllegalArgumentException} si las comillas no cierran.
 */
public final class CsvLine {

    private CsvLine() {
    }

    public static List<String> split(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Comillas sin cerrar");
        }
        fields.add(current.toString().trim());
        return fields;
    }
}
