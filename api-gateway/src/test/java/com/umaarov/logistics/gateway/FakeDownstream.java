package com.umaarov.logistics.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

final class FakeDownstream {

    record Received(String method, String uri, Map<String, String> headers, String body) {

        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private final HttpServer server;
    private final List<Received> requests = new CopyOnWriteArrayList<>();

    FakeDownstream() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> headers = exchange.getRequestHeaders().entrySet().stream()
                    .filter(entry -> !entry.getValue().isEmpty())
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().toLowerCase(Locale.ROOT),
                            entry -> entry.getValue().get(0),
                            (first, second) -> first));
            requests.add(new Received(
                    exchange.getRequestMethod(), exchange.getRequestURI().getRawPath()
                            + (exchange.getRequestURI().getRawQuery() == null
                                    ? "" : "?" + exchange.getRequestURI().getRawQuery()),
                    headers, body));
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<Received> requests() {
        return requests;
    }

    void clear() {
        requests.clear();
    }

    void stop() {
        server.stop(0);
    }
}
