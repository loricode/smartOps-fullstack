package com.smartops.batch.reconciliation;

import com.smartops.batch.support.CsvLine;
import org.springframework.batch.item.file.LineMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Formato: reference,amount,currency,value_date.
 * Cualquier excepción aquí la envuelve el lector en FlatFileParseException (con línea y número),
 * que el step trata como "skip" y registra vía {@link StatementSkipListener}.
 */
public class StatementLineMapper implements LineMapper<StatementLine> {

    public static final String HEADER = "reference,amount,currency,value_date";

    @Override
    public StatementLine mapLine(String line, int lineNumber) {
        List<String> f = CsvLine.split(line);
        if (f.size() != 4) {
            throw new IllegalArgumentException("Se esperaban 4 columnas y llegaron " + f.size());
        }
        if (f.get(0).isEmpty() || f.get(0).length() > 64) {
            throw new IllegalArgumentException("Referencia vacía o demasiado larga");
        }
        BigDecimal amount = new BigDecimal(f.get(1));
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Monto debe ser positivo: " + f.get(1));
        }
        String currency = f.get(2).toUpperCase();
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Moneda inválida: " + f.get(2));
        }
        return new StatementLine(lineNumber, f.get(0), amount, currency, LocalDate.parse(f.get(3)));
    }
}
