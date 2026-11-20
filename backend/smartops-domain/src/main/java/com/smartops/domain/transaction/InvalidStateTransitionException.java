package com.smartops.domain.transaction;

import com.smartops.domain.shared.DomainException;

public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(String reference, TransactionStatus from, TransactionStatus to) {
        super("INVALID_STATE_TRANSITION",
                "La operación %s no puede pasar de %s a %s".formatted(reference, from, to));
    }
}
