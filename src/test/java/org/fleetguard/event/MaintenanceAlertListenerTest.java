package org.fleetguard.event;

import org.fleetguard.service.MaintenanceAlertService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** CAM-118: al subir el km se revisan los planes de ese vehículo. */
class MaintenanceAlertListenerTest {

    @Test
    void revisaElVehiculoDelEvento() {
        MaintenanceAlertService service = mock(MaintenanceAlertService.class);
        UUID vehicleId = UUID.randomUUID();

        new MaintenanceAlertListener(service).onOdometerChanged(new OdometerChangedEvent(vehicleId));

        verify(service).checkVehicle(vehicleId);
    }
}
