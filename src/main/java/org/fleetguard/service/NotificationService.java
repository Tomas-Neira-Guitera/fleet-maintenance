package org.fleetguard.service;

import org.fleetguard.entity.NotificationType;

import java.util.UUID;

/**
 * Deja un aviso in-app para un usuario (CAM-96). Es lo único que conocen los avisos concretos
 * (CAM-97 a CAM-103): deciden cuándo y a quién avisar, y no saben cómo se guarda ni cómo se muestra.
 *
 * Hoy la implementa {@link LoggingNotificationService}, que solo escribe en el log, porque todavía no
 * existe la tabla de avisos. La implementación real la guarda en la base y la reemplaza; ver
 * docs/api/CAM-96-notifications-contract.md.
 *
 * Quien avisa no llama a esto directo sino a través de {@link NotificationDispatcher}, que le da a
 * cada aviso su propia transacción y hace que un error al guardarlo no rompa lo que lo originó. La
 * implementación puede llevar {@code @Transactional} común y tirar excepciones normalmente.
 */
public interface NotificationService {

    /**
     * @param recipientId usuario que recibe el aviso
     * @param title       una línea, por ejemplo "Te asignaron una orden de trabajo"
     * @param message     el detalle, ya armado
     * @param link        ruta del front a la que lleva tocar el aviso, o null si no lleva a ningún lado
     */
    void notify(UUID recipientId, NotificationType type, String title, String message, String link);

    /** Un aviso igual para cada usuario ADMIN activo (CAM-97, CAM-98). */
    void notifyAllAdmins(NotificationType type, String title, String message, String link);
}
