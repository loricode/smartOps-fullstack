-- =====================================================================
-- Datos semilla (solo perfiles dev/demo). Migración repetible de Flyway.
-- 200 cuentas ACC0000001..ACC0000200 con saldo inicial de 50.000.000 COP.
-- La cuenta ACC0000200 queda BLOQUEADA para probar validaciones.
-- =====================================================================
INSERT INTO smartops.bank_account (account_number, holder_name, currency, balance, status)
SELECT 'ACC' || lpad(n::text, 7, '0'),
       'Cliente demo ' || n,
       'COP',
       50000000.00,
       CASE WHEN n = 200 THEN 'BLOCKED' ELSE 'ACTIVE' END
FROM generate_series(1, 200) AS n
ON CONFLICT (account_number) DO NOTHING;
