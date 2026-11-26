package com.estudos.cliente.api.config;

import com.estudos.cliente.api.controller.PaymentNotificationController;
import com.estudos.cliente.api.request.PaymentNotificationReader;
import com.estudos.cliente.api.security.HmacSigner;
import com.estudos.cliente.api.security.SignatureVerifier;
import com.estudos.cliente.api.security.WebhookSecurityProperties;
import com.estudos.cliente.api.simulation.FailureSimulation;
import com.estudos.cliente.api.simulation.FailureSimulationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Componentes da entrada HTTP da loja.
 */
@Configuration(proxyBeanMethods = false)
public class ApiConfig {

    @Bean
    SignatureVerifier signatureVerifier(WebhookSecurityProperties properties, Clock clock) {
        return new SignatureVerifier(new HmacSigner(properties.secret()), properties.tolerance(), clock);
    }

    @Bean
    PaymentNotificationReader paymentNotificationReader(ObjectMapper objectMapper, Validator validator) {
        return new PaymentNotificationReader(objectMapper, validator);
    }

    @Bean
    OpenAPI clienteOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Cliente Webhook - Loja")
                .version("v1")
                .description("Recebe os avisos de pagamento da plataforma e libera os pedidos para envio."));
    }

    /**
     * Simulação de falha, apenas com {@code cliente.simulation.enabled=true}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "cliente.simulation", name = "enabled", havingValue = "true")
    static class SimulationConfig {

        @Bean
        FailureSimulation failureSimulation(@Value("${cliente.simulation.slow-delay:10s}") Duration slowDelay) {
            return new FailureSimulation(slowDelay);
        }

        @Bean
        FilterRegistrationBean<FailureSimulationFilter> failureSimulationFilter(FailureSimulation simulation,
                                                                                ObjectMapper objectMapper,
                                                                                Clock clock) {
            var registration = new FilterRegistrationBean<>(new FailureSimulationFilter(simulation, objectMapper, clock));
            registration.addUrlPatterns(PaymentNotificationController.PATH);
            return registration;
        }
    }
}
