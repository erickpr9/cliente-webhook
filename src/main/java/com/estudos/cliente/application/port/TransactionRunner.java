package com.estudos.cliente.application.port;

import java.util.function.Supplier;

/**
 * Executa um bloco em uma transação, sem a aplicação depender do framework.
 */
public interface TransactionRunner {

    /** Exceções não verificadas desfazem a transação e são propagadas. */
    <T> T inTransaction(Supplier<T> work);
}
