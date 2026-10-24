package org.fleetguard.event;

import org.fleetguard.service.MaintenanceAlertService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** CAM-118: los planes por km se revisan cada vez que sube el km del vehículo, no solo en el chequeo diario. */
@Component
public class MaintenanceAlertListener {

    private final MaintenanceAlertService alertService;

    public MaintenanceAlertListener(MaintenanceAlertService alertService) {
        this.alertService = alertService;
    }

    @TransactionalEventListener
    public void onOdometerChanged(OdometerChangedEvent event) {
        alertService.checkVehicle(event.vehicleId());
    }
}
