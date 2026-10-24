package org.fleetguard.service;

import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.User;

import java.util.List;

/** Un canal de entrega (CAM-117). Corre dentro de la transacción del aviso (NotificationDispatcher). */
public interface NotificationSender {

    NotificationChannel channel();

    void send(List<User> recipients, NotificationContent content);
}
