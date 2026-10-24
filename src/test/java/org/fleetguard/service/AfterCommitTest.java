package org.fleetguard.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** La push y el email salen recién cuando se confirma la transacción del aviso. */
class AfterCommitTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sinTransaccionCorreEnElMomento() {
        List<String> ran = new ArrayList<>();
        AfterCommit.run(() -> ran.add("push"));
        assertEquals(List.of("push"), ran);
    }

    @Test
    void conTransaccionEsperaAlCommitYSiSeDeshaceNoCorre() {
        List<String> ran = new ArrayList<>();
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(() -> ran.add("push"));
        assertEquals(List.of(), ran);

        List<TransactionSynchronization> pending = TransactionSynchronizationManager.getSynchronizations();
        pending.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertEquals(List.of(), ran);

        pending.forEach(TransactionSynchronization::afterCommit);
        assertEquals(List.of("push"), ran);
    }
}
