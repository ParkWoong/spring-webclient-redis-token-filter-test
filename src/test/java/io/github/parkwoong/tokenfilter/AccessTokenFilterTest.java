package io.github.parkwoong.tokenfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class AccessTokenFilterTest {
    private static final URI TOKEN_URI = URI.create("https://auth.example.test/token");
    private static final String KEY = "sample:access-token";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ReactiveValueOperations<String, String> values =
            mock(ReactiveValueOperations.class);

    private final AtomicReference<String> cached = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger authCalls = new AtomicInteger();
    private final AtomicInteger apiCalls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenAnswer(ignored -> Mono.justOrEmpty(cached.get()));
        when(values.set(eq(KEY), anyString(), eq(TTL)))
                .thenAnswer(
                        invocation -> {
                            cached.set(invocation.getArgument(1));
                            return Mono.just(true);
                        });
    }

    @Test
    void cachedTokenSkipsAuthenticationRequest() {
        cached.set("cached-token");

        assertEquals("ok", callApi(apiClient(HttpStatus.OK, "{\"access_token\":\"new-token\"}")));
        assertEquals(0, authCalls.get());
        assertEquals(1, apiCalls.get());
        assertEquals("Bearer cached-token", authorization.get());
    }

    @Test
    void cacheMissFetchesStoresAndReusesToken() {
        WebClient apiClient = apiClient(HttpStatus.OK, "{\"access_token\":\"issued-token\"}");

        assertEquals("ok", callApi(apiClient));
        assertEquals("ok", callApi(apiClient));
        assertEquals("issued-token", cached.get());
        assertEquals("Bearer issued-token", authorization.get());
        assertEquals(1, authCalls.get());
        assertEquals(2, apiCalls.get());
    }

    @Test
    void authFailureDoesNotForwardRequestWithoutToken() {
        WebClient apiClient = apiClient(HttpStatus.UNAUTHORIZED, "{}");

        assertThrows(WebClientResponseException.class, () -> callApi(apiClient));
        assertEquals(1, authCalls.get());
        assertEquals(0, apiCalls.get());
        assertNull(cached.get());
    }

    @Test
    void cacheWriteFailureDoesNotForwardRequest() {
        when(values.set(eq(KEY), anyString(), eq(TTL))).thenReturn(Mono.just(false));
        WebClient apiClient = apiClient(HttpStatus.OK, "{\"access_token\":\"issued-token\"}");

        assertThrows(IllegalStateException.class, () -> callApi(apiClient));
        assertEquals(0, apiCalls.get());
        assertNull(cached.get());
    }

    @Test
    void credentialsAreNotSentToExternalHttpUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AccessTokenFilter(
                                redis,
                                WebClient.create(),
                                URI.create("http://auth.example.test/token"),
                                KEY,
                                TTL,
                                "sample-client",
                                "sample-secret"));
    }

    private WebClient apiClient(HttpStatus authStatus, String authBody) {
        WebClient authClient =
                WebClient.builder()
                        .exchangeFunction(
                                request -> {
                                    assertEquals(HttpMethod.POST, request.method());
                                    assertEquals(TOKEN_URI, request.url());
                                    assertEquals(
                                            MediaType.APPLICATION_FORM_URLENCODED,
                                            request.headers().getContentType());
                                    authCalls.incrementAndGet();
                                    return Mono.just(
                                            ClientResponse.create(authStatus)
                                                    .header("Content-Type", "application/json")
                                                    .body(authBody)
                                                    .build());
                                })
                        .build();
        AccessTokenFilter filter =
                new AccessTokenFilter(
                        redis, authClient, TOKEN_URI, KEY, TTL, "sample-client", "sample-secret");
        return WebClient.builder()
                .filter(filter)
                .exchangeFunction(
                        request -> {
                            apiCalls.incrementAndGet();
                            authorization.set(request.headers().getFirst("Authorization"));
                            return Mono.just(
                                    ClientResponse.create(HttpStatus.OK).body("ok").build());
                        })
                .build();
    }

    private String callApi(WebClient client) {
        return client.get()
                .uri("https://api.example.test/data")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(2));
    }
}
