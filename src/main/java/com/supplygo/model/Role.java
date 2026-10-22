package com.supplygo.model;

public enum Role {
    SUPPLIER, SELLER, SUPPLIER_MANAGER, SELLER_MANAGER, PRODUCTION_MANAGER, ADMIN;

    public boolean allowsSelfRegistration() {
        return this == SUPPLIER || this == SELLER;
    }
}
