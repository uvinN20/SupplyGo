package com.supplygo.model;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;

/** Business details only; account identity always comes from the server session. */
public record BusinessProfile(String username, Role role, String fullName, String businessName,
        String contactNumber, String location, String createdAt, String updatedAt) {
    public BusinessProfile {
        if (username == null || !username.matches("[a-z][a-z0-9_]{2,31}")
                || (role != Role.SUPPLIER && role != Role.SELLER))
            throw new IllegalArgumentException("Invalid profile identity");
        validateDetails(fullName, businessName, contactNumber, location);
        if (Instant.parse(updatedAt).isBefore(Instant.parse(createdAt)))
            throw new IllegalArgumentException("Invalid profile timestamps");
    }
    public static void validateDetails(String name, String business, String phone, String location) {
        text(name, 64, "Full name"); text(business, 96, "Business name");
        text(phone, 32, "Contact number"); text(location, 64, "Location");
        if (!phone.matches("\\+?[0-9][0-9 ()-]{5,30}[0-9]"))
            throw new IllegalArgumentException("Contact number needs 7–32 characters: digits, spaces, parentheses or hyphens; optional leading +");
        Gson json = new Gson();
        int bytes = 0;
        for (String field : new String[]{name, business, phone, location})
            bytes += json.toJson(field).getBytes(StandardCharsets.UTF_8).length;
        if (bytes > 400) throw new IllegalArgumentException("Combined profile details exceed the 400-byte encoded limit; shorten the text");
    }
    private static void text(String value, int maximum, String label) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.length() > maximum
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c >= 0xD800 && c <= 0xDFFF))
            throw new IllegalArgumentException(label + " must be trimmed, printable text of 1–" + maximum + " characters");
    }
}
