package robert_neat.his_backend.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import robert_neat.his_backend.security.JwtProperties;
import robert_neat.his_backend.security.JwtTokenService;
import robert_neat.his_backend.security.TokenVersionLookup;
import robert_neat.his_backend.staff.StaffRole;

/** Reguly CONNECT/SUBSCRIBE kanalu wejsciowego STOMP (bez Springa i bazy). */
class StompSecurityInterceptorTest {

    private static final String SECRET = "unit-test-jwt-secret-0123456789-abcdefghijklmnop";
    private static final String ISSUER = "his-backend";
    private static final UUID OWN_WARD = UUID.randomUUID();
    private static final UUID OTHER_WARD = UUID.randomUUID();
    /** Zawsze zgodna wersja tokenu: ten test sprawdza reguly CONNECT/SUBSCRIBE, nie odwolywanie tokenow. */
    private static final TokenVersionLookup ALWAYS_CURRENT = new TokenVersionLookup() {
        @Override
        public Optional<Integer> currentVersion(UUID accountId) {
            return Optional.of(0);
        }

        @Override
        public java.util.Map<UUID, Integer> currentVersions(java.util.Collection<UUID> accountIds) {
            java.util.Map<UUID, Integer> result = new java.util.HashMap<>();
            accountIds.forEach(id -> result.put(id, 0));
            return result;
        }
    };

    private final SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final JwtDecoder decoder = decoder(key);
    private final StompSecurityInterceptor interceptor = new StompSecurityInterceptor(decoder, ALWAYS_CURRENT,
            new StompSessionRegistry());

    // --- CONNECT ---

    @Test
    void connectWithValidTokenSetsPrincipalNamedByStaffId() {
        UUID staffId = UUID.randomUUID();
        String token = token(key, Duration.ofHours(1), staffId, StaffRole.DOCTOR, OWN_WARD);

        StompHeaderAccessor accessor = frame(StompCommand.CONNECT, null);
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        interceptor.preSend(message(accessor), null);

        assertThat(accessor.getUser()).isInstanceOfSatisfying(StompUserAuthentication.class, user -> {
            assertThat(user.getName()).isEqualTo(staffId.toString());
            assertThat(user.getPrincipal().staffId()).isEqualTo(staffId);
            assertThat(user.getPrincipal().role()).isEqualTo(StaffRole.DOCTOR);
            assertThat(user.isAuthenticated()).isTrue();
            assertThat(user.getAuthorities()).extracting(Object::toString).contains("alert:read", "ROLE_DOCTOR");
        });
    }

    @Test
    void connectAcceptsCaseInsensitiveBearerScheme() {
        String token = token(key, Duration.ofHours(1), UUID.randomUUID(), StaffRole.NURSE, OWN_WARD);
        StompHeaderAccessor accessor = frame(StompCommand.CONNECT, null);
        accessor.addNativeHeader("Authorization", "bearer " + token);

        interceptor.preSend(message(accessor), null);

        assertThat(accessor.getUser()).isNotNull();
    }

    @Test
    void connectWithExpiredTokenIsRejected() {
        assertConnectRejected("Bearer " + expiredToken(key));
    }

    @Test
    void connectWithTokenSignedByOtherKeyIsRejected() {
        SecretKey other = new SecretKeySpec("another-secret-0123456789-abcdefghijklmnopqrstu".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        assertConnectRejected("Bearer " + token(other, Duration.ofHours(1), UUID.randomUUID(), StaffRole.DOCTOR, OWN_WARD));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer not-a-jwt", "Bearer ", "Basic dXNlcjpwYXNz", "garbage", ""})
    void connectWithMalformedAuthorizationIsRejected(String header) {
        assertConnectRejected(header);
    }

    @Test
    void connectWithoutAuthorizationHeaderIsRejected() {
        assertConnectRejected(null);
    }

    @Test
    void connectWithTokenMissingClaimsIsRejected() {
        // poprawny podpis i waznosc, ale bez claimow pracownika (np. token innego typu)
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer(ISSUER)
                .subject(UUID.randomUUID().toString()).issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(600)).build();
        String token = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        assertConnectRejected("Bearer " + token);
    }

    // --- SUBSCRIBE ---

    @ParameterizedTest
    @ValueSource(strings = {"/user/queue/alerts", "/user/queue/messages", "/user/queue/threads", "/user/queue/tasks"})
    void userQueuesAreAllowedForAnyAuthenticatedRole(String destination) {
        for (StaffRole role : StaffRole.values()) {
            assertThatCode(() -> subscribe(connect(role, OWN_WARD), destination)).doesNotThrowAnyException();
        }
    }

    @Test
    void presenceTopicIsAllowedForAnyAuthenticatedRole() {
        for (StaffRole role : StaffRole.values()) {
            assertThatCode(() -> subscribe(connect(role, OWN_WARD), "/topic/presence")).doesNotThrowAnyException();
        }
    }

    @Test
    void presenceTopicIsForbiddenWithoutAuthentication() {
        assertThatThrownBy(() -> subscribe(null, "/topic/presence")).isInstanceOf(MessageDeliveryException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/queue/other", "/queue/alerts", "/user/queue/alerts/x",
            "/user/00000000-0000-0000-0000-000000000000/queue/alerts", "/topic/anything", "/topic/alerts",
            "/topic/alerts/", "/topic/alerts/not-a-uuid", "/app/anything", "/topic/messages"})
    void otherDestinationsAreForbidden(String destination) {
        assertThatThrownBy(() -> subscribe(connect(StaffRole.ADMIN, OWN_WARD), destination))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void wardAlertsAllowedForOwnWardWithAlertRead() {
        for (StaffRole role : new StaffRole[] {StaffRole.DOCTOR, StaffRole.NURSE, StaffRole.LAB_TECHNICIAN,
                StaffRole.RADIOLOGIST}) {
            assertThatCode(() -> subscribe(connect(role, OWN_WARD), "/topic/alerts/" + OWN_WARD))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void wardAlertsForbiddenForOtherWard() {
        assertThatThrownBy(() -> subscribe(connect(StaffRole.DOCTOR, OWN_WARD), "/topic/alerts/" + OTHER_WARD))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void wardAlertsForbiddenWithoutAlertReadPermission() {
        for (StaffRole role : new StaffRole[] {StaffRole.PHARMACIST, StaffRole.REGISTRAR}) {
            assertThatThrownBy(() -> subscribe(connect(role, OWN_WARD), "/topic/alerts/" + OWN_WARD))
                    .isInstanceOf(MessageDeliveryException.class);
        }
    }

    @Test
    void adminMaySubscribeToAnyWard() {
        assertThatCode(() -> subscribe(connect(StaffRole.ADMIN, OWN_WARD), "/topic/alerts/" + OTHER_WARD))
                .doesNotThrowAnyException();
    }

    @Test
    void subscribeWithoutAuthenticationIsRejected() {
        StompHeaderAccessor accessor = frame(StompCommand.SUBSCRIBE, "/user/queue/alerts");
        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    // --- pozostale polecenia ---

    @Test
    void sendIsAlwaysForbidden() {
        for (String destination : new String[] {"/app/anything", "/topic/alerts/" + OWN_WARD, "/user/queue/alerts"}) {
            StompHeaderAccessor accessor = frame(StompCommand.SEND, destination);
            accessor.setUser(connect(StaffRole.ADMIN, OWN_WARD));
            assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                    .isInstanceOf(MessageDeliveryException.class);
        }
    }

    @Test
    void unsubscribeRequiresAuthentication() {
        StompHeaderAccessor anonymous = frame(StompCommand.UNSUBSCRIBE, null);
        assertThatThrownBy(() -> interceptor.preSend(message(anonymous), null))
                .isInstanceOf(MessageDeliveryException.class);
        StompHeaderAccessor authenticated = frame(StompCommand.UNSUBSCRIBE, null);
        authenticated.setUser(connect(StaffRole.NURSE, OWN_WARD));
        assertThatCode(() -> interceptor.preSend(message(authenticated), null)).doesNotThrowAnyException();
    }

    @Test
    void disconnectAndHeartbeatPassWithoutAuthentication() {
        assertThatCode(() -> interceptor.preSend(message(frame(StompCommand.DISCONNECT, null)), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(message(StompHeaderAccessor.createForHeartbeat()), null))
                .doesNotThrowAnyException();
    }

    @Test
    void ackAndTransactionCommandsAreForbidden() {
        for (StompCommand command : new StompCommand[] {StompCommand.ACK, StompCommand.NACK, StompCommand.BEGIN,
                StompCommand.COMMIT, StompCommand.ABORT}) {
            StompHeaderAccessor accessor = frame(command, null);
            accessor.setUser(connect(StaffRole.ADMIN, OWN_WARD));
            assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                    .isInstanceOf(MessageDeliveryException.class);
        }
    }

    // --- pomocnicze ---

    private void assertConnectRejected(String authorizationHeader) {
        StompHeaderAccessor accessor = frame(StompCommand.CONNECT, null);
        if (authorizationHeader != null) {
            accessor.addNativeHeader("Authorization", authorizationHeader);
        }
        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThat(accessor.getUser()).isNull();
    }

    private StompUserAuthentication connect(StaffRole role, UUID wardId) {
        StompHeaderAccessor accessor = frame(StompCommand.CONNECT, null);
        accessor.addNativeHeader("Authorization",
                "Bearer " + token(key, Duration.ofHours(1), UUID.randomUUID(), role, wardId));
        interceptor.preSend(message(accessor), null);
        return (StompUserAuthentication) accessor.getUser();
    }

    private void subscribe(StompUserAuthentication user, String destination) {
        StompHeaderAccessor accessor = frame(StompCommand.SUBSCRIBE, destination);
        accessor.setUser(user);
        interceptor.preSend(message(accessor), null);
    }

    private static StompHeaderAccessor frame(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        accessor.setLeaveMutable(true);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        return accessor;
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static JwtDecoder decoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    private static String expiredToken(SecretKey key) {
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer(ISSUER)
                .subject(UUID.randomUUID().toString()).issuedAt(java.time.Instant.now().minusSeconds(7200))
                .expiresAt(java.time.Instant.now().minusSeconds(3600))
                .claim("staffId", UUID.randomUUID().toString()).claim("employeeId", "EMP-TEST").claim("role", "doctor")
                .claim("wardId", OWN_WARD.toString()).claim("authorities", java.util.List.of("alert:read")).build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key)).encode(
                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String token(SecretKey key, Duration ttl, UUID staffId, StaffRole role, UUID wardId) {
        JwtTokenService service = new JwtTokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)),
                new JwtProperties("ignored", ttl, ISSUER));
        return service.issue(UUID.randomUUID(), staffId, "EMP-TEST", role, wardId, 0).value();
    }
}
