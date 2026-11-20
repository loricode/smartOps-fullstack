package com.smartops.domain.transaction;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tipos de operación y qué cuentas requiere cada uno.
 * <ul>
 *   <li>TRANSFER: débito en origen y crédito en destino.</li>
 *   <li>PAYMENT: débito en origen (pago de un servicio externo).</li>
 *   <li>COLLECTION: crédito en destino (recaudo recibido de un tercero).</li>
 * </ul>
 */
public enum TransactionType {
    TRANSFER(true, true),
    PAYMENT(true, false),
    COLLECTION(false, true);

    private final boolean requiresSource;
    private final boolean requiresTarget;

    TransactionType(boolean requiresSource, boolean requiresTarget) {
        this.requiresSource = requiresSource;
        this.requiresTarget = requiresTarget;
    }

    public boolean requiresSource() {
        return requiresSource;
    }

    public boolean requiresTarget() {
        return requiresTarget;
    }

    public static Optional<TransactionType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values()).filter(t -> t.name().equals(normalized)).findFirst();
    }
}
