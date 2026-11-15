package org.fleetguard.entity;

/** Etapas del aviso de vencimiento de un documento (CAM-127): antes, el día que vence y al vencer. */
public enum DocumentAlertStage {
    DUE_SOON,
    DUE_TODAY,
    EXPIRED
}
