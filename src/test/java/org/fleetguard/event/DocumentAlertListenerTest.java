package org.fleetguard.event;

import org.fleetguard.service.DocumentAlertService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** CAM-127: al cargar o corregir un documento se revisan los avisos de ese vehículo. */
class DocumentAlertListenerTest {

    @Test
    void revisaElVehiculoDelEvento() {
        DocumentAlertService service = mock(DocumentAlertService.class);
        UUID vehicleId = UUID.randomUUID();

        new DocumentAlertListener(service).onDocumentChanged(new VehicleDocumentChangedEvent(vehicleId));

        verify(service).checkVehicle(vehicleId);
    }
}
