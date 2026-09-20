package org.example.entity;

/** Categoría de una línea de gasto de una orden de trabajo -- CAM-63. */
public enum WorkOrderExpenseCategory {
    REPUESTO,
    MANO_DE_OBRA,
    OTRO;

    public static WorkOrderExpenseCategory fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "repuesto" -> REPUESTO;
            case "mano_de_obra" -> MANO_DE_OBRA;
            case "otro" -> OTRO;
            default -> null;
        };
    }

    public String toJson() {
        return switch (this) {
            case REPUESTO -> "repuesto";
            case MANO_DE_OBRA -> "mano_de_obra";
            case OTRO -> "otro";
        };
    }
}
