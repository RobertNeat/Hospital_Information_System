package robert_neat.his_backend.realtime;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import robert_neat.his_backend.realtime.StompSessionRegistry.SessionAccount;
import robert_neat.his_backend.security.TokenVersionLookup;

/**
 * Re-check tokenu STOMP poza CONNECT: {@link StompSecurityInterceptor} waliduje tylko przy nawiazaniu
 * polaczenia, a architektura jest jednoinstancyjna (prosty broker w pamieci), wiec zamiast ponownej
 * walidacji "przy okazji" kazdej ramki (sesje po SUBSCRIBE zwykle juz nic nie wysylaja - re-check per
 * ramke nie wylapalby wiekszosci przypadkow) wystarczy okresowy sweep: dla kazdej aktywnej sesji porownuje
 * zapamietana przy CONNECT wersje tokenu z biezaca wartoscia w bazie i zamyka niezgodne sesje. Interwal
 * jest konfigurowalny ({@code his.websocket.token-version-sweep-interval}), stad {@link SchedulingConfigurer}
 * zamiast {@code @Scheduled} ze stala wartoscia - testy moga ustawic krotki interwal wlasnosciami Springa.
 * <p>
 * Zamkniecie dziala przez wyslanie ramki STOMP ERROR zaadresowanej do konkretnej sesji na
 * `clientOutboundChannel` - {@code StompSubProtocolHandler} po takiej ramce zawsze zamyka fizyczne
 * polaczenie WebSocket (Spring `SubProtocolWebSocketHandler`/`StompSubProtocolHandler`), wiec klient
 * dostaje ERROR i rozlaczenie tak samo jak przy odrzuceniu CONNECT.
 */
@Component
class StompTokenVersionSweeper implements SchedulingConfigurer {

    private final StompSessionRegistry sessions;
    private final TokenVersionLookup tokenVersions;
    private final MessageChannel clientOutboundChannel;
    private final WebSocketProperties properties;

    StompTokenVersionSweeper(StompSessionRegistry sessions, TokenVersionLookup tokenVersions,
            @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel, WebSocketProperties properties) {
        this.sessions = sessions;
        this.tokenVersions = tokenVersions;
        this.clientOutboundChannel = clientOutboundChannel;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::sweep, properties.tokenVersionSweepInterval());
    }

    void sweep() {
        Map<String, SessionAccount> snapshot = sessions.snapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        Set<UUID> accountIds = snapshot.values().stream().map(SessionAccount::accountId).collect(Collectors.toSet());
        Map<UUID, Integer> current = tokenVersions.currentVersions(accountIds);
        snapshot.forEach((sessionId, entry) -> {
            Integer version = current.get(entry.accountId());
            if (version == null || !version.equals(entry.tokenVersion())) {
                closeSession(sessionId);
            }
        });
    }

    private void closeSession(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setSessionId(sessionId);
        accessor.setMessage("Token uniewazniony (zmiana stanu konta)");
        accessor.setLeaveMutable(true);
        clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
        sessions.remove(sessionId);
    }
}
