package org.fleetguard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Manda avisos sin que un error al mandarlos rompa lo que los originó (CAM-96, ver
 * docs/api/CAM-96-notifications-contract.md §2 y §3). Cada envío corre en su propia transacción:
 * <ul>
 *   <li>desde un listener AFTER_COMMIT, la del pedido ya se confirmó y un save sumado a ella no se
 *       escribiría nunca;</li>
 *   <li>si el envío falla, solo se deshace lo suyo: el pedido no termina en 500 por un aviso, y una
 *       tarea que manda varios no pierde los demás.</li>
 * </ul>
 * Es un TransactionTemplate y no {@code @Transactional(REQUIRES_NEW)} en cada listener porque el error
 * hay que atraparlo afuera de la transacción: adentro, una excepción ya la dejó marcada para rollback
 * y el commit igual tira.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final TransactionTemplate transaction;

    public NotificationDispatcher(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Corre {@code delivery} en una transacción nueva. Devuelve false si falló: el error queda en el
     * log y no se propaga.
     *
     * @param description qué se estaba avisando, para el log
     */
    public boolean dispatch(String description, Runnable delivery) {
        try {
            transaction.executeWithoutResult(status -> delivery.run());
            return true;
        } catch (RuntimeException e) {
            log.error("No se pudo mandar el aviso: {}", description, e);
            return false;
        }
    }
}
