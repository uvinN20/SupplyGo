package com.supplygo.service;

import java.io.IOException;
import java.time.*;
import java.util.*;
import com.supplygo.model.*;
import com.supplygo.storage.BusinessProfileRepository;

public final class BusinessProfileService {
    public record Page(List<BusinessProfile> profiles, int nextOffset) {
        public Page { profiles = List.copyOf(profiles); }
    }
    private final BusinessProfileRepository repository;
    private final Clock clock;
    public BusinessProfileService(BusinessProfileRepository repository) { this(repository, Clock.systemUTC()); }
    public BusinessProfileService(BusinessProfileRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }
    public BusinessProfile get(UserProfile actor) throws AuthException {
        owner(actor);
        BusinessProfile profile = repository.snapshot().get(actor.username());
        if (profile == null) throw new AuthException("PROFILE_NOT_FOUND", "No business profile yet. Complete your details and save.");
        return profile;
    }
    public BusinessProfile update(UserProfile actor, String name, String business, String phone, String location) throws AuthException {
        owner(actor);
        synchronized (repository) {
            BusinessProfile old = repository.snapshot().get(actor.username());
            Instant now = clock.instant();
            if (old != null && now.isBefore(Instant.parse(old.updatedAt()))) now = Instant.parse(old.updatedAt());
            final BusinessProfile next;
            try {
                next = new BusinessProfile(actor.username(), actor.role(), name, business, phone, location,
                        old == null ? now.toString() : old.createdAt(), now.toString());
            } catch (IllegalArgumentException exception) { throw new AuthException("INVALID_PROFILE", exception.getMessage()); }
            try { repository.save(next); }
            catch (IOException exception) { throw new AuthException("STORAGE_ERROR", "Business profile could not be saved. Try again."); }
            return next;
        }
    }
    public Page list(UserProfile actor, Role directory, int offset) throws AuthException {
        if (actor == null) throw new AuthException("AUTH_REQUIRED", "Sign in first");
        boolean allowed = directory == Role.SUPPLIER && (actor.role() == Role.ADMIN || actor.role() == Role.SUPPLIER_MANAGER)
                || directory == Role.SELLER && (actor.role() == Role.ADMIN || actor.role() == Role.SELLER_MANAGER);
        if (!allowed) throw new AuthException("FORBIDDEN", "Your role cannot access this business directory");
        if (offset < 0 || offset > 10000) throw new AuthException("INVALID_PROFILE", "Offset must be between 0 and 10000");
        var rows = repository.snapshot().values().stream().filter(p -> p.role() == directory)
                .sorted(Comparator.comparing(BusinessProfile::username)).toList();
        int start = Math.min(offset, rows.size()), end = Math.min(start + 10, rows.size());
        return new Page(rows.subList(start, end), end < rows.size() ? end : -1);
    }
    private static void owner(UserProfile actor) throws AuthException {
        if (actor == null) throw new AuthException("AUTH_REQUIRED", "Sign in first");
        if (actor.role() != Role.SUPPLIER && actor.role() != Role.SELLER)
            throw new AuthException("FORBIDDEN", "Only suppliers and sellers have editable business profiles");
    }
}
