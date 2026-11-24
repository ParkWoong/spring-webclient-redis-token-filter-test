# WebClient Redis Token Filter / WebClient Redis 토큰 필터

[ENG](#eng) | [KR](#kr)

## ENG

### Project structure

```text
pom.xml
src/main/java/io/github/parkwoong/tokenfilter/Application.java
src/main/java/io/github/parkwoong/tokenfilter/TokenProbeController.java
src/main/java/io/github/parkwoong/tokenfilter/ClientConfig.java
src/main/java/io/github/parkwoong/tokenfilter/AccessTokenFilter.java
src/main/resources/application.yml
src/test/java/io/github/parkwoong/tokenfilter/AccessTokenFilterTest.java
```

### Code explanation

`ClientConfig` attaches `AccessTokenFilter` to the API `WebClient`. The filter reads an access token from Redis. On a cache miss, a separate `WebClient` sends a `client_credentials` form to the configured token URL, reads `access_token` from the JSON response, and stores it in Redis with a TTL. It then adds the token as a Bearer header to the API request.

If token retrieval or cache storage fails, the API request is not sent without a token. The token URL must use HTTPS or localhost HTTP. Set the cache TTL shorter than the token's actual lifetime. Simultaneous cache misses may request the token more than once. The default credentials and URLs in `application.yml` are local sample values; set the `EXAMPLE_*` environment variables for your own setup.

TokenProbeController calls the filtered WebClient and provides local sample token and echo endpoints.

### Run and test

Use JDK 8 and Maven 3.6.3+ from the repository root:

```sh
java -version
mvn clean verify
mvn spring-boot:run
```

The app listens on localhost:8080. `AccessTokenFilterTest` uses fake Redis operations and fake HTTP responses, so tests do not need a running Redis server or token service. It checks cache hits, cache misses, Bearer headers, and failed token or cache operations.

#### Postman API

Start a local Redis server on localhost:6379, then run the app. Send GET http://localhost:8080/api/token-probe with no body. Expected: HTTP 200, {"authorized":true}. The filter first reads example:access-token from Redis; on a miss it calls the local sample token endpoint and writes the token to Redis for 60 seconds. Repeat the request to use the cached token. Unit tests use fake Redis and do not need a server.

## KR

### 프로젝트 구조

```text
pom.xml
src/main/java/io/github/parkwoong/tokenfilter/Application.java
src/main/java/io/github/parkwoong/tokenfilter/TokenProbeController.java
src/main/java/io/github/parkwoong/tokenfilter/ClientConfig.java
src/main/java/io/github/parkwoong/tokenfilter/AccessTokenFilter.java
src/main/resources/application.yml
src/test/java/io/github/parkwoong/tokenfilter/AccessTokenFilterTest.java
```

### 코드 설명

`ClientConfig`는 API용 `WebClient`에 `AccessTokenFilter`를 연결합니다. 필터는 Redis에서 액세스 토큰을 읽습니다. 캐시에 없으면 별도 `WebClient`가 설정된 토큰 URL로 `client_credentials` form을 보내고, JSON 응답의 `access_token`을 읽어 만료 시간과 함께 Redis에 저장합니다. 이후 API 요청에 Bearer 헤더를 추가합니다.

토큰 발급이나 캐시 저장에 실패하면 토큰 없이 API 요청을 보내지 않습니다. 토큰 URL은 HTTPS 또는 localhost HTTP만 허용합니다. 캐시 만료 시간은 실제 토큰 유효 시간보다 짧게 설정하세요. 동시에 캐시가 비어 있으면 토큰 발급 요청이 중복될 수 있습니다. `application.yml`의 기본 인증 정보와 URL은 로컬 테스트용 값이며, 실제 환경에서는 `EXAMPLE_*` 환경 변수를 설정하세요.

TokenProbeController는 필터가 연결된 WebClient를 호출하며 로컬 샘플 토큰·응답 API를 제공합니다.

### 실행과 테스트

저장소 루트에서 JDK 8과 Maven 3.6.3 이상을 사용하세요.

```sh
java -version
mvn clean verify
mvn spring-boot:run
```

앱은 localhost:8080에서 실행됩니다. `AccessTokenFilterTest`는 가짜 Redis 동작과 가짜 HTTP 응답을 사용하므로 테스트에 실제 Redis 서버나 토큰 서비스가 필요하지 않습니다. 캐시 적중·미적중, Bearer 헤더, 토큰 발급 및 캐시 저장 실패를 확인합니다.

#### Postman API

localhost:6379에서 로컬 Redis 서버를 실행하고 앱을 시작하세요. 본문 없이 GET http://localhost:8080/api/token-probe을 보내면 HTTP 200, {"authorized":true}가 예상됩니다. 필터는 Redis의 example:access-token을 읽고, 값이 없으면 로컬 샘플 토큰 API를 호출해 60초 동안 Redis에 저장합니다. 다시 호출하면 캐시된 토큰을 사용합니다. 단위 테스트에는 실제 Redis 서버가 필요하지 않습니다.
