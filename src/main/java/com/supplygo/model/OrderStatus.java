package com.supplygo.model;

public enum OrderStatus {
    PENDING, APPROVED, FULFILLED, CANCELLED;

    public boolean reservesStock() { return this == PENDING || this == APPROVED; }
}
