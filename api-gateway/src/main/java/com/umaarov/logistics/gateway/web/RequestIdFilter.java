package com.umaarov.logistics.gateway.web;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter implements WebFilter {

    public static final String HEADER = "X-Request-Id";

    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(HEADER);
        String requestId = supplied != null && ACCEPTED.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(HEADER, requestId);
        ServerWebExchange forwarded = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HEADER, requestId)))
                .build();
        return chain.filter(forwarded);
    }
}
