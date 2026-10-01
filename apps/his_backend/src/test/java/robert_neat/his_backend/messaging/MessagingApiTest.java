package robert_neat.his_backend.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import robert_neat.his_backend.ApiIntegrationTest;
import robert_neat.his_backend.messaging.events.MessageSent;
import robert_neat.his_backend.messaging.events.TaskAssigned;
import robert_neat.his_backend.messaging.events.TaskStatusChanged;

/**
 * Kontrakt komunikacji (API.md, par. 8) na danych mock: 6 watkow, 14 wiadomosci, 8 zadan, 3 przekazania zmiany.
 * Uzytkownicy z tokenow pracownikow mock (`EMP-0001`.. haslo `HisDemo2026!`) lub kont demo; aktor pochodzi z tokenu.
 * Dane mock maja znaczniki wzgledne (`now() - interval`), a kursory i wiadomosci z tej samej godziny roznia sie o
 * milisekundy (osobne changesety) - dlatego graniczne przypadki `unreadCount` weryfikuje wyrocznia SQL.
 */
@RecordApplicationEvents
class MessagingApiTest extends ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    // watki mock
    private static final String THREAD_FUROSEMIDE = "93e35d57-19fe-573b-8dbf-44f29d69af5c"; // EMP-0001 + EMP-0006
    private static final String THREAD_CORONARY = "d287dcc0-bbbf-5998-9a7b-f773cf7f57d1"; // EMP-0002 + EMP-0007
    private static final String THREAD_CARDIO = "28ddda82-8247-5f25-972a-27f6ff75e92c"; // EMP-0001 + EMP-0002
    private static final String THREAD_REHAB = "aceb453e-e3ac-571e-a371-d221f64bd6a8"; // EMP-0004 + EMP-0009

    // pracownicy mock
    private static final String DOC_1 = "16259545-f97c-531d-b9cd-6ba115379372"; // EMP-0001
    private static final String DOC_2 = "6a2063f1-ade9-52c8-a1b0-f894c0093d46"; // EMP-0002
    private static final String DOC_3 = "19d27aee-1c46-5dd5-a0a4-21da1fe3cf3e"; // EMP-0003
    private static final String DOC_4 = "0ffcc103-5e1d-5346-aeba-8f9907cce3df"; // EMP-0004
    private static final String NURSE_6 = "625e824c-3b63-51c2-9e56-78cbfd0ff9a5"; // EMP-0006
    private static final String NURSE_7 = "f9dd7885-41c2-5cf0-a4ca-2a5b45474502"; // EMP-0007
    private static final String NURSE_8 = "570c2cc4-eafe-5082-a1ca-e47097316d3a"; // EMP-0008
    private static final String NURSE_9 = "42fb062f-6dc8-574b-b1bd-fc33c5e4decb"; // EMP-0009

    private static final String KOWALSKI = "c078186c-c437-5fa8-8a5b-a6bf8883f8bf";
    private static final String SZYMANSKI = "50c8f3fa-ea66-581a-9207-f9c4c7131d26";
    private static final String MAZUR = "55cc6e9e-6413-58bc-88b6-6342579d8413";
    private static final String WARD_INTERNAL = "25c25490-5067-5aaa-bcf5-5dc23f56588b";
    private static final String WARD_CARDIO = "36877e50-4f0b-5b6c-bc22-983228fe0d83";

    // zadania mock
    private static final String TASK_CLEXANE = "94b29fa1-7c9e-5182-aab3-bdeceba70201"; // open, EMP-0003 -> EMP-0008
    private static final String TASK_GLUCOSE = "9411861a-a089-507a-8a0b-c51f67e1ef25"; // in_progress, EMP-0004 -> EMP-0009
    private static final String TASK_BLOOD = "3de0fe1a-0394-54a5-a65f-13fbc4cf68a6"; // done, EMP-0001 -> EMP-0006

    private static final String UNKNOWN = "00000000-0000-4000-8000-000000000000";

    private static final Map<String, String> TOKENS = new HashMap<>();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEvents events;

    // --- uwierzytelnienie i uprawnienia ---

    @Test
    void everyEndpointWithoutTokenIsUnauthorized() throws Exception {
        String body = "{}";
        mvc.perform(get("/api/v1/message-threads")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/message-threads/{id}", THREAD_FUROSEMIDE)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/message-threads").contentType(JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE).contentType(JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/message-threads/{id}/read", THREAD_FUROSEMIDE).contentType(JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/tasks").contentType(JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/handoff-notes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/handoff-notes").contentType(JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse", "lab-tech", "radiologist", "pharmacist", "registrar", "admin"})
    void everyRoleMayUseMessaging(String login) throws Exception {
        as(login, get("/api/v1/message-threads")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        as(login, post("/api/v1/message-threads").contentType(JSON)
                .content(threadBody(List.of(DOC_1), "Temat", null, "Treść", "normal"))).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lab-tech", "radiologist", "pharmacist", "registrar"})
    void rolesWithoutTaskPermissionsGet403(String login) throws Exception {
        as(login, get("/api/v1/tasks")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as(login, get("/api/v1/handoff-notes")).andExpect(status().isForbidden());
        as(login, post("/api/v1/tasks").contentType(JSON).content(taskBody("Zadanie", NURSE_6, null, "normal")))
                .andExpect(status().isForbidden());
        as(login, post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON).content("{\"status\":\"done\"}"))
                .andExpect(status().isForbidden());
        as(login, post("/api/v1/handoff-notes").contentType(JSON).content(handoffBody(WARD_INTERNAL, DOC_2, "[]")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminReadsTasksAndHandoffsButCannotWrite() throws Exception {
        as("admin", get("/api/v1/tasks")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(8)));
        as("admin", get("/api/v1/handoff-notes")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
        as("admin", post("/api/v1/tasks").contentType(JSON).content(taskBody("Zadanie", NURSE_6, null, "normal")))
                .andExpect(status().isForbidden());
        as("admin", post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON)
                .content("{\"status\":\"in_progress\"}")).andExpect(status().isForbidden());
        as("admin", post("/api/v1/handoff-notes").contentType(JSON).content(handoffBody(WARD_INTERNAL, DOC_2, "[]")))
                .andExpect(status().isForbidden());
    }

    // --- lista watkow ---

    @Test
    void threadListIsScopedToTheCurrentUserNewestFirst() throws Exception {
        as("EMP-0001", get("/api/v1/message-threads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[*].id", contains(THREAD_FUROSEMIDE, THREAD_CARDIO)));
        as("EMP-0006", get("/api/v1/message-threads")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains(THREAD_FUROSEMIDE)));
        as("EMP-0003", get("/api/v1/message-threads")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].subject", contains("Przygotowanie sali operacyjnej")));
    }

    @Test
    void threadListSerializationMatchesTypescriptModel() throws Exception {
        as("EMP-0001", get("/api/v1/message-threads"))
                .andExpect(jsonPath("$.items[0].id").value(THREAD_FUROSEMIDE))
                .andExpect(jsonPath("$.items[0].participantIds", containsInAnyOrder(DOC_1, NURSE_6)))
                .andExpect(jsonPath("$.items[0].subject").value("Pacjent Kowalski - dawkowanie furosemidu"))
                .andExpect(jsonPath("$.items[0].patientId").value(KOWALSKI))
                .andExpect(jsonPath("$.items[0].lastMessageAt").isString())
                .andExpect(jsonPath("$.items[0].unreadCount").isNumber())
                .andExpect(jsonPath("$.items[0].createdById").doesNotExist()) // mock: NULL -> pole pominiete
                .andExpect(jsonPath("$.items[1].id").value(THREAD_CARDIO));
        as("EMP-0005", get("/api/v1/message-threads"))
                .andExpect(jsonPath("$.items[0].patientId").doesNotExist()); // watek bez pacjenta
    }

    @Test
    void unreadCountMatchesTheReadCursorLogicForEveryMockUser() throws Exception {
        // jednoznaczne przypadki
        assertThat(unread("EMP-0001", THREAD_FUROSEMIDE)).isEqualTo(1); // cudza wiadomosc -1h, kursor -3h
        assertThat(unread("EMP-0006", THREAD_FUROSEMIDE)).isZero(); // kursor -1h, reszta wlasne lub starsze
        assertThat(unread("EMP-0004", THREAD_REHAB)).isEqualTo(2); // dwie wiadomosci EMP-0009 po kursorze -8h
        assertThat(unread("EMP-0009", THREAD_REHAB)).isZero();
        // wyrocznia SQL (te same reguly: cudze, sentAt > last_read_at; brak kursora = wszystkie cudze)
        for (int i = 1; i <= 10; i++) {
            String login = "EMP-%04d".formatted(i);
            UUID me = staffId(login);
            String body = as(login, get("/api/v1/message-threads?size=50")).andExpect(status().isOk()).andReturn()
                    .getResponse().getContentAsString(StandardCharsets.UTF_8);
            List<Map<String, Object>> items = JsonPath.read(body, "$.items");
            List<UUID> mine = jdbc.queryForList("select thread_id from thread_participant where staff_id = ?",
                    UUID.class, me);
            assertThat(items).as(login).hasSize(mine.size());
            for (Map<String, Object> item : items) {
                UUID threadId = UUID.fromString((String) item.get("id"));
                Integer expected = jdbc.queryForObject("""
                        select count(*) from message m
                        join thread_participant p on p.thread_id = m.thread_id and p.staff_id = ?
                        where m.thread_id = ? and m.sender_id <> ? and (p.last_read_at is null or m.sent_at > p.last_read_at)
                        """, Integer.class, me, threadId, me);
                assertThat(((Number) item.get("unreadCount")).intValue()).as(login + " " + threadId)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void threadListFiltersByPatientAndPaginates() throws Exception {
        as("EMP-0001", get("/api/v1/message-threads?patientId=" + KOWALSKI)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));
        as("EMP-0001", get("/api/v1/message-threads?patientId=" + SZYMANSKI)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.totalElements").value(0));
        as("EMP-0001", get("/api/v1/message-threads?size=1&page=1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains(THREAD_CARDIO)))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        as("EMP-0001", get("/api/v1/message-threads?sort=subject,asc")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(THREAD_CARDIO)); // "Konsultacja..." < "Pacjent..."
        as("EMP-0001", get("/api/v1/message-threads?sort=participantIds,asc"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void singleThreadIsViewerScopedAndGuarded() throws Exception {
        as("EMP-0001", get("/api/v1/message-threads/{id}", THREAD_FUROSEMIDE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(THREAD_FUROSEMIDE))
                .andExpect(jsonPath("$.unreadCount").value(1));
        as("EMP-0006", get("/api/v1/message-threads/{id}", THREAD_FUROSEMIDE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
        as("EMP-0002", get("/api/v1/message-threads/{id}", THREAD_FUROSEMIDE)).andExpect(status().isForbidden());
        as("EMP-0001", get("/api/v1/message-threads/{id}", UNKNOWN)).andExpect(status().isNotFound());
    }

    // --- wiadomosci watku ---

    @Test
    void messagesAreAscendingWithReadByIdsDerivedFromCursors() throws Exception {
        as("EMP-0001", get("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value("1ad95bbc-cfed-5dc0-bf58-4ac6587e14d0"))
                .andExpect(jsonPath("$[0].threadId").value(THREAD_FUROSEMIDE))
                .andExpect(jsonPath("$[0].senderId").value(NURSE_6))
                .andExpect(jsonPath("$[0].priority").value("high"))
                .andExpect(jsonPath("$[0].sentAt").isString())
                .andExpect(jsonPath("$[0].body").value(
                        "Panie doktorze, pacjent Kowalski zgłasza duszność przy zmianie pozycji. Bilans płynów za dobę dodatni."))
                // kursor EMP-0001 (-3h) >= sentAt (-4h): przeczytana przez obu
                .andExpect(jsonPath("$[0].readByIds", containsInAnyOrder(NURSE_6, DOC_1)))
                .andExpect(jsonPath("$[1].senderId").value(DOC_1))
                .andExpect(jsonPath("$[1].priority").value("normal"))
                // wiadomosc -1h: kursor EMP-0001 (-3h) starszy -> tylko nadawca
                .andExpect(jsonPath("$[2].id").value("f6aeb5c1-4a3b-5db2-b42e-b2c860674261"))
                .andExpect(jsonPath("$[2].readByIds", contains(NURSE_6)));
    }

    @Test
    void messagesRejectNonParticipantAndUnknownThread() throws Exception {
        as("EMP-0002", get("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as("admin", get("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE))
                .andExpect(status().isForbidden()); // admin: tylko wlasne watki
        as("EMP-0001", get("/api/v1/message-threads/{id}/messages", UNKNOWN)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        as("EMP-0001", get("/api/v1/message-threads/{id}/messages", "to-nie-uuid")).andExpect(status().isNotFound());
    }

    // --- tworzenie watku ---

    @Test
    void createThreadTakesCreatorFromTokenAndAddsFirstMessage() throws Exception {
        // `createdById` i inne pola kontekstu z ciala sa ignorowane
        String body = """
                {"participantIds":["%s"],"subject":"  Nowy temat  ","patientId":"%s","createdById":"%s",
                 "firstMessage":{"body":"Pierwsza wiadomość","priority":"high"}}
                """.formatted(NURSE_7, KOWALSKI, DOC_2);
        String response = as("EMP-0001", post("/api/v1/message-threads").contentType(JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/v1/message-threads/")))
                .andExpect(jsonPath("$.subject").value("Nowy temat"))
                .andExpect(jsonPath("$.patientId").value(KOWALSKI))
                .andExpect(jsonPath("$.createdById").value(DOC_1))
                .andExpect(jsonPath("$.participantIds", containsInAnyOrder(DOC_1, NURSE_7)))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.lastMessageAt").isString())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String threadId = JsonPath.read(response, "$.id");

        // tworca: wlasna wiadomosc przeczytana; adresat: 1 nieprzeczytana, wiadomosc o priorytecie high
        as("EMP-0001", get("/api/v1/message-threads/{id}", threadId)).andExpect(jsonPath("$.unreadCount").value(0));
        as("EMP-0007", get("/api/v1/message-threads/{id}", threadId)).andExpect(jsonPath("$.unreadCount").value(1));
        as("EMP-0007", get("/api/v1/message-threads/{id}/messages", threadId))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].senderId").value(DOC_1))
                .andExpect(jsonPath("$[0].priority").value("high"))
                .andExpect(jsonPath("$[0].body").value("Pierwsza wiadomość"))
                .andExpect(jsonPath("$[0].readByIds", contains(DOC_1)));
        as("EMP-0002", get("/api/v1/message-threads/{id}", threadId)).andExpect(status().isForbidden());

        assertThat(events.stream(MessageSent.class)).singleElement().satisfies(e -> {
            assertThat(e.threadId()).isEqualTo(UUID.fromString(threadId));
            assertThat(e.senderId()).isEqualTo(UUID.fromString(DOC_1));
            assertThat(e.recipientIds()).containsExactly(UUID.fromString(NURSE_7));
            assertThat(e.priority()).isEqualTo(Priority.HIGH);
            assertThat(e.patientId()).isEqualTo(UUID.fromString(KOWALSKI));
        });
    }

    @Test
    void createThreadMergesDuplicatesAndAlwaysIncludesCreator() throws Exception {
        String response = as("EMP-0001", post("/api/v1/message-threads").contentType(JSON)
                .content(threadBody(List.of(NURSE_6, NURSE_6, DOC_1), "Duplikaty", null, "Treść", "normal")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participantIds", containsInAnyOrder(DOC_1, NURSE_6)))
                .andExpect(jsonPath("$.patientId").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String threadId = JsonPath.read(response, "$.id");
        Integer rows = jdbc.queryForObject("select count(*) from thread_participant where thread_id = ?::uuid",
                Integer.class, threadId);
        assertThat(rows).isEqualTo(2);
        // brak innych uczestnikow: tylko tworca
        as("EMP-0001", post("/api/v1/message-threads").contentType(JSON)
                .content(threadBody(List.of(), "Notatka", null, "Do siebie", "normal"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.participantIds", contains(DOC_1)));
    }

    @Test
    void createThreadValidates() throws Exception {
        as("EMP-0001", post("/api/v1/message-threads").contentType(JSON)
                .content(threadBody(List.of(UNKNOWN), "Temat", null, "Treść", "normal")))
                .andExpect(status().isNotFound());
        as("EMP-0001", post("/api/v1/message-threads").contentType(JSON)
                .content(threadBody(List.of(NURSE_6), "Temat", UNKNOWN, "Treść", "normal")))
                .andExpect(status().isNotFound());
        as("EMP-0001", post("/api/v1/message-threads").contentType(JSON)
                .content(threadBody(List.of(NURSE_6), "   ", null, "Treść", "normal")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("subject"));
        as("EMP-0001", post("/api/v1/message-threads").contentType(JSON)
                .content(threadBody(List.of(NURSE_6), "Temat", null, "", "normal")))
                .andExpect(status().isUnprocessableContent());
        as("EMP-0001", post("/api/v1/message-threads").contentType(JSON)
                .content(threadBody(List.of(NURSE_6), "Temat", null, "Treść", "urgent")))
                .andExpect(status().isUnprocessableContent());
        as("EMP-0001", post("/api/v1/message-threads").contentType(JSON)
                .content("{\"participantIds\":[\"" + NURSE_6 + "\"],\"subject\":\"Temat\"}"))
                .andExpect(status().isUnprocessableContent());
        as("EMP-0001", post("/api/v1/message-threads").contentType(JSON)
                .content(threadBody(List.of(NURSE_6), "x".repeat(201), null, "Treść", "normal")))
                .andExpect(status().isUnprocessableContent());
    }

    // --- wysylanie wiadomosci ---

    @Test
    void sendMessageUsesTheTokenActorUpdatesThreadAndPublishesEvent() throws Exception {
        Instant before = Instant.now();
        // `senderId` w ciele (inny pracownik) jest ignorowany
        String body = "{\"body\":\"  Dawka podana.  \",\"priority\":\"critical\",\"senderId\":\"" + DOC_2 + "\"}";
        String response = as("EMP-0006", post("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE)
                .contentType(JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.threadId").value(THREAD_FUROSEMIDE))
                .andExpect(jsonPath("$.senderId").value(NURSE_6))
                .andExpect(jsonPath("$.body").value("Dawka podana."))
                .andExpect(jsonPath("$.priority").value("critical"))
                .andExpect(jsonPath("$.readByIds", contains(NURSE_6)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Instant sentAt = Instant.parse(JsonPath.read(response, "$.sentAt"));
        assertThat(sentAt).isBetween(before, Instant.now());

        // lastMessageAt przesuniety na sentAt, adresat widzi o jedna nieprzeczytana wiecej (1 -> 2)
        String thread = as("EMP-0001", get("/api/v1/message-threads/{id}", THREAD_FUROSEMIDE))
                .andExpect(jsonPath("$.unreadCount").value(2)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(Instant.parse(JsonPath.read(thread, "$.lastMessageAt"))).isEqualTo(sentAt);
        as("EMP-0001", get("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE))
                .andExpect(jsonPath("$", hasSize(4))).andExpect(jsonPath("$[3].body").value("Dawka podana."));

        assertThat(events.stream(MessageSent.class)).singleElement().satisfies(e -> {
            assertThat(e.messageId()).isEqualTo(UUID.fromString(JsonPath.read(response, "$.id")));
            assertThat(e.threadId()).isEqualTo(UUID.fromString(THREAD_FUROSEMIDE));
            assertThat(e.senderId()).isEqualTo(UUID.fromString(NURSE_6));
            assertThat(e.recipientIds()).containsExactly(UUID.fromString(DOC_1));
            assertThat(e.priority()).isEqualTo(Priority.CRITICAL);
            assertThat(e.sentAt()).isEqualTo(sentAt);
        });
    }

    @Test
    void sendMessageLeavesTheSendersOwnUnreadCountUntouched() throws Exception {
        int before = unread("EMP-0001", THREAD_FUROSEMIDE);
        as("EMP-0001", post("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE).contentType(JSON)
                .content("{\"body\":\"Kontrola jutro.\",\"priority\":\"normal\"}")).andExpect(status().isCreated());
        assertThat(unread("EMP-0001", THREAD_FUROSEMIDE)).isEqualTo(before); // wlasne nie liczą się
        assertThat(unread("EMP-0006", THREAD_FUROSEMIDE)).isEqualTo(1);
    }

    @Test
    void sendMessageGuardsAccessAndValidates() throws Exception {
        String ok = "{\"body\":\"Treść\",\"priority\":\"normal\"}";
        as("EMP-0002", post("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE).contentType(JSON)
                .content(ok)).andExpect(status().isForbidden());
        assertThat(events.stream(MessageSent.class)).isEmpty();
        as("EMP-0001", post("/api/v1/message-threads/{id}/messages", UNKNOWN).contentType(JSON).content(ok))
                .andExpect(status().isNotFound());
        as("EMP-0001", post("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE).contentType(JSON)
                .content("{\"body\":\"  \",\"priority\":\"normal\"}")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("body"));
        as("EMP-0001", post("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE).contentType(JSON)
                .content("{\"body\":\"Treść\",\"priority\":\"urgent\"}")).andExpect(status().isUnprocessableContent());
        as("EMP-0001", post("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE).contentType(JSON)
                .content("{\"body\":\"Treść\"}")).andExpect(status().isUnprocessableContent());
    }

    // --- oznaczanie jako przeczytane ---

    @Test
    void markReadMovesTheCursorAndIsIdempotent() throws Exception {
        assertThat(unread("EMP-0001", THREAD_FUROSEMIDE)).isEqualTo(1);
        Instant before = Instant.now();
        for (int i = 0; i < 2; i++) {
            as("EMP-0001", post("/api/v1/message-threads/{id}/read", THREAD_FUROSEMIDE).contentType(JSON)
                    .content("{}")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(THREAD_FUROSEMIDE))
                    .andExpect(jsonPath("$.unreadCount").value(0));
        }
        assertThat(unread("EMP-0001", THREAD_FUROSEMIDE)).isZero();
        Instant cursor = jdbc.queryForObject(
                "select last_read_at from thread_participant where thread_id = ?::uuid and staff_id = ?::uuid",
                OffsetDateTime.class, THREAD_FUROSEMIDE, DOC_1).toInstant();
        assertThat(cursor).isAfterOrEqualTo(before.minusSeconds(1));
        // pusty kursor innego uczestnika nie jest ruszany
        Instant other = jdbc.queryForObject(
                "select last_read_at from thread_participant where thread_id = ?::uuid and staff_id = ?::uuid",
                OffsetDateTime.class, THREAD_FUROSEMIDE, NURSE_6).toInstant();
        assertThat(other).isBefore(before.minusSeconds(60));
        // readByIds odzwierciedla nowy kursor: ostatnia wiadomosc przeczytana takze przez EMP-0001
        as("EMP-0006", get("/api/v1/message-threads/{id}/messages", THREAD_FUROSEMIDE))
                .andExpect(jsonPath("$[2].readByIds", containsInAnyOrder(NURSE_6, DOC_1)));
    }

    @Test
    void markReadWithoutBodyWorksAndGuardsAccess() throws Exception {
        as("EMP-0001", post("/api/v1/message-threads/{id}/read", THREAD_FUROSEMIDE)).andExpect(status().isOk());
        as("EMP-0002", post("/api/v1/message-threads/{id}/read", THREAD_FUROSEMIDE).contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());
        as("EMP-0001", post("/api/v1/message-threads/{id}/read", UNKNOWN).contentType(JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    // --- zadania: odczyt ---

    @Test
    void taskListReturnsAllMockTasksNewestFirst() throws Exception {
        as("EMP-0001", get("/api/v1/tasks")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].id").value("668f9ca2-059c-5a39-b807-466f9a601001")) // utworzone -2h
                .andExpect(jsonPath("$[7].id").value("21e60213-e036-57c6-b9d3-52d4203c6d78")); // -30h
        as("EMP-0006", get("/api/v1/tasks")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(8)));
    }

    @Test
    void taskSerializationMatchesTypescriptModel() throws Exception {
        as("EMP-0001", get("/api/v1/tasks?assignedToId=" + NURSE_8 + "&status=open"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(TASK_CLEXANE))
                .andExpect(jsonPath("$[0].title").value("Podać Clexane 40 mg sc o 20:00"))
                .andExpect(jsonPath("$[0].description").value("Pacjentka po zabiegu, profilaktyka przeciwzakrzepowa."))
                .andExpect(jsonPath("$[0].patientId").value("17a3dd05-d7d5-5211-ba70-0fc6e51cc476"))
                .andExpect(jsonPath("$[0].assignedToId").value(NURSE_8))
                .andExpect(jsonPath("$[0].createdById").value(DOC_3))
                .andExpect(jsonPath("$[0].createdAt").isString())
                .andExpect(jsonPath("$[0].dueAt").isString())
                .andExpect(jsonPath("$[0].priority").value("high"))
                .andExpect(jsonPath("$[0].status").value("open"))
                .andExpect(jsonPath("$[0].version").isNumber());
        as("EMP-0001", get("/api/v1/tasks?status=done")).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.id=='" + TASK_BLOOD + "')].description").isEmpty()) // brak opisu -> pole pominiete
                .andExpect(jsonPath("$[?(@.id=='21e60213-e036-57c6-b9d3-52d4203c6d78')].dueAt").isEmpty());
    }

    @Test
    void taskListFilters() throws Exception {
        as("EMP-0001", get("/api/v1/tasks?assignedToId=" + NURSE_6)).andExpect(jsonPath("$", hasSize(2)));
        as("EMP-0001", get("/api/v1/tasks?createdById=" + DOC_3)).andExpect(jsonPath("$", hasSize(2)));
        as("EMP-0001", get("/api/v1/tasks?status=open")).andExpect(jsonPath("$", hasSize(3)));
        as("EMP-0001", get("/api/v1/tasks?status=in_progress")).andExpect(jsonPath("$", hasSize(2)));
        as("EMP-0001", get("/api/v1/tasks?status=cancelled")).andExpect(jsonPath("$", hasSize(0)));
        as("EMP-0001", get("/api/v1/tasks?patientId=" + KOWALSKI)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(TASK_BLOOD));
        as("EMP-0001", get("/api/v1/tasks?patientId=" + MAZUR + "&status=done&assignedToId=" + DOC_1))
                .andExpect(jsonPath("$", hasSize(1)));
        as("EMP-0001", get("/api/v1/tasks?status=zrobione")).andExpect(status().isUnprocessableContent());
    }

    // --- zadania: tworzenie ---

    @Test
    void createTaskTakesCreatorFromTokenAndPublishesTaskAssigned() throws Exception {
        // `createdById` w ciele jest ignorowany, status startowy zawsze `open`
        String body = """
                {"title":"  Zmierzyć ciśnienie  ","description":"Co 2 h","patientId":"%s","assignedToId":"%s",
                 "createdById":"%s","dueAt":"2030-01-01T10:00:00Z","priority":"high","status":"open"}
                """.formatted(KOWALSKI, NURSE_6, DOC_2);
        Instant before = Instant.now();
        String response = as("EMP-0001", post("/api/v1/tasks").contentType(JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Zmierzyć ciśnienie"))
                .andExpect(jsonPath("$.description").value("Co 2 h"))
                .andExpect(jsonPath("$.patientId").value(KOWALSKI))
                .andExpect(jsonPath("$.assignedToId").value(NURSE_6))
                .andExpect(jsonPath("$.createdById").value(DOC_1))
                .andExpect(jsonPath("$.dueAt").value("2030-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.priority").value("high"))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(Instant.parse(JsonPath.read(response, "$.createdAt"))).isBetween(before.minusSeconds(1),
                Instant.now().plusSeconds(1));
        String id = JsonPath.read(response, "$.id");

        as("EMP-0006", get("/api/v1/tasks?assignedToId=" + NURSE_6)).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(id)); // najnowsze pierwsze

        assertThat(events.stream(TaskAssigned.class)).singleElement().satisfies(e -> {
            assertThat(e.taskId()).isEqualTo(UUID.fromString(id));
            assertThat(e.assignedToId()).isEqualTo(UUID.fromString(NURSE_6));
            assertThat(e.createdById()).isEqualTo(UUID.fromString(DOC_1));
            assertThat(e.patientId()).isEqualTo(UUID.fromString(KOWALSKI));
            assertThat(e.priority()).isEqualTo(Priority.HIGH);
            assertThat(e.title()).isEqualTo("Zmierzyć ciśnienie");
        });
    }

    @Test
    void nurseMayCreateTasksWithoutOptionalFields() throws Exception {
        as("EMP-0006", post("/api/v1/tasks").contentType(JSON).content(taskBody("Krótkie", DOC_1, null, "normal")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdById").value(NURSE_6))
                .andExpect(jsonPath("$.patientId").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.dueAt").doesNotExist());
    }

    @Test
    void createTaskValidates() throws Exception {
        as("EMP-0001", post("/api/v1/tasks").contentType(JSON).content(taskBody("Zadanie", UNKNOWN, null, "normal")))
                .andExpect(status().isNotFound());
        as("EMP-0001", post("/api/v1/tasks").contentType(JSON).content(taskBody("Zadanie", NURSE_6, UNKNOWN, "normal")))
                .andExpect(status().isNotFound());
        as("EMP-0001", post("/api/v1/tasks").contentType(JSON).content(taskBody("  ", NURSE_6, null, "normal")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        as("EMP-0001", post("/api/v1/tasks").contentType(JSON).content(taskBody("Zadanie", NURSE_6, null, "urgent")))
                .andExpect(status().isUnprocessableContent());
        as("EMP-0001", post("/api/v1/tasks").contentType(JSON)
                .content("{\"title\":\"Zadanie\",\"priority\":\"normal\"}")).andExpect(status().isUnprocessableContent());
        as("EMP-0001", post("/api/v1/tasks").contentType(JSON).content(
                "{\"title\":\"Zadanie\",\"assignedToId\":\"" + NURSE_6 + "\",\"priority\":\"normal\",\"status\":\"done\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("status"));
        assertThat(events.stream(TaskAssigned.class)).isEmpty();
    }

    // --- zadania: zmiana statusu ---

    @Test
    void assigneeWalksTheTaskThroughItsLifecycle() throws Exception {
        as("EMP-0008", post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON)
                .content("{\"status\":\"in_progress\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TASK_CLEXANE))
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andExpect(jsonPath("$.updatedById").value(NURSE_8));
        as("EMP-0008", post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON)
                .content("{\"status\":\"done\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"));
        as("EMP-0001", get("/api/v1/tasks?status=done")).andExpect(jsonPath("$", hasSize(4)));

        assertThat(events.stream(TaskStatusChanged.class)).hasSize(2).satisfiesExactly(
                first -> {
                    assertThat(first.previousStatus()).isEqualTo(TaskStatus.OPEN);
                    assertThat(first.status()).isEqualTo(TaskStatus.IN_PROGRESS);
                    assertThat(first.actorId()).isEqualTo(UUID.fromString(NURSE_8));
                    assertThat(first.createdById()).isEqualTo(UUID.fromString(DOC_3));
                },
                second -> assertThat(second.status()).isEqualTo(TaskStatus.DONE));
    }

    @Test
    void creatorMayChangeStatusButOutsidersMayNot() throws Exception {
        as("EMP-0002", post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON)
                .content("{\"status\":\"cancelled\"}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        as("EMP-0009", post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON)
                .content("{\"status\":\"in_progress\"}")).andExpect(status().isForbidden());
        assertThat(events.stream(TaskStatusChanged.class)).isEmpty();
        as("EMP-0003", post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON)
                .content("{\"status\":\"cancelled\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    static Stream<Arguments> transitions() {
        // wszystkie 16 par; dozwolone: open->{in_progress,done,cancelled}, in_progress->{done,cancelled}
        Map<String, List<String>> allowed = Map.of(
                "open", List.of("in_progress", "done", "cancelled"),
                "in_progress", List.of("done", "cancelled"),
                "done", List.of(),
                "cancelled", List.of());
        List<String> all = List.of("open", "in_progress", "done", "cancelled");
        return all.stream().flatMap(from -> all.stream()
                .map(to -> Arguments.of(from, to, allowed.get(from).contains(to))));
    }

    @ParameterizedTest(name = "{0} -> {1}: dozwolone={2}")
    @MethodSource("transitions")
    void statusTransitionsFollowTheStateMachine(String from, String to, boolean allowed) throws Exception {
        jdbc.update("update team_task set status = ? where id = ?::uuid", from, TASK_CLEXANE);
        ResultActions result = as("EMP-0008", post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON)
                .content("{\"status\":\"" + to + "\"}"));
        if (allowed) {
            result.andExpect(status().isOk()).andExpect(jsonPath("$.status").value(to));
        } else {
            result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
            assertThat(jdbc.queryForObject("select status from team_task where id = ?::uuid", String.class,
                    TASK_CLEXANE)).isEqualTo(from);
        }
    }

    @Test
    void statusUpdateValidatesAndDetectsStaleVersion() throws Exception {
        as("EMP-0008", post("/api/v1/tasks/{id}/status", UNKNOWN).contentType(JSON)
                .content("{\"status\":\"done\"}")).andExpect(status().isNotFound());
        as("EMP-0008", post("/api/v1/tasks/{id}/status", "to-nie-uuid").contentType(JSON)
                .content("{\"status\":\"done\"}")).andExpect(status().isNotFound());
        as("EMP-0008", post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON).content("{}"))
                .andExpect(status().isUnprocessableContent());
        as("EMP-0008", post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON)
                .content("{\"status\":\"zrobione\"}")).andExpect(status().isUnprocessableContent());
        as("EMP-0008", post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON)
                .content("{\"status\":\"in_progress\",\"version\":99}")).andExpect(status().isConflict());
        long version = jdbc.queryForObject("select version from team_task where id = ?::uuid", Long.class,
                TASK_CLEXANE);
        as("EMP-0008", post("/api/v1/tasks/{id}/status", TASK_CLEXANE).contentType(JSON)
                .content("{\"status\":\"in_progress\",\"version\":" + version + "}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version + 1));
    }

    // --- przekazanie zmiany ---

    @Test
    void handoffListReturnsMockNotesWithSbarPatientNotes() throws Exception {
        as("EMP-0001", get("/api/v1/handoff-notes")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].shiftDate").value(
                        jdbc.queryForObject("select (current_date - 1)::text", String.class))); // najstarsze na koncu
        as("EMP-0001", get("/api/v1/handoff-notes?wardId=" + WARD_INTERNAL)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("a186b9a2-556b-58c5-8ca1-087857b97dfa"))
                .andExpect(jsonPath("$[0].wardId").value(WARD_INTERNAL))
                .andExpect(jsonPath("$[0].shift").value("day"))
                .andExpect(jsonPath("$[0].fromId").value(NURSE_6))
                .andExpect(jsonPath("$[0].toId").value(NURSE_7))
                .andExpect(jsonPath("$[0].createdAt").isString())
                .andExpect(jsonPath("$[0].generalNotes").value(
                        "Oddział pełny, brak wolnych łóżek. Zwrócić uwagę na pacjentów z ryzykiem upadku."))
                .andExpect(jsonPath("$[0].patientNotes", hasSize(2)))
                .andExpect(jsonPath("$[0].patientNotes[0].patientId").value(MAZUR)) // rosnaco po patientId
                .andExpect(jsonPath("$[0].patientNotes[0].situation").value(
                        "Pacjentka z przewlekłą chorobą nerek, zaburzenia elektrolitowe."))
                .andExpect(jsonPath("$[0].patientNotes[0].background").isString())
                .andExpect(jsonPath("$[0].patientNotes[0].assessment").isString())
                .andExpect(jsonPath("$[0].patientNotes[0].recommendation").isString())
                .andExpect(jsonPath("$[0].patientNotes[1].patientId").value(KOWALSKI));
        as("EMP-0001", get("/api/v1/handoff-notes?wardId=" + UNKNOWN)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void handoffListFiltersByShiftDate() throws Exception {
        String yesterday = jdbc.queryForObject("select (current_date - 1)::text", String.class);
        as("EMP-0001", get("/api/v1/handoff-notes?shiftDate=" + yesterday)).andExpect(jsonPath("$", hasSize(1)));
        as("EMP-0001", get("/api/v1/handoff-notes?shiftDate=2001-01-01")).andExpect(jsonPath("$", hasSize(0)));
        as("EMP-0001", get("/api/v1/handoff-notes?shiftDate=jutro")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void createHandoffTakesFromIdFromTokenAndStoresPatientNotes() throws Exception {
        String notes = "[" + sbar(KOWALSKI) + "," + sbar(SZYMANSKI) + "]";
        // `fromId` w ciele (inny pracownik) jest ignorowany
        String body = """
                {"wardId":"%s","shiftDate":"2030-05-04","shift":"night","fromId":"%s","toId":"%s",
                 "generalNotes":"Spokojna noc","patientNotes":%s}
                """.formatted(WARD_CARDIO, DOC_2, NURSE_7, notes);
        String response = as("EMP-0006", post("/api/v1/handoff-notes").contentType(JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wardId").value(WARD_CARDIO))
                .andExpect(jsonPath("$.shiftDate").value("2030-05-04"))
                .andExpect(jsonPath("$.shift").value("night"))
                .andExpect(jsonPath("$.fromId").value(NURSE_6))
                .andExpect(jsonPath("$.toId").value(NURSE_7))
                .andExpect(jsonPath("$.generalNotes").value("Spokojna noc"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.patientNotes", hasSize(2)))
                .andExpect(jsonPath("$.patientNotes[*].patientId", containsInAnyOrder(KOWALSKI, SZYMANSKI)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(response, "$.id");

        as("EMP-0001", get("/api/v1/handoff-notes?shiftDate=2030-05-04")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].patientNotes", hasSize(2)));
        as("EMP-0001", get("/api/v1/handoff-notes")).andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].id").value(id)); // najnowsza data zmiany pierwsza
    }

    @Test
    void createHandoffWithoutGeneralNotesOrPatients() throws Exception {
        as("EMP-0001", post("/api/v1/handoff-notes").contentType(JSON).content(handoffBody(WARD_INTERNAL, NURSE_6, "[]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generalNotes").doesNotExist())
                .andExpect(jsonPath("$.patientNotes", hasSize(0)));
    }

    @Test
    void createHandoffValidatesReferencesAndContent() throws Exception {
        // nieistniejacy oddzial, odbierajacy i pacjent -> zbiorczo 422 z polami
        as("EMP-0001", post("/api/v1/handoff-notes").contentType(JSON)
                .content(handoffBody(UNKNOWN, UNKNOWN, "[" + sbar(UNKNOWN) + "]")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("wardId", "toId", "patientNotes[0].patientId")));
        // powtorzony pacjent
        as("EMP-0001", post("/api/v1/handoff-notes").contentType(JSON)
                .content(handoffBody(WARD_INTERNAL, NURSE_6, "[" + sbar(KOWALSKI) + "," + sbar(KOWALSKI) + "]")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("patientNotes[1].patientId"));
        // pusta sekcja SBAR, brak zmiany, zla zmiana
        as("EMP-0001", post("/api/v1/handoff-notes").contentType(JSON).content(handoffBody(WARD_INTERNAL, NURSE_6,
                "[{\"patientId\":\"" + KOWALSKI + "\",\"situation\":\" \",\"background\":\"b\",\"assessment\":\"a\",\"recommendation\":\"r\"}]")))
                .andExpect(status().isUnprocessableContent());
        as("EMP-0001", post("/api/v1/handoff-notes").contentType(JSON)
                .content(handoffBody(WARD_INTERNAL, NURSE_6, "[]").replace("\"day\"", "\"evening\"")))
                .andExpect(status().isUnprocessableContent());
        as("EMP-0001", post("/api/v1/handoff-notes").contentType(JSON)
                .content("{\"wardId\":\"" + WARD_INTERNAL + "\",\"toId\":\"" + NURSE_6 + "\"}"))
                .andExpect(status().isUnprocessableContent());
        // nic nie zapisano
        as("EMP-0001", get("/api/v1/handoff-notes")).andExpect(jsonPath("$", hasSize(3)));
    }

    // --- pomocnicze ---

    private int unread(String login, String threadId) throws Exception {
        String body = as(login, get("/api/v1/message-threads/{id}", threadId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.unreadCount")).intValue();
    }

    private UUID staffId(String employeeId) {
        return jdbc.queryForObject("select id from staff_member where employee_id = ?", UUID.class, employeeId);
    }

    private static String threadBody(List<String> participants, String subject, String patientId, String firstMessage,
            String priority) {
        String ids = participants.stream().map(p -> "\"" + p + "\"").reduce((a, b) -> a + "," + b).orElse("");
        return "{\"participantIds\":[" + ids + "],\"subject\":\"" + subject + "\""
                + (patientId == null ? "" : ",\"patientId\":\"" + patientId + "\"")
                + ",\"firstMessage\":{\"body\":\"" + firstMessage + "\",\"priority\":\"" + priority + "\"}}";
    }

    private static String taskBody(String title, String assignedToId, String patientId, String priority) {
        return "{\"title\":\"" + title + "\",\"assignedToId\":\"" + assignedToId + "\",\"priority\":\"" + priority
                + "\"" + (patientId == null ? "" : ",\"patientId\":\"" + patientId + "\"") + "}";
    }

    private static String handoffBody(String wardId, String toId, String patientNotesJson) {
        return "{\"wardId\":\"" + wardId + "\",\"shiftDate\":\"2030-01-01\",\"shift\":\"day\",\"toId\":\"" + toId
                + "\",\"patientNotes\":" + patientNotesJson + "}";
    }

    private static String sbar(String patientId) {
        return "{\"patientId\":\"" + patientId + "\",\"situation\":\"S\",\"background\":\"B\",\"assessment\":\"A\","
                + "\"recommendation\":\"R\"}";
    }

    private ResultActions as(String login, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(login)));
    }

    /** Pracownicy mock (`EMP-xxxx`): haslo `HisDemo2026!`; konta demo: login = haslo. */
    private String bearer(String login) throws Exception {
        String token = TOKENS.get(login);
        if (token == null) {
            String password = login.startsWith("EMP-") ? "HisDemo2026!" : login;
            String response = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                    .content("{\"employeeId\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            token = JsonPath.read(response, "$.accessToken");
            TOKENS.put(login, token);
        }
        return "Bearer " + token;
    }
}
