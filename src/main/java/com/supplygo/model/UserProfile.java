package com.supplygo.model;

/** Public account information. Never contains password material. */
public record UserProfile(String username, Role role) { }
