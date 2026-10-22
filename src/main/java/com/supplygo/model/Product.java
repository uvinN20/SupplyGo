package com.supplygo.model;

/** Stock is measured in whole fruit units, not weight or currency. */
public record Product(String id, String name) {
    public Product {
        if (id == null || !id.matches("[A-Z0-9_-]{1,24}") || name == null || name.isBlank()
                || name.length() > 60 || name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Use a product ID of 1–24 uppercase letters/digits/_/- and a name of 1–60 printable characters");
    }
}
