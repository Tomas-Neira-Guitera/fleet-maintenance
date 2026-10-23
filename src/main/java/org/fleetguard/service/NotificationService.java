package org.fleetguard.service;

import org.fleetguard.entity.NotificationChannel;
import org.fleetguard.entity.NotificationType;

import java.util.Set;
import java.util.UUID;

/**
 * Lo único que conocen los avisos concretos (CAM-97 a CAM-103, CAM-118): qué pasó y el texto. A quién y
 * por qué canales sale lo decide la configuración (CAM-117). Se llama siempre por NotificationDispatcher.
 */
public interface NotificationService {

    /** Aviso para la persona involucrada (el técnico de la OT, el chofer del viaje). */
    void notify(UUID recipientId, NotificationType type, String title, String message, String link);

    /** Aviso para los destinatarios elegidos en la configuración del tipo (por defecto, los admins). */
    void notifyConfiguredRecipients(NotificationType type, String title, String message, String link);

    /** Igual, pero con los canales de un plan con configuración propia (CAM-118), aunque el tipo esté apagado. */
    void notifyConfiguredRecipients(NotificationType type, String title, String message, String link,
                                    Set<NotificationChannel> channels);
}
