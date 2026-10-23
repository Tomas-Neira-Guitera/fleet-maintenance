package org.fleetguard.service;

import org.fleetguard.entity.MaintenanceAlertStage;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reglas puras de los avisos de vencimiento (CAM-118). "Vencido" es el mismo criterio del dashboard
 * (MaintenanceStatusCalculator); "por vencer" usa la anticipación del aviso, no el umbral del dashboard.
 */
final class MaintenanceAlertRules {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale AR = Locale.forLanguageTag("es-AR");

    private MaintenanceAlertRules() {
    }

    /** null si todavía no corresponde ningún aviso. */
    static MaintenanceAlertStage stage(Long nextDueKm, LocalDate nextDueDate, long currentKm, LocalDate today,
                                       Integer leadKm, Integer leadDays) {
        if (overdueByKm(nextDueKm, currentKm) || overdueByDate(nextDueDate, today)) {
            return MaintenanceAlertStage.OVERDUE;
        }
        boolean soonByKm = nextDueKm != null && leadKm != null && nextDueKm - currentKm <= leadKm;
        boolean soonByDate = nextDueDate != null && leadDays != null && ChronoUnit.DAYS.between(today, nextDueDate) <= leadDays;
        return soonByKm || soonByDate ? MaintenanceAlertStage.DUE_SOON : null;
    }

    /** Identifica el ciclo actual: cambia cuando se registra el mantenimiento y se recalcula el próximo. */
    static String cycleKey(Long nextDueKm, LocalDate nextDueDate) {
        return "d:" + (nextDueDate == null ? "-" : nextDueDate) + "|k:" + (nextDueKm == null ? "-" : nextDueKm);
    }

    static String message(MaintenanceAlertStage stage, String planName, String plate, Long nextDueKm,
                          LocalDate nextDueDate, long currentKm, LocalDate today) {
        List<String> parts = new ArrayList<>();
        if (stage == MaintenanceAlertStage.OVERDUE) {
            if (overdueByDate(nextDueDate, today)) {
                long days = ChronoUnit.DAYS.between(nextDueDate, today);
                parts.add("venció el " + DATE.format(nextDueDate) + " (hace " + plural(days, "día", "días") + ")");
            }
            if (overdueByKm(nextDueKm, currentKm)) {
                parts.add("se pasó por " + km(currentKm - nextDueKm) + " (vencía a los " + km(nextDueKm) + ")");
            }
        } else {
            if (nextDueDate != null) {
                long days = ChronoUnit.DAYS.between(today, nextDueDate);
                parts.add(days == 0 ? "vence hoy"
                        : (days == 1 ? "falta 1 día" : "faltan " + days + " días") + " (vence el " + DATE.format(nextDueDate) + ")");
            }
            if (nextDueKm != null) {
                parts.add((nextDueKm - currentKm == 0 ? "vence con el km actual" : "faltan " + km(nextDueKm - currentKm))
                        + " (vence a los " + km(nextDueKm) + ")");
            }
        }
        return planName + " de " + plate + ": " + String.join(" y ", parts) + ".";
    }

    private static boolean overdueByKm(Long nextDueKm, long currentKm) {
        return nextDueKm != null && currentKm > nextDueKm;
    }

    private static boolean overdueByDate(LocalDate nextDueDate, LocalDate today) {
        return nextDueDate != null && today.isAfter(nextDueDate);
    }

    private static String km(long value) {
        return NumberFormat.getIntegerInstance(AR).format(value) + " km";
    }

    private static String plural(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }
}
