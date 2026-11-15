package org.fleetguard.exception;

public class ExpenseNotFoundException extends RuntimeException {
    public ExpenseNotFoundException(String expenseId) {
        super("No existe un gasto operativo con id " + expenseId);
    }
}
