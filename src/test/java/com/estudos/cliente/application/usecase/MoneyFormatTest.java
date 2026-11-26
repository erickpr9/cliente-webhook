package com.estudos.cliente.application.usecase;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyFormatTest {

    @Test
    void formatsBrazilianReais() {
        assertThat(MoneyFormat.format(15990, "BRL")).isEqualTo("R$ 159,90");
        assertThat(MoneyFormat.format(129900, "BRL")).isEqualTo("R$ 1.299,00");
    }

    @Test
    void fallsBackForUnknownCurrency() {
        assertThat(MoneyFormat.format(100, "XYZ")).isEqualTo("100 centavos XYZ");
    }
}
