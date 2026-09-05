package org.example.service;

import org.example.entity.MaintenanceStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Lógica de negocio pura (sin dependencias de Spring/JPA) para calcular el estado de un
 * plan, el health score de un vehículo, y cuál plan mostrar como "próximo mantenimiento".
 * Umbral fijo global -- ver CAM-40-modelo-mantenimiento-preventivo.md secciones 3, 5 y 6.
 */
public final class MaintenanceStatusCalculator {

    public static final long WARNING_THRESHOLD_KM = 1000;
    public static final long WARNING_THRESHOLD_DAYS = 7;

    private static final int OVERDUE_PENALTY = 25;
    private static final int DUE_SOON_PENALTY = 8;

    private MaintenanceStatusCalculator() {
    }

    /** estado(plan) -- sección 3. */
    public static MaintenanceStatus computeStatus(Long nextDueKm, LocalDate nextDueDate, long currentKm, LocalDate today) {
        boolean overdueByKm = nextDueKm != null && currentKm > nextDueKm;
        boolean overdueByDate = nextDueDate != null && today.isAfter(nextDueDate);
        if (overdueByKm || overdueByDate) {
            return MaintenanceStatus.VENCIDO;
        }

        boolean dueSoonByKm = nextDueKm != null && (nextDueKm - currentKm) <= WARNING_THRESHOLD_KM;
        boolean dueSoonByDate = nextDueDate != null && ChronoUnit.DAYS.between(today, nextDueDate) <= WARNING_THRESHOLD_DAYS;
        if (dueSoonByKm || dueSoonByDate) {
            return MaintenanceStatus.POR_VENCER;
        }

        return MaintenanceStatus.AL_DIA;
    }

    /**
     * Porcentaje del intervalo consumido -- sección 5, usado únicamente como desempate
     * entre planes con el mismo estado a la hora de elegir cuál mostrar como
     * "próximo mantenimiento". No se usa para decidir el estado en sí (eso es umbral fijo).
     */
    public static double urgencyPercent(Long lastDoneKm, Long nextDueKm, LocalDate lastDoneDate, LocalDate nextDueDate,
                                         long currentKm, LocalDate today) {
        double pctKm = 0;
        if (lastDoneKm != null && nextDueKm != null && nextDueKm > lastDoneKm) {
            pctKm = (currentKm - lastDoneKm) / (double) (nextDueKm - lastDoneKm);
        }
        double pctTime = 0;
        if (lastDoneDate != null && nextDueDate != null && nextDueDate.isAfter(lastDoneDate)) {
            long totalDays = ChronoUnit.DAYS.between(lastDoneDate, nextDueDate);
            long elapsedDays = ChronoUnit.DAYS.between(lastDoneDate, today);
            pctTime = elapsedDays / (double) totalDays;
        }
        return Math.max(pctKm, pctTime);
    }

    /** healthScore(vehiculo) -- sección 6. */
    public static int healthScore(Iterable<MaintenanceStatus> activeAssignmentStatuses) {
        int penalty = 0;
        for (MaintenanceStatus status : activeAssignmentStatuses) {
            if (status == MaintenanceStatus.VENCIDO) {
                penalty += OVERDUE_PENALTY;
            } else if (status == MaintenanceStatus.POR_VENCER) {
                penalty += DUE_SOON_PENALTY;
            }
        }
        return Math.max(0, Math.min(100, 100 - penalty));
    }
}
