-- Consultas de operación y soporte para SmartOps.

-- 1. Resumen de operaciones por estado y canal
SELECT status, channel, sum(total) AS operaciones, sum(total_amount) AS monto
  FROM smartops.v_transaction_summary
 GROUP BY status, channel
 ORDER BY status, channel;

-- 2. Últimos lotes con su tasa de rechazo y throughput
SELECT e.id, f.file_name, e.status, e.read_count, e.processed_count, e.rejected_count,
       round(100.0 * e.rejected_count / nullif(e.read_count, 0), 2)          AS pct_rechazo,
       round(e.read_count / nullif(e.duration_ms / 1000.0, 0))              AS registros_por_seg
  FROM smartops.batch_execution e
  JOIN smartops.batch_file f ON f.id = e.batch_file_id
 ORDER BY e.id DESC
 LIMIT 10;

-- 3. Motivos de rechazo de un lote (validación + contabilización)
SELECT reason_code, count(*)
  FROM (SELECT reason_code FROM smartops.rejected_record WHERE batch_file_id = :file_id
        UNION ALL
        SELECT split_part(rejection_reason, ':', 1) FROM smartops.financial_transaction
         WHERE batch_file_id = :file_id AND status = 'REJECTED') r
 GROUP BY reason_code
 ORDER BY 2 DESC;

-- 4. Diferencias de una conciliación (lo que requiere gestión)
SELECT reference, result, internal_amount, external_amount, difference
  FROM smartops.reconciliation_detail
 WHERE reconciliation_id = :reconciliation_id AND result <> 'MATCHED'
 ORDER BY result, abs(coalesce(difference, 0)) DESC;

-- 5. Trazabilidad completa de una operación
SELECT occurred_at, action, actor, correlation_id, details
  FROM smartops.audit_log
 WHERE entity_type = 'TRANSACTION' AND entity_id = :transaction_id
 ORDER BY occurred_at;

-- 6. Verificación contable: la suma de saldos solo cambia por pagos (salen) y recaudos (entran)
SELECT (SELECT sum(balance) FROM smartops.bank_account)                                     AS saldo_total_actual,
       200 * 50000000.00
         - coalesce((SELECT sum(amount) FROM smartops.financial_transaction
                      WHERE type_code = 'PAYMENT' AND status IN ('PROCESSED','RECONCILED')), 0)
         + coalesce((SELECT sum(amount) FROM smartops.financial_transaction
                      WHERE type_code = 'COLLECTION' AND status IN ('PROCESSED','RECONCILED')), 0) AS saldo_total_esperado;
