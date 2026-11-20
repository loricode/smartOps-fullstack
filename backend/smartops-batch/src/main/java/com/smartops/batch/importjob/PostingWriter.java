package com.smartops.batch.importjob;

import com.smartops.application.port.out.TransactionRepository;
import com.smartops.application.transaction.TransactionPostingService;
import com.smartops.domain.transaction.Transaction;
import com.smartops.domain.transaction.TransactionStatus;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.UUID;

/**
 * Contabiliza operaciones VALIDATED reutilizando exactamente el mismo servicio que el canal API.
 * Corre dentro de la transacción del chunk: si falla, se revierte el chunk completo.
 */
public class PostingWriter implements ItemWriter<UUID> {

    private final TransactionRepository transactions;
    private final TransactionPostingService posting;
    private final String actor;

    public PostingWriter(TransactionRepository transactions, TransactionPostingService posting, String actor) {
        this.transactions = transactions;
        this.posting = posting;
        this.actor = actor;
    }

    @Override
    public void write(Chunk<? extends UUID> ids) {
        for (UUID id : ids) {
            Transaction tx = transactions.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Operación desaparecida: " + id));
            if (tx.getStatus() == TransactionStatus.VALIDATED) { // protección ante reinicios del job
                posting.post(tx, actor);
            }
        }
    }
}
