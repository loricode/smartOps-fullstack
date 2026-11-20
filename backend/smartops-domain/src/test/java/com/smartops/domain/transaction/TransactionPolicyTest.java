package com.smartops.domain.transaction;

import com.smartops.domain.account.AccountDirectory;
import com.smartops.domain.account.AccountStatus;
import com.smartops.domain.account.BankAccount;
import com.smartops.domain.shared.ValidationCode;
import com.smartops.domain.shared.Violation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final Clock CLOCK = Clock.fixed(
            ZonedDateTime.of(TODAY.atTime(10, 0), ZoneId.of("America/Bogota")).toInstant(),
            ZoneId.of("America/Bogota"));

    private final TransactionPolicy policy = new TransactionPolicy(new BigDecimal("1000000000"), CLOCK);

    private final Map<String, BankAccount> accounts = Map.of(
            "ACC0000001", account("ACC0000001", "COP", AccountStatus.ACTIVE),
            "ACC0000002", account("ACC0000002", "COP", AccountStatus.ACTIVE),
            "ACC0000003", account("ACC0000003", "USD", AccountStatus.ACTIVE),
            "ACC0000200", account("ACC0000200", "COP", AccountStatus.BLOCKED));
    private final AccountDirectory directory = n -> Optional.ofNullable(accounts.get(n));

    private static BankAccount account(String number, String currency, AccountStatus status) {
        return new BankAccount(1L, number, "Demo", currency, new BigDecimal("1000"), status, 0L);
    }

    private static TransactionDraft draft(TransactionType type, String source, String target, String amount) {
        return new TransactionDraft("REF-0001", type, source, target,
                amount == null ? null : new BigDecimal(amount), "COP", TODAY, null);
    }

    private static List<ValidationCode> codes(List<Violation> violations) {
        return violations.stream().map(Violation::code).toList();
    }

    @Test
    void validTransferHasNoViolations() {
        assertThat(policy.validate(draft(TransactionType.TRANSFER, "ACC0000001", "ACC0000002", "250.50"), directory))
                .isEmpty();
    }

    @Test
    void rejectsNonPositiveAndOverPreciseAmounts() {
        assertThat(codes(policy.validateFormat(draft(TransactionType.PAYMENT, "ACC0000001", null, "0"))))
                .containsExactly(ValidationCode.INVALID_AMOUNT);
        assertThat(codes(policy.validateFormat(draft(TransactionType.PAYMENT, "ACC0000001", null, "10.001"))))
                .containsExactly(ValidationCode.INVALID_AMOUNT);
        assertThat(codes(policy.validateFormat(draft(TransactionType.PAYMENT, "ACC0000001", null, "1000000000.01"))))
                .containsExactly(ValidationCode.AMOUNT_LIMIT_EXCEEDED);
    }

    @Test
    void enforcesAccountsRequiredByEachType() {
        assertThat(codes(policy.validateFormat(draft(TransactionType.TRANSFER, "ACC0000001", null, "10"))))
                .containsExactly(ValidationCode.INVALID_ACCOUNT_COMBINATION);
        assertThat(codes(policy.validateFormat(draft(TransactionType.COLLECTION, "ACC0000001", "ACC0000002", "10"))))
                .containsExactly(ValidationCode.INVALID_ACCOUNT_COMBINATION);
        assertThat(codes(policy.validateFormat(draft(TransactionType.TRANSFER, "ACC0000001", "ACC0000001", "10"))))
                .containsExactly(ValidationCode.SAME_ACCOUNT);
    }

    @Test
    void detectsUnknownBlockedAndForeignCurrencyAccounts() {
        assertThat(codes(policy.validate(draft(TransactionType.TRANSFER, "ACC9999999", "ACC0000002", "10"), directory)))
                .containsExactly(ValidationCode.ACCOUNT_NOT_FOUND);
        assertThat(codes(policy.validate(draft(TransactionType.PAYMENT, "ACC0000200", null, "10"), directory)))
                .containsExactly(ValidationCode.ACCOUNT_BLOCKED);
        assertThat(codes(policy.validate(draft(TransactionType.COLLECTION, null, "ACC0000003", "10"), directory)))
                .containsExactly(ValidationCode.CURRENCY_MISMATCH);
    }

    @Test
    void doesNotQueryAccountsWhenFormatIsInvalid() {
        AccountDirectory failingDirectory = n -> {
            throw new AssertionError("No debería consultar cuentas");
        };
        TransactionDraft invalid = new TransactionDraft("x", null, null, null, null, "EUR", null, null);

        List<ValidationCode> result = codes(policy.validate(invalid, failingDirectory));

        assertThat(result).contains(ValidationCode.MALFORMED_RECORD, ValidationCode.INVALID_TYPE,
                ValidationCode.MISSING_FIELD, ValidationCode.INVALID_CURRENCY);
    }

    @Test
    void rejectsValueDatesOutOfRange() {
        TransactionDraft future = new TransactionDraft("REF-0001", TransactionType.PAYMENT, "ACC0000001", null,
                BigDecimal.TEN, "COP", TODAY.plusDays(2), null);
        assertThat(codes(policy.validateFormat(future))).containsExactly(ValidationCode.VALUE_DATE_OUT_OF_RANGE);
    }
}
