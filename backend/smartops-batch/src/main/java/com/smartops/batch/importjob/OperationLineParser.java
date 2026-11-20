package com.smartops.batch.importjob;

import com.smartops.batch.support.CsvLine;
import com.smartops.domain.shared.ValidationCode;
import com.smartops.domain.shared.Violation;
import com.smartops.domain.transaction.TransactionDraft;
import com.smartops.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Convierte una línea CSV en {@link TransactionDraft}.
 * Formato: reference,type,source_account,target_account,amount,currency,value_date[,description]
 */
public final class OperationLineParser {

    public static final String HEADER = "reference,type,source_account,target_account,amount,currency,value_date,description";

    public sealed interface ParseResult {
        record Ok(TransactionDraft draft) implements ParseResult { }

        record Failed(String reference, Violation violation) implements ParseResult { }
    }

    private OperationLineParser() {
    }

    public static ParseResult parse(String raw) {
        List<String> f;
        try {
            f = CsvLine.split(raw);
        } catch (IllegalArgumentException e) {
            return failed(null, ValidationCode.MALFORMED_RECORD, e.getMessage());
        }
        if (f.size() < 7 || f.size() > 8) {
            return failed(f.isEmpty() ? null : f.get(0), ValidationCode.MALFORMED_RECORD,
                    "Se esperaban 7 u 8 columnas y llegaron " + f.size());
        }
        String reference = f.get(0);
        TransactionType type = TransactionType.parse(f.get(1)).orElse(null);
        if (type == null) {
            return failed(reference, ValidationCode.INVALID_TYPE, "Tipo de operación desconocido: '" + f.get(1) + "'");
        }
        BigDecimal amount;
        try {
            amount = f.get(4).isEmpty() ? null : new BigDecimal(f.get(4));
        } catch (NumberFormatException e) {
            return failed(reference, ValidationCode.INVALID_AMOUNT, "Monto no numérico: '" + f.get(4) + "'");
        }
        LocalDate valueDate;
        try {
            valueDate = f.get(6).isEmpty() ? null : LocalDate.parse(f.get(6));
        } catch (DateTimeParseException e) {
            return failed(reference, ValidationCode.INVALID_DATE, "Fecha inválida (yyyy-MM-dd): '" + f.get(6) + "'");
        }
        return new ParseResult.Ok(new TransactionDraft(reference, type, f.get(2), f.get(3), amount, f.get(5),
                valueDate, f.size() == 8 ? f.get(7) : null));
    }

    private static ParseResult failed(String reference, ValidationCode code, String message) {
        String ref = reference == null || reference.isBlank() ? null : reference;
        return new ParseResult.Failed(ref, Violation.of(code, message));
    }
}
