package com.supplygo.security;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;

/** Uses the JVM keystore/truststore settings; certificate validation is never bypassed. */
public final class TlsSupport {
    private TlsSupport() { }
    public static SSLContext defaultContext() throws IOException {
        try { return SSLContext.getDefault(); }
        catch (NoSuchAlgorithmException exception) { throw new IOException("Cannot initialize TLS; check keystore settings", exception); }
    }
}
