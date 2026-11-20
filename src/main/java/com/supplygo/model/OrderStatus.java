// SupplyGo code explanation: Defines allowed named states of an order.
package com.supplygo.model;

public enum OrderStatus {
    PENDING, APPROVED, FULFILLED, CANCELLED;

    public boolean reservesStock() { return this == PENDING || this == APPROVED; }
}
