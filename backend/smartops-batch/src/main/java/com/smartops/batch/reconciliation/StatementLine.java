package com.smartops.batch.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Movimiento del extracto externo (banco corresponsal, red de pagos, etc.). */
public record StatementLine(int lineNumber, String reference, BigDecimal amount, String currency,
                            LocalDate valueDate) { }
