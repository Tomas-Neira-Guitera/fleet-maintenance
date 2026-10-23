package org.fleetguard.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CAM-96 -- un aviso se manda en su propia transacción y un error al mandarlo no se propaga. */
class NotificationDispatcherTest {

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus status = mock(TransactionStatus.class);
    private final NotificationDispatcher dispatcher = new NotificationDispatcher(transactionManager);

    @Test
    void mandaElAvisoEnUnaTransaccionNuevaYLaConfirma() {
        when(transactionManager.getTransaction(any())).thenReturn(status);
        AtomicBoolean ran = new AtomicBoolean();

        assertTrue(dispatcher.dispatch("prueba", () -> ran.set(true)));

        assertTrue(ran.get());
        verify(transactionManager).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(transactionManager).commit(status);
    }

    @Test
    void siElAvisoFallaDeshaceSuTransaccionYNoPropagaElError() {
        when(transactionManager.getTransaction(any())).thenReturn(status);

        assertFalse(dispatcher.dispatch("prueba", () -> {
            throw new IllegalStateException("base caída");
        }));

        verify(transactionManager).rollback(status);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void siFallaElCommitTampocoPropagaElError() {
        when(transactionManager.getTransaction(any())).thenReturn(status);
        doThrow(new IllegalStateException("no se pudo confirmar"))
                .when(transactionManager).commit(status);

        assertFalse(dispatcher.dispatch("prueba", () -> { }));
    }
}
