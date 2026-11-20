package com.smartops.batch.support;

import org.springframework.batch.item.file.LineCallbackHandler;

/** Verifica la cabecera del archivo; si no coincide, el job falla antes de leer datos. */
public class HeaderValidator implements LineCallbackHandler {

    private final String expected;

    public HeaderValidator(String expected) {
        this.expected = expected;
    }

    @Override
    public void handleLine(String line) {
        String normalized = line.replace("﻿", "").trim().toLowerCase();
        if (!normalized.equals(expected)) {
            throw new IllegalStateException("Cabecera inválida. Esperada: '" + expected + "', recibida: '" + line + "'");
        }
    }
}
