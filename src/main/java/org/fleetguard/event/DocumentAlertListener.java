package org.fleetguard.event;

import org.fleetguard.service.DocumentAlertService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** CAM-127: al cargar o corregir un documento se revisan sus avisos ya, sin esperar a la corrida diaria. */
@Component
public class DocumentAlertListener {

    private final DocumentAlertService alertService;

    public DocumentAlertListener(DocumentAlertService alertService) {
        this.alertService = alertService;
    }

    @TransactionalEventListener
    public void onDocumentChanged(VehicleDocumentChangedEvent event) {
        alertService.checkVehicle(event.vehicleId());
    }
}
