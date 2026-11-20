package com.smartops.batch.importjob;

import com.smartops.batch.importjob.ImportOutcome.Accepted;
import com.smartops.batch.importjob.ImportOutcome.Rejected;
import com.smartops.batch.importjob.OperationLineParser.ParseResult;
import com.smartops.domain.account.AccountDirectory;
import com.smartops.domain.account.BankAccount;
import com.smartops.domain.shared.ValidationCode;
import com.smartops.domain.shared.Violation;
import com.smartops.domain.transaction.Channel;
import com.smartops.domain.transaction.Transaction;
import com.smartops.domain.transaction.TransactionDraft;
import com.smartops.domain.transaction.TransactionPolicy;
import org.springframework.batch.item.ItemProcessor;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Valida cada línea en este orden (el primer fallo determina el motivo):
 * <ol>
 *   <li>Estructura y tipos de dato.</li>
 *   <li>Reglas de formato de negocio ({@link TransactionPolicy#validateFormat}).</li>
 *   <li>Duplicado dentro del mismo archivo.</li>
 *   <li>Duplicado contra operaciones ya registradas (idempotencia).</li>
 *   <li>Existencia, estado y moneda de las cuentas.</li>
 * </ol>
 * Tiene estado (referencias vistas, caché de cuentas), por eso es de ámbito step.
 */
public class OperationValidationProcessor implements ItemProcessor<OperationLine, ImportOutcome> {

    private final TransactionPolicy policy;
    private final AccountDirectory accounts;
    private final Predicate<String> referenceExists;
    private final long batchFileId;
    private final String actor;
    private final Clock clock;

    private final Set<String> seenReferences = new HashSet<>();
    private final Map<String, Optional<BankAccount>> accountCache = new HashMap<>();

    public OperationValidationProcessor(TransactionPolicy policy, AccountDirectory accounts,
                                        Predicate<String> referenceExists, long batchFileId, String actor,
                                        Clock clock) {
        this.policy = policy;
        this.accounts = accounts;
        this.referenceExists = referenceExists;
        this.batchFileId = batchFileId;
        this.actor = actor;
        this.clock = clock;
    }

    @Override
    public ImportOutcome process(OperationLine line) {
        if (line.raw().isBlank()) {
            return null; // líneas vacías se filtran (cuentan como "filtered", no como rechazo)
        }
        ParseResult parsed = OperationLineParser.parse(line.raw());
        if (parsed instanceof ParseResult.Failed failed) {
            return reject(line, failed.reference(), failed.violation());
        }
        TransactionDraft draft = ((ParseResult.Ok) parsed).draft();

        List<Violation> formatViolations = policy.validateFormat(draft);
        if (!formatViolations.isEmpty()) {
            return reject(line, draft.reference(), formatViolations.get(0));
        }
        if (!seenReferences.add(draft.reference())) {
            return reject(line, draft.reference(), Violation.of(ValidationCode.DUPLICATE_REFERENCE,
                    "Referencia repetida dentro del mismo archivo"));
        }
        if (referenceExists.test(draft.reference())) {
            return reject(line, draft.reference(), Violation.of(ValidationCode.DUPLICATE_REFERENCE,
                    "La referencia ya fue registrada previamente"));
        }
        List<Violation> accountViolations = policy.validateAccounts(draft,
                number -> accountCache.computeIfAbsent(number, accounts::findByNumber));
        if (!accountViolations.isEmpty()) {
            return reject(line, draft.reference(), accountViolations.get(0));
        }

        Transaction tx = Transaction.receive(draft, Channel.BATCH, batchFileId, actor, clock.instant());
        tx.markValidated();
        return new Accepted(line.lineNumber(), tx);
    }

    private static Rejected reject(OperationLine line, String reference, Violation violation) {
        return new Rejected(line.lineNumber(), reference, violation.code().name(), violation.message(), line.raw());
    }
}
