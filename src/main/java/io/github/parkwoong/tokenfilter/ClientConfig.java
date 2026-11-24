package io.github.parkwoong.tokenfilter;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;

@Configuration
public class ClientConfig {
    @Bean
    public WebClient authClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public AccessTokenFilter accessTokenFilter(
            ReactiveStringRedisTemplate redis,
            @Qualifier("authClient") WebClient authClient,
            @Value("${example.token-url}") URI tokenUri,
            @Value("${example.cache-key}") String cacheKey,
            @Value("${example.token-ttl-seconds}") long ttlSeconds,
            @Value("${example.client-id}") String clientId,
            @Value("${example.client-secret}") String clientSecret) {
        return new AccessTokenFilter(
                redis,
                authClient,
                tokenUri,
                cacheKey,
                Duration.ofSeconds(ttlSeconds),
                clientId,
                clientSecret);
    }

    @Bean
    public WebClient apiClient(WebClient.Builder builder, AccessTokenFilter filter) {
        return builder.filter(filter).build();
    }
}
