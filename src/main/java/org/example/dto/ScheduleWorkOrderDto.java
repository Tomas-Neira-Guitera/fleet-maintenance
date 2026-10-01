package org.example.dto;

/** Resumen de la OT abierta de una programación, para el detalle del calendario. */
public record ScheduleWorkOrderDto(
        String id,
        String status,
        String responsible
) {
}
