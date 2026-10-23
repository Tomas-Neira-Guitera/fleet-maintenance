package org.fleetguard.service;

import org.fleetguard.entity.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * TEMPORAL (CAM-96): escribe en el log el aviso que se mandaría, porque todavía no existe la tabla de
 * avisos. Se borra cuando esté la implementación que los guarda en la base; nada más tiene que cambiar.
 */
@Service
public class LoggingNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationService.class);

    @Override
    public void notify(UUID recipientId, NotificationType type, String title, String message, String link) {
        log.info("Aviso {} para el usuario {}: {} -- {} (link: {})", type.toJson(), recipientId, title, message, link);
    }

    @Override
    public void notifyAllAdmins(NotificationType type, String title, String message, String link) {
        log.info("Aviso {} para todos los admins: {} -- {} (link: {})", type.toJson(), title, message, link);
    }
}
