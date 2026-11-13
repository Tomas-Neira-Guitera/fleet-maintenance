package org.fleetguard.dto;

import java.util.List;

/** CAM-157: en una inspección, lo que el chofer vio en la cubierta de una posición (códigos de TireCondition). */
public record TireConditionInput(String position, List<String> conditions) {
}
