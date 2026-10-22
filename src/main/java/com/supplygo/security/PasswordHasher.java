package com.supplygo.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** JDK-only PBKDF2-HMAC-SHA256, with independent 128-bit salts and 256-bit hashes. */
public final class PasswordHasher {
    public static final int ITERATIONS = 600_000;
    private final SecureRandom random = new SecureRandom();

    public record Hash(String salt, String value, int iterations) {
        public Hash {
            if (iterations != ITERATIONS || Base64.getDecoder().decode(salt).length != 16
                    || Base64.getDecoder().decode(value).length != 32) {
                throw new IllegalArgumentException("Invalid password hash record");
            }
        }
        @Override public String toString() { return "PasswordHash[redacted]"; }
    }

    public Hash hash(char[] password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] value = derive(password, salt);
        try {
            return new Hash(Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(value), ITERATIONS);
        } finally {
            Arrays.fill(value, (byte) 0);
        }
    }

    public boolean verify(char[] password, Hash hash) {
        byte[] actual = derive(password, Base64.getDecoder().decode(hash.salt()));
        byte[] expected = Base64.getDecoder().decode(hash.value());
        try {
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
            Arrays.fill(expected, (byte) 0);
        }
    }

    private byte[] derive(char[] password, byte[] salt) {
        PBEKeySpec specification = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2-HMAC-SHA256 is unavailable", exception);
        } finally {
            specification.clearPassword();
        }
    }
}
