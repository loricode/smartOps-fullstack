package com.smartops.application.transaction;

import com.smartops.domain.shared.DomainException;

public class TransactionNotFoundException extends DomainException {

    public TransactionNotFoundException(Object id) {
        super("TRANSACTION_NOT_FOUND", "No existe la operación " + id);
    }
}
