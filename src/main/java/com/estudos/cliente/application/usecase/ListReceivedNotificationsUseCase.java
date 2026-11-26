package com.estudos.cliente.application.usecase;

import com.estudos.cliente.application.dto.ReceivedNotificationView;
import com.estudos.cliente.application.port.PaymentNotificationRepository;

import java.util.List;
import java.util.Objects;

/**
 * Lista os avisos recebidos mais recentes.
 */
public final class ListReceivedNotificationsUseCase {

    public static final int MAX_LIMIT = 500;

    private final PaymentNotificationRepository notifications;

    public ListReceivedNotificationsUseCase(PaymentNotificationRepository notifications) {
        this.notifications = Objects.requireNonNull(notifications);
    }

    public List<ReceivedNotificationView> execute(int limit) {
        return notifications.findRecent(Math.clamp(limit, 1, MAX_LIMIT));
    }
}
