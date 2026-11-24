package io.github.parkwoong.tokenfilter;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

public final class AccessTokenFilter implements ExchangeFilterFunction {
    private final ReactiveStringRedisTemplate redis;
    private final WebClient authClient;
    private final URI tokenUri;
    private final String cacheKey;
    private final Duration ttl;
    private final String clientId;
    private final String clientSecret;

    public AccessTokenFilter(
            ReactiveStringRedisTemplate redis,
            WebClient authClient,
            URI tokenUri,
            String cacheKey,
            Duration ttl,
            String clientId,
            String clientSecret) {
        if (redis == null
                || authClient == null
                || tokenUri == null
                || tokenUri.getHost() == null
                || tokenUri.getUserInfo() != null
                || tokenUri.getFragment() != null
                || !("https".equalsIgnoreCase(tokenUri.getScheme())
                        || ("http".equalsIgnoreCase(tokenUri.getScheme())
                                && "localhost".equalsIgnoreCase(tokenUri.getHost())))
                || cacheKey == null
                || cacheKey.trim().isEmpty()
                || ttl == null
                || ttl.isNegative()
                || ttl.isZero()
                || clientId == null
                || clientId.trim().isEmpty()
                || clientSecret == null
                || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid token filter configuration");
        }
        this.redis = redis;
        this.authClient = authClient;
        this.tokenUri = tokenUri;
        this.cacheKey = cacheKey;
        this.ttl = ttl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return redis.opsForValue()
                .get(cacheKey)
                .filter(token -> !token.trim().isEmpty())
                .switchIfEmpty(Mono.defer(this::fetchAndCache))
                .flatMap(
                        token ->
                                next.exchange(
                                        ClientRequest.from(request)
                                                .headers(headers -> headers.setBearerAuth(token))
                                                .build()));
    }

    private Mono<String> fetchAndCache() {
        return authClient
                .post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                        BodyInserters.fromFormData("grant_type", "client_credentials")
                                .with("client_id", clientId)
                                .with("client_secret", clientSecret))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(body -> body.path("access_token").asText(""))
                .filter(token -> !token.trim().isEmpty())
                .switchIfEmpty(Mono.error(new IllegalStateException("Missing access token")))
                .flatMap(
                        token ->
                                redis.opsForValue()
                                        .set(cacheKey, token, ttl)
                                        .flatMap(
                                                stored ->
                                                        Boolean.TRUE.equals(stored)
                                                                ? Mono.just(token)
                                                                : Mono.error(
                                                                        new IllegalStateException(
                                                                                "Token cache write"
                                                                                    + " failed")))
                                        .switchIfEmpty(
                                                Mono.error(
                                                        new IllegalStateException(
                                                                "Token cache write failed"))));
    }
}
