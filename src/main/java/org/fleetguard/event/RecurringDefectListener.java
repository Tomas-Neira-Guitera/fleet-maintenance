package org.fleetguard.event;

import org.fleetguard.service.RecurringDefectService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** CAM-135: después de guardar una inspección con defectos, revisa si algún control se volvió recurrente. */
@Component
public class RecurringDefectListener {

    private final RecurringDefectService recurringDefectService;

    public RecurringDefectListener(RecurringDefectService recurringDefectService) {
        this.recurringDefectService = recurringDefectService;
    }

    @TransactionalEventListener
    public void onDefectsReported(DefectsReportedEvent event) {
        recurringDefectService.alertIfRecurring(event.vehicleId(), event.plate(), event.itemIds());
    }
}
