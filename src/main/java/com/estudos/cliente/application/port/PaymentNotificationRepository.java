package com.estudos.cliente.application.port;

import com.estudos.cliente.application.dto.ReceivedNotificationView;
import com.estudos.cliente.domain.model.NotificationOutcome;
import com.estudos.cliente.domain.model.PaymentNotification;

import java.util.List;

/**
 * Porta de persistência dos avisos recebidos.
 */
public interface PaymentNotificationRepository {

    /**
     * Grava o aviso se ainda não existir outro com o mesmo id.
     *
     * @return {@code true} se gravou; {@code false} se era uma reentrega
     */
    boolean insertIfAbsent(PaymentNotification notification);

    void recordOutcome(String notificationId, NotificationOutcome outcome, String note);

    List<ReceivedNotificationView> findRecent(int limit);
}
