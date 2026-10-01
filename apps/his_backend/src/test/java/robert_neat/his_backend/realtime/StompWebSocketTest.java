package robert_neat.his_backend.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import robert_neat.his_backend.DemoAccounts;
import robert_neat.his_backend.TestcontainersConfiguration;
import robert_neat.his_backend.alert.AlertSeverity;
import robert_neat.his_backend.alert.AlertTarget;
import robert_neat.his_backend.alert.AlertTargetKind;
import robert_neat.his_backend.alert.AlertType;
import robert_neat.his_backend.alert.events.AlertCreated;
import robert_neat.his_backend.staff.PresenceRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * STOMP end-to-end z prawdziwym klientem (`WebSocketStompClient`) na RANDOM_PORT: login REST -> CONNECT z
 * `Authorization: Bearer` -> subskrypcje -> akcje domenowe przez REST -> push po commit.
 * <p>
 * Osobny kontekst Springa (nie dziedziczy {@code ApiIntegrationTest}): wymaga prawdziwego serwera (RANDOM_PORT)
 * i NIE jest {@code @Transactional} - akcje przez REST musza sie naprawde zatwierdzic, bo push jest `AFTER_COMMIT`
 * (w tescie z rollbackiem nic by nie wyslal). Koszt: dodatkowy kontener postgres + start kontekstu (kilkanascie
 * sekund). Dane utworzone przez testy (zadania, watki, alerty - rozpoznawane prefiksem {@value #MARK}) sa
 * sprzatane w {@code @AfterEach}; konta demo (login = haslo) sa tylko czytane.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.liquibase.contexts=reference,mock")
@Import(TestcontainersConfiguration.class)
class StompWebSocketTest {

    private static final String MARK = "STOMP-TEST";
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final Duration QUIET = Duration.ofMillis(1200);

    @LocalServerPort
    private int port;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private RestClient rest;
    private WebSocketStompClient client;

    @Autowired
    private PresenceRegistry presence;
    @Autowired
    private org.springframework.messaging.simp.user.SimpUserRegistry userRegistry;
    @Autowired
    private org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler broker;

    private final List<TestSession> sessions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private ThreadPoolTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        rest = RestClient.create("http://localhost:" + port);
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter(json));
        client.setTaskScheduler(scheduler); // wymagany przez RECEIPT
    }

    @AfterEach
    void cleanUp() {
        sessions.forEach(StompWebSocketTest::disconnectQuietly);
        await().atMost(WAIT).until(() -> presence.onlineStaffIds().isEmpty());
        client.stop();
        scheduler.shutdown();
        jdbc.update("delete from clinical_alert where message like ?", "%" + MARK + "%");
        jdbc.update("delete from team_task where title like ?", MARK + "%");
        jdbc.update("delete from message_thread where subject like ?", MARK + "%");
    }

    /**
     * Serwer zamyka sesje po ramce ERROR (odrzucone SUBSCRIBE), wiec moze to zrobic miedzy `isConnected()` a
     * `disconnect()`; wtedy `disconnect()` rzuca MessageDeliveryException. Ignorujemy wylacznie ten wyjatek
     * i tylko wtedy, gdy sesja faktycznie jest juz zamknieta.
     */
    private static void disconnectQuietly(TestSession handler) {
        StompSession session = handler.session;
        if (session == null || !session.isConnected()) {
            return;
        }
        try {
            session.disconnect();
        } catch (org.springframework.messaging.MessageDeliveryException e) {
            if (session.isConnected()) {
                throw e;
            }
        }
    }

    // --- uwierzytelnienie CONNECT ---

    @Test
    void connectWithoutTokenIsRejectedEvenThoughHandshakeIsPublic() throws Exception {
        TestSession session = new TestSession(null);
        client.connectAsync(url(), new WebSocketHttpHeaders(), new StompHeaders(), session);

        assertThat(session.errors.poll(WAIT.toSeconds(), TimeUnit.SECONDS)).as("ramka ERROR po CONNECT bez tokenu")
                .isNotNull();
        assertThat(session.connected).isNotDone();
    }

    @Test
    void connectWithGarbageTokenIsRejected() throws Exception {
        TestSession session = new TestSession(null);
        client.connectAsync(url(), new WebSocketHttpHeaders(), bearer("not.a.jwt"), session);

        assertThat(session.errors.poll(WAIT.toSeconds(), TimeUnit.SECONDS)).isNotNull();
        assertThat(session.connected).isNotDone();
    }

    @Test
    void handshakeFromForeignOriginIsRefused() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://evil.example");
        TestSession session = new TestSession(null);

        assertThatThrownBy(() -> client.connectAsync(url(), headers, bearer(login("nurse").token()), session)
                .get(WAIT.toSeconds(), TimeUnit.SECONDS)).hasMessageContaining("403");
    }

    // --- push po commit ---

    @Test
    void taskAssignmentPushesTaskAndAlertToAssigneeOnlyAfterCommit() throws Exception {
        Login doctor = login("doctor");
        Login nurse = login("nurse");
        TestSession nurseSession = connect(nurse);
        TestSession doctorSession = connect(doctor);
        nurseSession.subscribe("/user/queue/tasks");
        nurseSession.subscribe("/user/queue/alerts");
        doctorSession.subscribe("/user/queue/alerts");

        String task = post(doctor, "/api/v1/tasks", Map.of("title", MARK + " zadanie", "assignedToId",
                nurse.staffId(), "priority", "normal"));
        String taskId = json.readTree(task).get("id").asString();

        JsonNode taskPush = nurseSession.next("/user/queue/tasks");
        assertThat(taskPush.get("id").asString()).isEqualTo(taskId);
        assertThat(taskPush.get("status").asString()).isEqualTo("open");
        assertThat(taskPush.get("assignedToId").asString()).isEqualTo(nurse.staffId());
        // w chwili odbioru pushu dane sa juz zatwierdzone i widoczne dla innej transakcji
        assertThat(jdbc.queryForObject("select count(*) from team_task where id = ?::uuid", Integer.class, taskId))
                .isEqualTo(1);

        JsonNode alertPush = nurseSession.next("/user/queue/alerts");
        assertThat(alertPush.get("type").asString()).isEqualTo("task");
        assertThat(alertPush.get("severity").asString()).isEqualTo("info");
        assertThat(alertPush.get("acknowledged").asBoolean()).isFalse();
        assertThat(alertPush.has("acknowledgedById")).isFalse();
        assertThat(alertPush.get("target").get("kind").asString()).isEqualTo("task");
        assertThat(alertPush.get("target").get("id").asString()).isEqualTo(taskId);
        assertThat(Instant.parse(alertPush.get("createdAt").asString())).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from clinical_alert where id = ?::uuid", Integer.class,
                alertPush.get("id").asString())).isEqualTo(1);

        // zmiana statusu przez tworce -> push zadania do osoby przypisanej
        post(doctor, "/api/v1/tasks/" + taskId + "/status", Map.of("status", "in_progress"));
        assertThat(nurseSession.next("/user/queue/tasks").get("status").asString()).isEqualTo("in_progress");

        // potwierdzenie alertu -> projekcja tylko do sesji potwierdzajacego (acknowledged:true)
        post(nurse, "/api/v1/alerts/" + alertPush.get("id").asString() + "/acknowledge", Map.of());
        JsonNode acked = nurseSession.next("/user/queue/alerts");
        assertThat(acked.get("id").asString()).isEqualTo(alertPush.get("id").asString());
        assertThat(acked.get("acknowledged").asBoolean()).isTrue();
        assertThat(acked.get("acknowledgedById").asString()).isEqualTo(nurse.staffId());

        // lekarz (tworca zadania, nie adresat alertu) nie dostaje cudzych alertow
        assertThat(doctorSession.poll("/user/queue/alerts", QUIET)).isNull();
    }

    @Test
    void newMessageThreadPushesMessageAndViewerScopedThreadsThenReadSyncs() throws Exception {
        Login doctor = login("doctor");
        Login nurse = login("nurse");
        TestSession nurseSession = connect(nurse);
        TestSession doctorSession = connect(doctor);
        nurseSession.subscribe("/user/queue/messages");
        nurseSession.subscribe("/user/queue/threads");
        doctorSession.subscribe("/user/queue/messages");
        doctorSession.subscribe("/user/queue/threads");

        String thread = post(doctor, "/api/v1/message-threads", Map.of("participantIds", List.of(nurse.staffId()),
                "subject", MARK + " watek", "firstMessage", Map.of("body", "Czesc", "priority", "normal")));
        String threadId = json.readTree(thread).get("id").asString();

        JsonNode message = nurseSession.next("/user/queue/messages");
        assertThat(message.get("threadId").asString()).isEqualTo(threadId);
        assertThat(message.get("senderId").asString()).isEqualTo(doctor.staffId());
        assertThat(message.get("body").asString()).isEqualTo("Czesc");
        assertThat(nurseSession.next("/user/queue/threads").get("unreadCount").asInt()).isEqualTo(1);
        // nadawca: watek (zmiana lastMessageAt) z jego wlasnym unreadCount = 0, ale nie wlasna wiadomosc
        assertThat(doctorSession.next("/user/queue/threads").get("unreadCount").asInt()).isZero();
        assertThat(doctorSession.poll("/user/queue/messages", QUIET)).isNull();

        // read -> sync sesji czytajacego z unreadCount 0
        post(nurse, "/api/v1/message-threads/" + threadId + "/read", Map.of());
        JsonNode afterRead = nurseSession.next("/user/queue/threads");
        assertThat(afterRead.get("id").asString()).isEqualTo(threadId);
        assertThat(afterRead.get("unreadCount").asInt()).isZero();
    }

    @Test
    void rolledBackTransactionPushesNothingAndCommittedOneDoesIncludingWardTopic() throws Exception {
        Login doctor = login("doctor");
        Login nurse = login("nurse");
        TestSession nurseSession = connect(nurse);
        TestSession doctorSession = connect(doctor);
        nurseSession.subscribe("/user/queue/alerts");
        doctorSession.subscribe("/topic/alerts/" + DemoAccounts.WARD_ID);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID ward = UUID.fromString(DemoAccounts.WARD_ID);
        UUID nurseId = UUID.fromString(nurse.staffId());

        tx.executeWithoutResult(status -> {
            events.publishEvent(alert(MARK + " rollback", ward, nurseId));
            status.setRollbackOnly();
        });
        assertThat(nurseSession.poll("/user/queue/alerts", QUIET)).isNull();
        assertThat(doctorSession.poll("/topic/alerts/" + DemoAccounts.WARD_ID, Duration.ofMillis(10))).isNull();

        tx.executeWithoutResult(status -> events.publishEvent(alert(MARK + " commit", ward, nurseId)));
        JsonNode user = nurseSession.next("/user/queue/alerts");
        JsonNode topic = doctorSession.next("/topic/alerts/" + DemoAccounts.WARD_ID);
        assertThat(user.get("message").asString()).isEqualTo(MARK + " commit");
        assertThat(topic.get("message").asString()).isEqualTo(MARK + " commit");
        assertThat(topic.get("acknowledged").asBoolean()).isFalse();
        assertThat(topic.has("acknowledgedById")).isFalse();
        assertThat(topic.get("severity").asString()).isEqualTo("critical");
    }

    // --- autoryzacja subskrypcji ---

    @Test
    void forbiddenSubscriptionsGetErrorFrame() throws Exception {
        TestSession pharmacist = connect(login("pharmacist"));
        pharmacist.subscribeInternal("/topic/alerts/" + DemoAccounts.WARD_ID);
        assertThat(pharmacist.errors.poll(WAIT.toSeconds(), TimeUnit.SECONDS)).as("brak alert:read").isNotNull();

        TestSession nurse = connect(login("nurse"));
        nurse.subscribeInternal("/topic/alerts/" + UUID.randomUUID());
        assertThat(nurse.errors.poll(WAIT.toSeconds(), TimeUnit.SECONDS)).as("cudzy oddzial").isNotNull();

        TestSession doctor = connect(login("doctor"));
        doctor.subscribeInternal("/queue/alerts");
        assertThat(doctor.errors.poll(WAIT.toSeconds(), TimeUnit.SECONDS)).as("kolejka bez /user").isNotNull();
    }

    // --- obecnosc ---

    @Test
    void staffOnlineFollowsStompSessions() throws Exception {
        Login doctor = login("doctor");
        Login nurse = login("nurse");
        assertThat(online(doctor, nurse.staffId())).isFalse();

        TestSession first = connect(nurse);
        await().atMost(WAIT).untilAsserted(() -> assertThat(online(doctor, nurse.staffId())).isTrue());
        assertThat(online(doctor, doctor.staffId())).isFalse();
        // /auth/me tez bierze obecnosc z rejestru
        assertThat(json.readTree(get(nurse, "/api/v1/auth/me")).get("online").asBoolean()).isTrue();

        TestSession second = connect(nurse);
        first.session.disconnect();
        // druga sesja nadal trzyma uzytkownika online
        await().during(Duration.ofMillis(800)).atMost(WAIT)
                .untilAsserted(() -> assertThat(online(doctor, nurse.staffId())).isTrue());

        second.session.disconnect();
        await().atMost(WAIT).untilAsserted(() -> assertThat(online(doctor, nurse.staffId())).isFalse());
    }

    @Test
    void abruptConnectionLossAlsoMarksOffline() throws Exception {
        Login doctor = login("doctor");
        Login nurse = login("nurse");
        // surowy WebSocket: ramka CONNECT "z reki", potem zamkniecie transportu bez ramki DISCONNECT
        org.springframework.web.socket.WebSocketSession raw = new StandardWebSocketClient()
                .execute(new org.springframework.web.socket.handler.AbstractWebSocketHandler() {
                }, url()).get(WAIT.toSeconds(), TimeUnit.SECONDS);
        raw.sendMessage(new org.springframework.web.socket.TextMessage(
                "CONNECT\naccept-version:1.2\nAuthorization:Bearer " + nurse.token() + "\n\n\u0000"));
        await().atMost(WAIT).untilAsserted(() -> assertThat(online(doctor, nurse.staffId())).isTrue());

        raw.close();
        await().atMost(WAIT).untilAsserted(() -> assertThat(online(doctor, nurse.staffId())).isFalse());
    }

    // --- pomocnicze ---

    private AlertCreated alert(String message, UUID wardId, UUID recipient) {
        return new AlertCreated(UUID.randomUUID(), AlertType.CRITICAL_RESULT, AlertSeverity.CRITICAL, null, message,
                Instant.now(), new AlertTarget(AlertTargetKind.TASK, UUID.randomUUID(), null), wardId,
                List.of(recipient));
    }

    private String url() {
        return "ws://localhost:" + port + "/ws";
    }

    private static StompHeaders bearer(String token) {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return headers;
    }

    private TestSession connect(Login login) throws Exception {
        TestSession handler = new TestSession(login.staffId());
        sessions.add(handler);
        handler.session = client.connectAsync(url(), new WebSocketHttpHeaders(), bearer(login.token()), handler)
                .get(WAIT.toSeconds(), TimeUnit.SECONDS);
        return handler;
    }

    private record Login(String token, String staffId) {
    }

    private Login login(String account) {
        String body = rest.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("employeeId", account, "password", account)).retrieve().body(String.class);
        JsonNode node = json.readTree(body);
        return new Login(node.get("accessToken").asString(), node.get("user").get("id").asString());
    }

    private String post(Login as, String path, Object body) {
        return rest.post().uri(path).header("Authorization", "Bearer " + as.token())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
    }

    private String get(Login as, String path) {
        return rest.get().uri(path).header("Authorization", "Bearer " + as.token()).retrieve().body(String.class);
    }

    private boolean online(Login viewer, String staffId) {
        return json.readTree(get(viewer, "/api/v1/staff/" + staffId)).get("online").asBoolean();
    }

    /** Czy prosty broker ma subskrypcje na dany temat (dla `/user/...` - sesji uzytkownika `staffId`). */
    private boolean brokerHasSubscription(String staffId, String destination) {
        if (!destination.startsWith("/user/")) {
            return hasSubscribers(destination);
        }
        String queue = destination.substring("/user".length());
        return userRegistry.getUsers().stream().filter(u -> u.getName().equals(staffId))
                .flatMap(u -> u.getSessions().stream())
                .anyMatch(s -> hasSubscribers(queue + "-user" + s.getId()));
    }

    private boolean hasSubscribers(String brokerDestination) {
        var headers = org.springframework.messaging.simp.SimpMessageHeaderAccessor
                .create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        headers.setDestination(brokerDestination);
        headers.setLeaveMutable(true);
        var message = org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],
                headers.getMessageHeaders());
        return !broker.getSubscriptionRegistry().findSubscriptions(message).isEmpty();
    }

    /** Sesja testowa: zbiera ramki per temat (payload jako JSON) oraz ramki ERROR. */
    private final class TestSession extends StompSessionHandlerAdapter {

        final CompletableFuture<StompSession> connected = new CompletableFuture<>();
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        private final Map<String, BlockingQueue<JsonNode>> frames = new java.util.concurrent.ConcurrentHashMap<>();
        private final String staffId;
        volatile StompSession session;

        TestSession(String staffId) {
            this.staffId = staffId;
        }

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            connected.complete(session);
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errors.add(String.valueOf(headers.getFirst("message")));
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            errors.add("transport: " + exception);
        }

        @Override
        public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command,
                StompHeaders headers, byte[] payload, Throwable exception) {
            errors.add("exception: " + exception);
        }

        /** Subskrybuje i czeka, az broker zarejestruje subskrypcje (prosty broker nie obsluguje RECEIPT). */
        void subscribe(String destination) {
            subscribeInternal(destination);
            await().atMost(WAIT).until(() -> brokerHasSubscription(staffId, destination));
        }

        /** Subskrypcja bez oczekiwania na rejestracje (dla tematow, ktore maja zostac odrzucone). */
        void subscribeInternal(String destination) {
            BlockingQueue<JsonNode> queue = frames.computeIfAbsent(destination, d -> new LinkedBlockingQueue<>());
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                    return JsonNode.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    queue.add((JsonNode) payload);
                }
            });
        }

        JsonNode next(String destination) throws Exception {
            JsonNode payload = frames.get(destination).poll(WAIT.toSeconds(), TimeUnit.SECONDS);
            assertThat(payload).as("push na " + destination).isNotNull();
            return payload;
        }

        JsonNode poll(String destination, Duration timeout) throws Exception {
            return frames.get(destination).poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
