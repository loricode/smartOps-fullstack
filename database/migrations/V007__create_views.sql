-- =====================================================================
-- V007 - Vistas de reporte
-- =====================================================================
CREATE OR REPLACE VIEW smartops.v_transaction_summary AS
SELECT status,
       type_code,
       channel,
       COUNT(*)    AS total,
       SUM(amount) AS total_amount
FROM smartops.financial_transaction
GROUP BY status, type_code, channel;

CREATE OR REPLACE VIEW smartops.v_daily_volume AS
SELECT value_date,
       COUNT(*)                                                         AS total,
       COUNT(*) FILTER (WHERE status = 'REJECTED')                      AS rejected,
       SUM(amount) FILTER (WHERE status IN ('PROCESSED','RECONCILED'))  AS processed_amount
FROM smartops.financial_transaction
GROUP BY value_date;
