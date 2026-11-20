package com.smartops.application.transaction;

import com.smartops.domain.shared.DomainException;

/** Se reutilizó una clave de idempotencia con un contenido distinto al original. */
public class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(String reference) {
        super("IDEMPOTENCY_CONFLICT",
                "La referencia " + reference + " ya fue usada con un contenido distinto");
    }
}
