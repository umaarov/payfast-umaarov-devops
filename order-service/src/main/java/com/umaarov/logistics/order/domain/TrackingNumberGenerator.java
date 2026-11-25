package com.umaarov.logistics.order.domain;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class TrackingNumberGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder builder = new StringBuilder("LG");
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
