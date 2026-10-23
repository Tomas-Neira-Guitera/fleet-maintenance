package org.fleetguard.service;

import org.fleetguard.entity.NotificationType;

/** Lo que dice un aviso, igual para todos los canales. link: ruta del front, o null. */
public record NotificationContent(NotificationType type, String title, String message, String link) {
}
