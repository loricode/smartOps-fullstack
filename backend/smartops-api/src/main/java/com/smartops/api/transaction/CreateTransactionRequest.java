package com.smartops.api.transaction;

import com.smartops.domain.transaction.TransactionDraft;
import com.smartops.domain.transaction.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Contrato de entrada. Aquí solo se validan forma y tipos (Bean Validation);
 * las reglas de negocio viven en el dominio y no dependen del controlador.
 */
@Schema(description = "Solicitud de registro de operación")
public record CreateTransactionRequest(
        @NotNull @Schema(example = "TRANSFER") TransactionType type,
        @Pattern(regexp = "^[A-Z0-9]{4,20}$") @Schema(example = "ACC0000001") String sourceAccount,
        @Pattern(regexp = "^[A-Z0-9]{4,20}$") @Schema(example = "ACC0000002") String targetAccount,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) @Schema(example = "150000.00") BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") @Schema(example = "COP") String currency,
        @Schema(description = "Fecha valor; por defecto hoy", example = "2026-11-19") LocalDate valueDate,
        @Size(max = 255) String description) {

    public TransactionDraft toDraft(String reference, LocalDate today) {
        return new TransactionDraft(reference, type, sourceAccount, targetAccount, amount, currency,
                valueDate == null ? today : valueDate, description);
    }
}
