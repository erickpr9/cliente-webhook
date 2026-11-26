package com.estudos.cliente;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * API da loja que recebe os avisos de pagamento da plataforma (webhook de saída da plataforma)
 * e libera os pedidos para envio.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ClienteWebhookApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClienteWebhookApplication.class, args);
    }
}
