package com.docflow.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcAuthProviderTest {

    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "docflow";

    @Test
    void acceptsValidSignedJwt() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            UserContext user = provider.authenticate(request(jwkServer.token(claims()
                    .subject("user-1")
                    .claim("email", "user-1@example.test")
                    .claim("roles", List.of("maker", " reviewer ")))));

            assertThat(user.userId()).isEqualTo("user-1");
            assertThat(user.email()).isEqualTo("user-1@example.test");
            assertThat(user.roles()).isEmpty();
            assertThat(user.source()).isEqualTo("OIDC");
        }
    }

    @Test
    void missingAuthorizationHeaderReturnsUnauthorized() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            assertUnauthorized(() -> provider.authenticate(new MockHttpServletRequest()));
        }
    }

    @Test
    void nonBearerAuthorizationHeaderReturnsUnauthorized() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic abc");

            assertUnauthorized(() -> provider.authenticate(request));
        }
    }

    @Test
    void expiredJwtReturnsUnauthorized() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            assertUnauthorized(() -> provider.authenticate(request(jwkServer.token(claims()
                    .subject("user-1")
                    .expirationTime(Date.from(Instant.now().minusSeconds(120)))))));
        }
    }

    @Test
    void invalidAudienceReturnsUnauthorized() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            assertUnauthorized(() -> provider.authenticate(request(jwkServer.token(claims()
                    .subject("user-1")
                    .audience("other-service")))));
        }
    }

    @Test
    void audienceValidationAcceptsScalarStringAudience() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            UserContext user = provider.authenticate(request(jwkServer.token(claimsWithoutAudience()
                    .subject("user-1")
                    .claim("aud", AUDIENCE))));

            assertThat(user.userId()).isEqualTo("user-1");
        }
    }

    @Test
    void audienceValidationAcceptsListAudience() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            UserContext user = provider.authenticate(request(jwkServer.token(claims()
                    .subject("user-1"))));

            assertThat(user.userId()).isEqualTo("user-1");
        }
    }

    @Test
    void missingAudienceIsAcceptedWhenAudienceIsNotConfigured() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, null);

            UserContext user = provider.authenticate(request(jwkServer.token(claimsWithoutAudience()
                    .subject("user-1"))));

            assertThat(user.userId()).isEqualTo("user-1");
        }
    }

    @Test
    void invalidIssuerReturnsUnauthorized() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            assertUnauthorized(() -> provider.authenticate(request(jwkServer.token(claims()
                    .subject("user-1")
                    .issuer("https://wrong-issuer.example.test")))));
        }
    }

    @Test
    void missingConfiguredUserIdClaimReturnsUnauthorized() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            assertUnauthorized(() -> provider.authenticate(request(jwkServer.token(claims()
                    .claim("email", "user-1@example.test")))));
        }
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start();
             TestJwkServer signingServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            assertUnauthorized(() -> provider.authenticate(request(signingServer.token(claims()
                    .subject("user-1")))));
        }
    }

    @Test
    void malformedJwtReturnsUnauthorized() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            assertUnauthorized(() -> provider.authenticate(request("not-a-jwt")));
        }
    }

    @Test
    void rolesClaimAsArrayIsIgnoredForDocFlowAuthorization() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            UserContext user = provider.authenticate(request(jwkServer.token(claims()
                    .subject("user-1")
                    .claim("roles", List.of("maker", " reviewer ", "")))));

            assertThat(user.roles()).isEmpty();
        }
    }

    @Test
    void rolesClaimAsCommaSeparatedStringIsIgnoredForDocFlowAuthorization() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            UserContext user = provider.authenticate(request(jwkServer.token(claims()
                    .subject("user-1")
                    .claim("roles", "maker, reviewer, "))));

            assertThat(user.roles()).isEmpty();
        }
    }

    @Test
    void missingRolesClaimAuthenticatesSafely() throws Exception {
        try (TestJwkServer jwkServer = TestJwkServer.start()) {
            OidcAuthProvider provider = provider(jwkServer, ISSUER, AUDIENCE);

            UserContext user = provider.authenticate(request(jwkServer.token(claims()
                    .subject("user-1"))));

            assertThat(user.roles()).isEmpty();
        }
    }

    private OidcAuthProvider provider(TestJwkServer jwkServer, String issuer, String audience) {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.OIDC_AUTH);
        properties.getOidc().setJwkSetUri(jwkServer.jwkSetUri());
        properties.getOidc().setIssuerUri(issuer);
        properties.getOidc().setAudience(audience);
        return new OidcAuthProvider(properties);
    }

    private JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    private JWTClaimsSet.Builder claimsWithoutAudience() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private void assertUnauthorized(ThrowingRunnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(401);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class TestJwkServer implements AutoCloseable {
        private final HttpServer server;
        private final RSAKey rsaKey;

        private TestJwkServer(HttpServer server, RSAKey rsaKey) {
            this.server = server;
            this.rsaKey = rsaKey;
        }

        static TestJwkServer start() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID("test-key")
                    .build();

            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            TestJwkServer testServer = new TestJwkServer(server, rsaKey);
            server.createContext("/jwks", exchange -> {
                byte[] body = ("{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return testServer;
        }

        String jwkSetUri() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
        }

        String token(JWTClaimsSet.Builder claims) throws Exception {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(rsaKey.getKeyID())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims.build()
            );
            jwt.sign(new RSASSASigner(rsaKey));
            return jwt.serialize();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
