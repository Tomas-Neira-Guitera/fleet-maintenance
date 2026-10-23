package org.fleetguard.service;

import org.fleetguard.entity.Vehicle;
import org.fleetguard.event.OdometerChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * CAM-90: el kilometraje del vehículo avanza solo con lo que ya cargan el chofer (pre/post-viaje)
 * y el técnico (OT finalizada, mantenimiento hecho), sin tener que usar "Cargar km" todas las
 * semanas. Una lectura actualiza el vehículo solo si es mayor a la actual y no la supera en más
 * de {@code fleetguard.odometer.max-jump-km}: el odómetro no baja (ODOMETER_REGRESSION), así que
 * un error de tipeo (1.500.000 en vez de 150.000) quedaría cargado para siempre. Una lectura que
 * no pasa la regla no es un error: se guarda donde corresponda y el vehículo queda como estaba.
 * Un vehículo dado de baja no se toca, igual que en "Cargar km" (VEHICLE_INACTIVE, CAM-25 §8).
 */
@Component
public class OdometerReadings {

    private static final Logger log = LoggerFactory.getLogger(OdometerReadings.class);

    private final long maxJumpKm;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public OdometerReadings(@Value("${fleetguard.odometer.max-jump-km:5000}") long maxJumpKm,
                            ApplicationEventPublisher eventPublisher) {
        this.maxJumpKm = maxJumpKm;
        this.eventPublisher = eventPublisher;
    }

    /** Para tests que no miran los avisos de vencimiento (CAM-118). */
    public OdometerReadings(long maxJumpKm) {
        this(maxJumpKm, event -> { });
    }

    /** Aplica la lectura al vehículo si corresponde; devuelve si cambió. El vehículo tiene que estar gestionado. */
    public boolean apply(Vehicle vehicle, long readingKm) {
        // Ej.: una OT en proceso que se finaliza después de dar de baja el vehículo.
        if (!vehicle.isActive()) {
            return false;
        }
        long current = vehicle.getOdometerKm();
        if (readingKm <= current) {
            return false;
        }
        if (readingKm - current > maxJumpKm) {
            log.warn("Lectura de {} km para el vehículo {} ignorada: supera en más de {} km al actual ({} km)",
                    readingKm, vehicle.getPlate(), maxJumpKm, current);
            return false;
        }
        vehicle.setOdometerKm(readingKm);
        eventPublisher.publishEvent(new OdometerChangedEvent(vehicle.getId()));
        return true;
    }
}
