package robert_neat.his_backend.realtime;

import java.util.Locale;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import robert_neat.his_backend.security.HisJwtAuthenticationConverter;
import robert_neat.his_backend.security.HisUserPrincipal;
import robert_neat.his_backend.staff.StaffRole;

/**
 * Bezpieczenstwo kanalu wejsciowego STOMP (handshake `/ws` jest publiczny, cala ochrona jest tutaj):
 * <ul>
 *   <li>CONNECT: wymagany naglowek `Authorization: Bearer <JWT>`; token jest weryfikowany tym samym
 *       {@link JwtDecoder} i konwerterem claimow co REST. Brak/zly/wygasly token = odrzucenie CONNECT (ERROR);</li>
 *   <li>SUBSCRIBE: tylko `/user/queue/{alerts,messages,threads,tasks}` (dla uwierzytelnionego) oraz
 *       `/topic/alerts/{wardId}` (uprawnienie `alert:read` i wlasny oddzial; `admin` - kazdy oddzial);</li>
 *   <li>SEND (takze `/app/**`) jest zawsze zabroniony - kontrakt nie przewiduje komunikatow klient -> serwer
 *       (bez tego klient moglby publikowac na `/topic/**`); inne polecenia poza UNSUBSCRIBE/DISCONNECT tez.</li>
 * </ul>
 * Ramki bez polecenia (heartbeat) przechodza. Waznosc tokenu jest sprawdzana tylko przy CONNECT.
 */
class StompSecurityInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "bearer ";
    private static final String ALERT_READ = "alert:read";

    private final JwtDecoder jwtDecoder;
    private final HisJwtAuthenticationConverter converter = new HisJwtAuthenticationConverter();

    StompSecurityInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        StompCommand command = accessor == null ? null : accessor.getCommand();
        if (command == null) {
            return message;
        }
        switch (command) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(message, accessor));
            case SUBSCRIBE -> authorizeSubscribe(message, accessor);
            case UNSUBSCRIBE -> requireAuthenticated(message, accessor);
            case DISCONNECT -> {
                // zawsze dozwolone (takze po odrzuconym CONNECT)
            }
            default -> throw deny(message, "Polecenie STOMP " + command + " jest niedozwolone");
        }
        return message;
    }

    private StompUserAuthentication authenticate(Message<?> message, StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX)) {
            throw deny(message, "Brak tokenu: wymagany naglowek Authorization: Bearer <token>");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Authentication auth = converter.convert(jwt);
            if (auth == null || !(auth.getPrincipal() instanceof HisUserPrincipal principal)) {
                throw new InvalidBearerTokenException("Niepoprawny token");
            }
            return new StompUserAuthentication(principal, auth.getAuthorities());
        } catch (JwtException | InvalidBearerTokenException e) {
            throw deny(message, "Nieprawidlowy lub wygasly token");
        }
    }

    private void authorizeSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        StompUserAuthentication user = requireAuthenticated(message, accessor);
        String destination = accessor.getDestination();
        if (destination != null && StompDestinations.USER_QUEUES.contains(destination)) {
            return;
        }
        if (destination != null && destination.startsWith(StompDestinations.TOPIC_ALERTS_PREFIX)
                && mayReadWardAlerts(user, destination.substring(StompDestinations.TOPIC_ALERTS_PREFIX.length()))) {
            return;
        }
        throw deny(message, "Brak dostepu do tematu " + destination);
    }

    private static boolean mayReadWardAlerts(StompUserAuthentication user, String wardId) {
        UUID ward;
        try {
            ward = UUID.fromString(wardId);
        } catch (IllegalArgumentException e) {
            return false;
        }
        boolean canRead = user.getAuthorities().stream().anyMatch(a -> ALERT_READ.equals(a.getAuthority()));
        HisUserPrincipal principal = user.getPrincipal();
        return canRead && (principal.role() == StaffRole.ADMIN || ward.equals(principal.wardId()));
    }

    private StompUserAuthentication requireAuthenticated(Message<?> message, StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof StompUserAuthentication user && user.isAuthenticated()) {
            return user;
        }
        throw deny(message, "Wymagane uwierzytelnienie (CONNECT z tokenem)");
    }

    private static MessageDeliveryException deny(Message<?> message, String reason) {
        return new MessageDeliveryException(message, reason);
    }
}
