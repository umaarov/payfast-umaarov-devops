package com.umaarov.logistics.gateway.web;

import com.umaarov.logistics.gateway.security.ApiKeyAuthenticator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiKeyFilter implements WebFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyAuthenticator authenticator;
    private final PathPattern publicTimeline;
    private final PathPattern publicHealth;

    public ApiKeyFilter(ApiKeyAuthenticator authenticator) {
        this.authenticator = authenticator;
        PathPatternParser parser = new PathPatternParser();
        parser.setMatchOptionalTrailingSeparator(false);
        this.publicTimeline = parser.parse("/api/tracking/{trackingNumber}");
        this.publicHealth = parser.parse("/actuator/health");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!isPublic(request) && !authenticator.isValid(request.getHeaders().getFirst(HEADER))) {
            return ProblemResponses.write(exchange, HttpStatus.UNAUTHORIZED, "A valid X-API-Key header is required");
        }
        ServerWebExchange withoutKey = exchange.mutate()
                .request(builder -> builder.headers(headers -> headers.remove(HEADER)))
                .build();
        return chain.filter(withoutKey);
    }

    private boolean isPublic(ServerHttpRequest request) {
        if (request.getMethod() != HttpMethod.GET) {
            return false;
        }
        PathContainer path = request.getPath().pathWithinApplication();
        return publicTimeline.matches(path) || publicHealth.matches(path);
    }
}
