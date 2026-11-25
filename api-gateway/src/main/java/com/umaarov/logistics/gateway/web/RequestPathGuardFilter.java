package com.umaarov.logistics.gateway.web;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestPathGuardFilter implements WebFilter {

    private static final Pattern PERCENT_ESCAPE = Pattern.compile("%[0-9a-fA-F]{2}");
    private static final String INTERNAL_PREFIX = "/internal";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
            return chain.filter(exchange);
        }
        String lowerRaw = rawPath.toLowerCase(Locale.ROOT);
        if (lowerRaw.contains("%2f") || lowerRaw.contains("%5c") || lowerRaw.contains("%00")) {
            return ProblemResponses.write(exchange, HttpStatus.BAD_REQUEST, "The request path is not allowed");
        }
        String decoded;
        try {
            decoded = UriUtils.decode(rawPath, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return ProblemResponses.write(exchange, HttpStatus.BAD_REQUEST, "The request path is malformed");
        }
        if (isSuspicious(decoded)) {
            return ProblemResponses.write(exchange, HttpStatus.BAD_REQUEST, "The request path is not allowed");
        }
        String normalized = decoded.toLowerCase(Locale.ROOT);
        if (normalized.equals(INTERNAL_PREFIX) || normalized.startsWith(INTERNAL_PREFIX + "/")) {
            return ProblemResponses.write(exchange, HttpStatus.NOT_FOUND, "No route matches the request");
        }
        return chain.filter(exchange);
    }

    private boolean isSuspicious(String decoded) {
        if (decoded.indexOf('\\') >= 0 || decoded.indexOf('\0') >= 0 || decoded.contains("//")) {
            return true;
        }
        if (PERCENT_ESCAPE.matcher(decoded).find()) {
            return true;
        }
        for (String segment : decoded.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }
}
