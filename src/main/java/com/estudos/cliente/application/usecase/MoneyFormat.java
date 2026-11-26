package com.estudos.cliente.application.usecase;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/**
 * Formata valores em centavos para leitura humana nos logs (ex.: {@code R$ 159,90}).
 */
final class MoneyFormat {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private MoneyFormat() {
    }

    static String format(long amountInCents, String currency) {
        try {
            NumberFormat format = NumberFormat.getCurrencyInstance(PT_BR);
            format.setCurrency(Currency.getInstance(currency));
            // O formatador usa espaço não separável; troca por espaço comum para o console.
            return format.format(BigDecimal.valueOf(amountInCents, 2)).replace(' ', ' ');
        } catch (IllegalArgumentException unknownCurrency) {
            return amountInCents + " centavos " + currency;
        }
    }
}
