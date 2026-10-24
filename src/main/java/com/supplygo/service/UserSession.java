package com.supplygo.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import com.supplygo.model.Role;
import com.supplygo.model.UserProfile;

/** One session per connection. Identity is assigned exclusively by the authentication service. */
public final class UserSession {
    private final Clock clock;
    private UserProfile profile;
    private Instant expires;

    public UserSession() { this(Clock.systemUTC()); }
    public UserSession(Clock clock) { this.clock = clock; }
    public void login(UserProfile profile) { this.profile = profile; expires = clock.instant().plus(Duration.ofMinutes(15)); }
    public void logout() { profile = null; expires = null; }
    public UserProfile requireUser() throws AuthException {
        if (profile == null) throw new AuthException("AUTH_REQUIRED", "Sign in first");
        if (!clock.instant().isBefore(expires)) {
            logout();
            throw new AuthException("SESSION_EXPIRED", "Session expired; sign in again");
        }
        return profile;
    }
    public Instant expiresAt() throws AuthException { requireUser(); return expires; }
    public UserProfile requireRole(Role role) throws AuthException {
        UserProfile user = requireUser();
        if (user.role() != role) throw new AuthException("FORBIDDEN", "This operation requires the " + role + " role");
        return user;
    }
}
