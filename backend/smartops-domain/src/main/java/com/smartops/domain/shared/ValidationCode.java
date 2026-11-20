package com.smartops.domain.shared;

/** Códigos de rechazo estables: se persisten y se exponen en reportes. */
public enum ValidationCode {
    MALFORMED_RECORD,
    MISSING_FIELD,
    INVALID_TYPE,
    INVALID_AMOUNT,
    AMOUNT_LIMIT_EXCEEDED,
    INVALID_CURRENCY,
    INVALID_DATE,
    VALUE_DATE_OUT_OF_RANGE,
    INVALID_ACCOUNT_COMBINATION,
    SAME_ACCOUNT,
    ACCOUNT_NOT_FOUND,
    ACCOUNT_BLOCKED,
    CURRENCY_MISMATCH,
    DUPLICATE_REFERENCE,
    INSUFFICIENT_FUNDS
}
