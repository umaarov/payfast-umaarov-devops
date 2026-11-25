package com.umaarov.logistics.gateway.security;

import com.umaarov.logistics.gateway.config.GatewaySecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyAuthenticator {

    private final List<byte[]> keys;

    public ApiKeyAuthenticator(GatewaySecurityProperties properties) {
        this.keys = properties.apiKeys().stream().map(key -> key.getBytes(StandardCharsets.UTF_8)).toList();
    }

    public boolean isValid(String presented) {
        if (presented == null) {
            return false;
        }
        byte[] candidate = presented.getBytes(StandardCharsets.UTF_8);
        boolean matched = false;
        for (byte[] key : keys) {
            matched |= MessageDigest.isEqual(key, candidate);
        }
        return matched;
    }
}
