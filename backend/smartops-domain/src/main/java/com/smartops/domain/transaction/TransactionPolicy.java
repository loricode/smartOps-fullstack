package com.smartops.domain.transaction;

import com.smartops.domain.account.AccountDirectory;
import com.smartops.domain.account.BankAccount;
import com.smartops.domain.shared.ValidationCode;
import com.smartops.domain.shared.Violation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reglas de validación de operaciones. Es la misma política para el canal API
 * y el canal batch, de modo que una operación nunca se valida distinto según su origen.
 */
public class TransactionPolicy {

    public static final Set<String> SUPPORTED_CURRENCIES = Set.of("COP", "USD");
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{4,64}$");
    private static final int MAX_DAYS_IN_PAST = 90;

    private final BigDecimal maxAmountPerOperation;
    private final Clock clock;

    public TransactionPolicy(BigDecimal maxAmountPerOperation, Clock clock) {
        this.maxAmountPerOperation = maxAmountPerOperation;
        this.clock = clock;
    }

    /** Validación completa: formato y, si el formato es correcto, cuentas. */
    public List<Violation> validate(TransactionDraft draft, AccountDirectory accounts) {
        List<Violation> violations = validateFormat(draft);
        if (violations.isEmpty()) {
            violations.addAll(validateAccounts(draft, accounts));
        }
        return violations;
    }

    /** Reglas que no requieren consultar datos externos. */
    public List<Violation> validateFormat(TransactionDraft d) {
        List<Violation> v = new ArrayList<>();
        if (d.reference() == null) {
            v.add(Violation.of(ValidationCode.MISSING_FIELD, "La referencia es obligatoria"));
        } else if (!REFERENCE_PATTERN.matcher(d.reference()).matches()) {
            v.add(Violation.of(ValidationCode.MALFORMED_RECORD,
                    "La referencia debe tener entre 4 y 64 caracteres alfanuméricos, '-' o '_'"));
        }
        if (d.type() == null) {
            v.add(Violation.of(ValidationCode.INVALID_TYPE, "El tipo de operación es obligatorio"));
        }
        validateAmount(d.amount(), v);
        if (d.currency() == null || !SUPPORTED_CURRENCIES.contains(d.currency())) {
            v.add(Violation.of(ValidationCode.INVALID_CURRENCY,
                    "Moneda no soportada: " + d.currency() + ". Soportadas: " + SUPPORTED_CURRENCIES));
        }
        validateValueDate(d.valueDate(), v);
        if (d.type() != null) {
            validateAccountCombination(d, v);
        }
        return v;
    }

    /** Reglas que dependen del estado de las cuentas. */
    public List<Violation> validateAccounts(TransactionDraft d, AccountDirectory accounts) {
        List<Violation> v = new ArrayList<>();
        checkAccount(d.sourceAccount(), d.currency(), "origen", accounts, v);
        checkAccount(d.targetAccount(), d.currency(), "destino", accounts, v);
        return v;
    }

    private void validateAmount(BigDecimal amount, List<Violation> v) {
        if (amount == null) {
            v.add(Violation.of(ValidationCode.MISSING_FIELD, "El monto es obligatorio"));
        } else if (amount.signum() <= 0) {
            v.add(Violation.of(ValidationCode.INVALID_AMOUNT, "El monto debe ser mayor que cero"));
        } else if (amount.stripTrailingZeros().scale() > 2) {
            v.add(Violation.of(ValidationCode.INVALID_AMOUNT, "El monto admite máximo 2 decimales"));
        } else if (amount.compareTo(maxAmountPerOperation) > 0) {
            v.add(Violation.of(ValidationCode.AMOUNT_LIMIT_EXCEEDED,
                    "El monto supera el límite por operación de " + maxAmountPerOperation.toPlainString()));
        }
    }

    private void validateValueDate(LocalDate valueDate, List<Violation> v) {
        if (valueDate == null) {
            v.add(Violation.of(ValidationCode.MISSING_FIELD, "La fecha valor es obligatoria"));
            return;
        }
        LocalDate today = LocalDate.now(clock);
        if (valueDate.isAfter(today.plusDays(1)) || valueDate.isBefore(today.minusDays(MAX_DAYS_IN_PAST))) {
            v.add(Violation.of(ValidationCode.VALUE_DATE_OUT_OF_RANGE,
                    "La fecha valor " + valueDate + " está fuera del rango permitido"));
        }
    }

    private static void validateAccountCombination(TransactionDraft d, List<Violation> v) {
        TransactionType type = d.type();
        if (type.requiresSource() && d.sourceAccount() == null) {
            v.add(Violation.of(ValidationCode.INVALID_ACCOUNT_COMBINATION, type + " requiere cuenta origen"));
        }
        if (!type.requiresSource() && d.sourceAccount() != null) {
            v.add(Violation.of(ValidationCode.INVALID_ACCOUNT_COMBINATION, type + " no admite cuenta origen"));
        }
        if (type.requiresTarget() && d.targetAccount() == null) {
            v.add(Violation.of(ValidationCode.INVALID_ACCOUNT_COMBINATION, type + " requiere cuenta destino"));
        }
        if (!type.requiresTarget() && d.targetAccount() != null) {
            v.add(Violation.of(ValidationCode.INVALID_ACCOUNT_COMBINATION, type + " no admite cuenta destino"));
        }
        if (d.sourceAccount() != null && d.sourceAccount().equals(d.targetAccount())) {
            v.add(Violation.of(ValidationCode.SAME_ACCOUNT, "La cuenta origen y destino no pueden ser iguales"));
        }
    }

    private static void checkAccount(String number, String currency, String role,
                                     AccountDirectory accounts, List<Violation> v) {
        if (number == null) {
            return;
        }
        Optional<BankAccount> account = accounts.findByNumber(number);
        if (account.isEmpty()) {
            v.add(Violation.of(ValidationCode.ACCOUNT_NOT_FOUND, "La cuenta " + role + " " + number + " no existe"));
        } else if (!account.get().isActive()) {
            v.add(Violation.of(ValidationCode.ACCOUNT_BLOCKED, "La cuenta " + role + " " + number + " está bloqueada"));
        } else if (!account.get().getCurrency().equals(currency)) {
            v.add(Violation.of(ValidationCode.CURRENCY_MISMATCH,
                    "La cuenta " + role + " " + number + " opera en " + account.get().getCurrency()));
        }
    }
}
