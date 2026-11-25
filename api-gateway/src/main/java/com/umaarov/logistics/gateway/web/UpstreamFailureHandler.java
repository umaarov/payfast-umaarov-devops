package com.umaarov.logistics.gateway.web;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class UpstreamFailureHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(failure);
        }
        if (causedBy(failure, TimeoutException.class)
                || causedBy(failure, ReadTimeoutException.class)
                || causedBy(failure, ConnectTimeoutException.class)) {
            return ProblemResponses.write(
                    exchange, HttpStatus.GATEWAY_TIMEOUT, "The upstream service did not answer in time");
        }
        if (causedBy(failure, ConnectException.class) || causedBy(failure, UnknownHostException.class)) {
            return ProblemResponses.write(
                    exchange, HttpStatus.SERVICE_UNAVAILABLE, "The upstream service is unavailable");
        }
        return Mono.error(failure);
    }

    private boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
        }
        return false;
    }
}
