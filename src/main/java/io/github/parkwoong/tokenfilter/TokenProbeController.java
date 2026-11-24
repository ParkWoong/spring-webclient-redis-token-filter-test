package io.github.parkwoong.tokenfilter;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

@RestController
public class TokenProbeController {
    private final WebClient apiClient;
    private final URI apiUri;

    public TokenProbeController(
            @Qualifier("apiClient") WebClient apiClient, @Value("${example.api-url}") URI apiUri) {
        this.apiClient = apiClient;
        this.apiUri = apiUri;
    }

    @GetMapping("/api/token-probe")
    public Mono<JsonNode> probe() {
        return apiClient.get().uri(apiUri).retrieve().bodyToMono(JsonNode.class);
    }

    @PostMapping("/sample/token")
    public Map<String, String> sampleToken() {
        return Collections.singletonMap("access_token", "sample-token");
    }

    @GetMapping("/sample/echo")
    public Map<String, Boolean> sampleEcho(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Collections.singletonMap(
                "authorized", authorization != null && authorization.startsWith("Bearer "));
    }
}
