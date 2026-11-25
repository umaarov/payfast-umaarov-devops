package com.umaarov.logistics.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.umaarov.logistics.gateway.security.ApiKeyAuthenticator;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatewaySecurityPropertiesTest {

    @Test
    void startupFailsWithoutKeys() {
        assertThatThrownBy(() -> new GatewaySecurityProperties(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GatewaySecurityProperties(List.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GatewaySecurityProperties(List.of("  ", "")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupFailsWithWeakKeys() {
        assertThatThrownBy(() -> new GatewaySecurityProperties(List.of("short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 16");
    }

    @Test
    void errorMessagesNeverContainTheKey() {
        assertThatThrownBy(() -> new GatewaySecurityProperties(List.of("secret-short")))
                .hasMessageNotContaining("secret-short");
    }

    @Test
    void keysAreTrimmedAndBlanksDropped() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties(
                Arrays.asList(" aaaaaaaaaaaaaaaa ", "", null, "bbbbbbbbbbbbbbbb"));

        assertThat(properties.apiKeys()).containsExactly("aaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb");
    }

    @Test
    void toStringDoesNotLeakKeys() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties(List.of("aaaaaaaaaaaaaaaa"));

        assertThat(properties.toString()).doesNotContain("aaaaaaaaaaaaaaaa").contains("1 configured");
    }

    @Test
    void authenticatorAcceptsOnlyExactKeys() {
        ApiKeyAuthenticator authenticator = new ApiKeyAuthenticator(
                new GatewaySecurityProperties(List.of("aaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb")));

        assertThat(authenticator.isValid("aaaaaaaaaaaaaaaa")).isTrue();
        assertThat(authenticator.isValid("bbbbbbbbbbbbbbbb")).isTrue();
        assertThat(authenticator.isValid("aaaaaaaaaaaaaaab")).isFalse();
        assertThat(authenticator.isValid("aaaaaaaaaaaaaaaa ")).isFalse();
        assertThat(authenticator.isValid("")).isFalse();
        assertThat(authenticator.isValid(null)).isFalse();
    }
}
