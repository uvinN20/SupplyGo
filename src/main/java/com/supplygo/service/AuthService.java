package com.supplygo.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import com.supplygo.model.Role;
import com.supplygo.model.UserProfile;
import com.supplygo.security.PasswordHasher;
import com.supplygo.storage.AccountRepository;

/** Account validation, password checks and server-wide login throttling. */
public final class AuthService {
    private final AccountRepository repository;
    private final PasswordHasher hasher = new PasswordHasher();
    private final PasswordHasher.Hash dummy = hasher.hash("dummy-password-for-unknown-user".toCharArray());
    private final Semaphore hashingSlots = new Semaphore(4);
    private final Map<String, Failure> failures = new LinkedHashMap<>();
    private final Clock clock;
    private record Failure(int count, Instant until) { }

    public AuthService(AccountRepository repository) { this(repository, Clock.systemUTC()); }
    public AuthService(AccountRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    public UserProfile register(String username, char[] password, Role role) throws AuthException {
        if (!role.allowsSelfRegistration()) throw new AuthException("FORBIDDEN", "Only suppliers and sellers may self-register");
        return provision(username, password, role);
    }

    /** Used only by the offline operator command; never exposed as a protocol operation. */
    public UserProfile provision(String username, char[] password, Role role) throws AuthException {
        String canonical = canonical(username);
        if (password.length < 12 || password.length > 128) {
            throw new AuthException("INVALID_ACCOUNT", "Use a password between 12 and 128 characters");
        }
        acquire();
        try {
            UserProfile profile = new UserProfile(canonical, role);
            if (!repository.create(new AccountRepository.Account(profile, hasher.hash(password)))) {
                throw new AuthException("USERNAME_TAKEN", "That username is unavailable");
            }
            return profile;
        } catch (IOException exception) {
            throw new AuthException("STORAGE_ERROR", "Account could not be saved; contact the server operator");
        } finally { hashingSlots.release(); }
    }

    public UserProfile login(String username, char[] password) throws AuthException {
        String canonical;
        try { canonical = canonical(username); }
        catch (AuthException exception) { throw invalidCredentials(); }
        if (password.length < 1 || password.length > 128) throw invalidCredentials();
        checkThrottle(canonical);
        acquire();
        try {
            var account = repository.find(canonical);
            boolean matches = hasher.verify(password, account == null ? dummy : account.passwordHash());
            synchronized (failures) {
                checkThrottle(canonical);
                if (account == null || !matches) {
                    Failure previous = failures.get(canonical);
                    int count = previous == null || !clock.instant().isBefore(previous.until()) ? 1 : previous.count() + 1;
                    if (failures.size() >= 1024 && !failures.containsKey(canonical)) {
                        failures.remove(failures.keySet().iterator().next());
                    }
                    failures.put(canonical, new Failure(count, clock.instant().plusSeconds(60)));
                    throw invalidCredentials();
                }
                failures.remove(canonical);
            }
            return account.profile();
        } finally { hashingSlots.release(); }
    }

    public int userCount() { return repository.count(); }

    private void checkThrottle(String username) throws AuthException {
        synchronized (failures) {
            Failure failure = failures.get(username);
            if (failure != null && failure.count() >= 5 && clock.instant().isBefore(failure.until())) {
                throw new AuthException("RATE_LIMITED", "Too many failed attempts; try again in one minute");
            }
        }
    }

    private void acquire() throws AuthException {
        if (!hashingSlots.tryAcquire()) throw new AuthException("SERVER_BUSY", "Server is busy; try again shortly");
    }

    private static String canonical(String username) throws AuthException {
        String value = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z][a-z0-9_]{2,31}")) {
            throw new AuthException("INVALID_ACCOUNT", "Username: 3-32 letters, digits or underscores, starting with a letter");
        }
        return value;
    }

    private static AuthException invalidCredentials() { return new AuthException("INVALID_CREDENTIALS", "Invalid username or password"); }
}
