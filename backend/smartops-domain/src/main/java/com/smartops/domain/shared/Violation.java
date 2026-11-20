package com.smartops.domain.shared;

import java.util.Objects;

/** Incumplimiento de una regla de negocio. Es un dato, no una excepción. */
public record Violation(ValidationCode code, String message) {

    public Violation {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public static Violation of(ValidationCode code, String message) {
        return new Violation(code, message);
    }

    /** Representación compacta usada como motivo de rechazo persistido. */
    public String asReason() {
        return code + ": " + message;
    }
}
