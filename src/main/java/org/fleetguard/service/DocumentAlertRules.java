package org.fleetguard.service;

import org.fleetguard.entity.DocumentAlertStage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Reglas puras de los vencimientos de documentos (CAM-127). El documento vale hasta el día de su
 * vencimiento inclusive: desde el día siguiente está vencido y el vehículo no puede salir.
 */
final class DocumentAlertRules {

    static final String AL_DIA = "al_dia";
    static final String POR_VENCER = "por_vencer";
    static final String VENCIDO = "vencido";
    static final String SIN_VENCIMIENTO = "sin_vencimiento";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DocumentAlertRules() {
    }

    static String status(LocalDate expiresAt, LocalDate today, int leadDays) {
        if (expiresAt == null) {
            return SIN_VENCIMIENTO;
        }
        if (VehicleAvailabilityService.isExpired(expiresAt, today)) {
            return VENCIDO;
        }
        return ChronoUnit.DAYS.between(today, expiresAt) <= leadDays ? POR_VENCER : AL_DIA;
    }

    /** null si todavía no corresponde ningún aviso. */
    static DocumentAlertStage stage(LocalDate expiresAt, LocalDate today, int leadDays) {
        if (expiresAt == null) {
            return null;
        }
        if (VehicleAvailabilityService.isExpired(expiresAt, today)) {
            return DocumentAlertStage.EXPIRED;
        }
        long days = ChronoUnit.DAYS.between(today, expiresAt);
        if (days == 0) {
            return DocumentAlertStage.DUE_TODAY;
        }
        return days <= leadDays ? DocumentAlertStage.DUE_SOON : null;
    }

    static String title(DocumentAlertStage stage) {
        return stage == DocumentAlertStage.EXPIRED ? "Documento vencido" : "Documento por vencer";
    }

    static String message(DocumentAlertStage stage, String documentName, String plate, LocalDate expiresAt, LocalDate today) {
        String subject = documentName + " de " + plate + ": ";
        return switch (stage) {
            case DUE_SOON -> {
                long days = ChronoUnit.DAYS.between(today, expiresAt);
                yield subject + "vence el " + DATE.format(expiresAt) + " (" + (days == 1 ? "falta 1 día" : "faltan " + days + " días") + ").";
            }
            case DUE_TODAY -> subject + "vence hoy (" + DATE.format(expiresAt)
                    + "). Desde mañana el vehículo queda no disponible si no se carga la renovación.";
            case EXPIRED -> subject + "venció el " + DATE.format(expiresAt)
                    + ". El vehículo queda no disponible hasta que se cargue la renovación.";
        };
    }
}
