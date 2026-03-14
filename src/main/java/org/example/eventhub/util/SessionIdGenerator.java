package org.example.eventhub.util;

import java.security.SecureRandom;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class SessionIdGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSid() {
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }
}