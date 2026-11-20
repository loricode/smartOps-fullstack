package com.smartops.domain.transaction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Datos de entrada de una operación, antes de convertirse en {@link Transaction}.
 * Es el contrato común entre el canal API y el canal batch.
 */
public record TransactionDraft(
        String reference,
        TransactionType type,
        String sourceAccount,
        String targetAccount,
        BigDecimal amount,
        String currency,
        LocalDate valueDate,
        String description) {

    public TransactionDraft {
        reference = trimToNull(reference);
        sourceAccount = trimToNull(sourceAccount);
        targetAccount = trimToNull(targetAccount);
        currency = currency == null ? null : currency.trim().toUpperCase();
        description = trimToNull(description);
    }

    /**
     * Huella SHA-256 del contenido económico de la operación. Permite distinguir
     * un reintento legítimo (misma referencia, mismo contenido) de un conflicto
     * (misma referencia, contenido distinto).
     */
    public String payloadHash() {
        String canonical = String.join("|",
                String.valueOf(reference),
                String.valueOf(type),
                String.valueOf(sourceAccount),
                String.valueOf(targetAccount),
                amount == null ? "null" : amount.stripTrailingZeros().toPlainString(),
                String.valueOf(currency),
                String.valueOf(valueDate));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
