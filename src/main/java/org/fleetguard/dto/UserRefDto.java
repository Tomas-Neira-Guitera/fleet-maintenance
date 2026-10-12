package org.fleetguard.dto;

/** Referencia a un usuario (id y nombre), p. ej. quién abrió o cerró un viaje -- CAM-87. */
public record UserRefDto(String id, String name) {
}
