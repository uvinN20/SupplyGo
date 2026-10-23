package com.supplygo.protocol;

import java.util.Set;
import com.google.gson.*;
import com.supplygo.model.*;

public final class BusinessProfileData {
    private BusinessProfileData() { }
    public static JsonObject encode(BusinessProfile profile) {
        JsonObject row = new JsonObject();
        row.addProperty("username", profile.username()); row.addProperty("role", profile.role().name());
        row.addProperty("fullName", profile.fullName()); row.addProperty("businessName", profile.businessName());
        row.addProperty("contactNumber", profile.contactNumber()); row.addProperty("location", profile.location());
        row.addProperty("createdAt", profile.createdAt()); row.addProperty("updatedAt", profile.updatedAt());
        return row;
    }
    public static BusinessProfile decode(JsonElement element) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException("Invalid business profile");
        JsonObject row = element.getAsJsonObject();
        if (!row.keySet().equals(Set.of("username", "role", "fullName", "businessName", "contactNumber", "location", "createdAt", "updatedAt")))
            throw new IllegalArgumentException("Invalid business profile fields");
        return new BusinessProfile(string(row, "username"), Role.valueOf(string(row, "role")),
                string(row, "fullName"), string(row, "businessName"), string(row, "contactNumber"),
                string(row, "location"), string(row, "createdAt"), string(row, "updatedAt"));
    }
    private static String string(JsonObject row, String key) {
        if (!(row.get(key) instanceof JsonPrimitive value) || !value.isString() || value.getAsString().length() > 128)
            throw new IllegalArgumentException("Invalid profile string");
        return value.getAsString();
    }
}
