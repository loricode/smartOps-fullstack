package com.smartops.batch.support;

import com.smartops.batch.reconciliation.StatementLineMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvLineTest {

    @Test
    void splitsQuotedFieldsWithCommasAndEscapedQuotes() {
        assertThat(CsvLine.split("a, \"b,c\" ,\"say \"\"hi\"\"\",,"))
                .containsExactly("a", "b,c", "say \"hi\"", "", "");
    }

    @Test
    void failsOnUnclosedQuotes() {
        assertThatThrownBy(() -> CsvLine.split("a,\"b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void headerValidatorAcceptsBomAndCase() {
        new HeaderValidator(StatementLineMapper.HEADER).handleLine("﻿Reference,Amount,Currency,Value_Date");
        assertThatThrownBy(() -> new HeaderValidator(StatementLineMapper.HEADER).handleLine("ref,amt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cabecera inválida");
    }

    @Test
    void statementMapperParsesAndValidates() {
        StatementLineMapper mapper = new StatementLineMapper();
        var line = mapper.mapLine("OP-1,100.50,cop,2026-11-19", 5);
        assertThat(line.lineNumber()).isEqualTo(5);
        assertThat(line.currency()).isEqualTo("COP");
        assertThat(line.amount()).isEqualByComparingTo("100.50");

        assertThatThrownBy(() -> mapper.mapLine("OP-1,-1,COP,2026-11-19", 6))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.mapLine("OP-1,1,COP", 7))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
