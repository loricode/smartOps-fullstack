package com.smartops.batch.importjob;

import com.smartops.batch.importjob.ImportOutcome.Accepted;
import com.smartops.batch.importjob.ImportOutcome.Rejected;
import com.smartops.domain.account.AccountStatus;
import com.smartops.domain.account.BankAccount;
import com.smartops.domain.transaction.Channel;
import com.smartops.domain.transaction.TransactionPolicy;
import com.smartops.domain.transaction.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OperationValidationProcessorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T15:00:00Z"), ZoneOffset.UTC);
    private static final Map<String, BankAccount> ACCOUNTS = Map.of(
            "ACC0000001", new BankAccount(1L, "ACC0000001", "A", "COP", BigDecimal.TEN, AccountStatus.ACTIVE, 0L),
            "ACC0000002", new BankAccount(2L, "ACC0000002", "B", "COP", BigDecimal.TEN, AccountStatus.ACTIVE, 0L));

    private final AtomicInteger accountLookups = new AtomicInteger();
    private final OperationValidationProcessor processor = new OperationValidationProcessor(
            new TransactionPolicy(new BigDecimal("1000000000"), CLOCK),
            n -> {
                accountLookups.incrementAndGet();
                return Optional.ofNullable(ACCOUNTS.get(n));
            },
            Set.of("ALREADY-0001")::contains, 42L, "batch-user", CLOCK);

    private ImportOutcome process(String raw) {
        return processor.process(new OperationLine(2, raw));
    }

    @Test
    void acceptsValidLineAsValidatedBatchTransaction() {
        ImportOutcome outcome = process("OP-0001,TRANSFER,ACC0000001,ACC0000002,1500.50,COP,2026-11-19,\"Pago, factura 12\"");

        assertThat(outcome).isInstanceOf(Accepted.class);
        var tx = ((Accepted) outcome).transaction();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.VALIDATED);
        assertThat(tx.getChannel()).isEqualTo(Channel.BATCH);
        assertThat(tx.getBatchFileId()).isEqualTo(42L);
        assertThat(tx.getDescription()).isEqualTo("Pago, factura 12");
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
            "OP-0002,TRANSFER,ACC0000001,ACC0000002,abc,COP,2026-11-19           | INVALID_AMOUNT",
            "OP-0003,TRANSFER,ACC0000001,ACC0000002,-5,COP,2026-11-19            | INVALID_AMOUNT",
            "OP-0004,WIRE,ACC0000001,ACC0000002,5,COP,2026-11-19                 | INVALID_TYPE",
            "OP-0005,TRANSFER,ACC0000001,ACC0000002,5,COP,19/11/2026             | INVALID_DATE",
            "OP-0006,TRANSFER,ACC0000001                                         | MALFORMED_RECORD",
            "OP-0007,TRANSFER,ACC0000001,ACC9999999,5,COP,2026-11-19             | ACCOUNT_NOT_FOUND",
            "OP-0008,PAYMENT,ACC0000001,ACC0000002,5,COP,2026-11-19              | INVALID_ACCOUNT_COMBINATION",
            "OP-0009,PAYMENT,ACC0000001,,5,EUR,2026-11-19                        | INVALID_CURRENCY",
            "ALREADY-0001,PAYMENT,ACC0000001,,5,COP,2026-11-19                   | DUPLICATE_REFERENCE"
    })
    void rejectsInvalidLinesWithReasonCode(String raw, String expectedCode) {
        ImportOutcome outcome = process(raw.trim());

        assertThat(outcome).isInstanceOf(Rejected.class);
        assertThat(((Rejected) outcome).reasonCode()).isEqualTo(expectedCode);
        assertThat(((Rejected) outcome).rawLine()).isEqualTo(raw.trim());
    }

    @Test
    void detectsDuplicateWithinSameFile() {
        assertThat(process("OP-0100,PAYMENT,ACC0000001,,5,COP,2026-11-19")).isInstanceOf(Accepted.class);

        ImportOutcome second = process("OP-0100,PAYMENT,ACC0000001,,5,COP,2026-11-19");

        assertThat(second).isInstanceOf(Rejected.class);
        assertThat(((Rejected) second).reasonCode()).isEqualTo("DUPLICATE_REFERENCE");
    }

    @Test
    void cachesAccountLookupsAcrossLines() {
        process("OP-0200,PAYMENT,ACC0000001,,5,COP,2026-11-19");
        process("OP-0201,PAYMENT,ACC0000001,,5,COP,2026-11-19");
        process("OP-0202,PAYMENT,ACC0000001,,5,COP,2026-11-19");

        assertThat(accountLookups).hasValue(1);
    }

    @Test
    void filtersBlankLines() {
        assertThat(process("   ")).isNull();
    }
}
