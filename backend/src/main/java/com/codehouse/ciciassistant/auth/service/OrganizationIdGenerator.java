package com.codehouse.ciciassistant.auth.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class OrganizationIdGenerator {

    public static final String PREFIX = "org";
    public static final int TOTAL_LENGTH = 20;
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private final SecureRandom secureRandom = new SecureRandom();

    public String nextId() {
        StringBuilder builder = new StringBuilder(TOTAL_LENGTH);
        builder.append(PREFIX);
        while (builder.length() < TOTAL_LENGTH) {
            builder.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
