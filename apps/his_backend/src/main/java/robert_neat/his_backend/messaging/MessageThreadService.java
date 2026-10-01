package robert_neat.his_backend.messaging;

import static robert_neat.his_backend.messaging.MessagingSupport.actor;
import static robert_neat.his_backend.messaging.MessagingSupport.parse;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.PageResponse;
import robert_neat.his_backend.common.api.SortWhitelist;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.messaging.events.MessageSent;
import robert_neat.his_backend.messaging.events.ThreadMarkedRead;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.staff.StaffMemberRepository;

/**
 * Watki i wiadomosci. Aktor zawsze z tokenu (nadawca/tworca z zadania nie istnieje lub jest ignorowany); dostep do
 * watku ma wylacznie jego uczestnik (nie-uczestnik = 403, brak watku = 404). `unreadCount` i `readByIds` liczy backend
 * z kursorow {@link ThreadParticipant#getLastReadAt()} dla biezacego uzytkownika. Kazda wyslana wiadomosc (takze
 * pierwsza w nowym watku) publikuje {@link MessageSent} (konsument: RealtimePublisher). DTO mapowane w transakcji.
 */
@Service
@Transactional(readOnly = true)
public class MessageThreadService {

    private static final SortWhitelist SORT = SortWhitelist.of(Sort.by(Sort.Direction.DESC, "lastMessageAt"),
            "lastMessageAt", "subject", "createdAt");

    private final MessageThreadRepository threads;
    private final ThreadParticipantRepository participants;
    private final MessageRepository messages;
    private final StaffMemberRepository staff;
    private final PatientRepository patients;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;

    MessageThreadService(MessageThreadRepository threads, ThreadParticipantRepository participants,
            MessageRepository messages, StaffMemberRepository staff, PatientRepository patients,
            CurrentActor currentActor, ApplicationEventPublisher events) {
        this.threads = threads;
        this.participants = participants;
        this.messages = messages;
        this.staff = staff;
        this.patients = patients;
        this.currentActor = currentActor;
        this.events = events;
    }

    // --- odczyt ---

    /** Watki biezacego uzytkownika (sort domyslny `lastMessageAt,desc`), z jego `unreadCount`. */
    public PageResponse<MessageThreadResponse> list(UUID patientId, Pageable requested) {
        UUID me = actor(currentActor);
        Pageable pageable = requested;
        if (requested.isPaged()) {
            Pageable mapped = SORT.apply(requested);
            pageable = PageRequest.of(mapped.getPageNumber(), mapped.getPageSize(),
                    mapped.getSort().and(Sort.by("id")));
        }
        Page<MessageThread> page = threads.findVisibleTo(me, patientId, pageable);
        List<UUID> ids = page.getContent().stream().map(MessageThread::getId).toList();
        Map<UUID, List<ThreadParticipant>> byThread = participantsOf(ids);
        Map<UUID, Long> unread = unreadOf(me, ids);
        return PageResponse.from(page, t -> MessagingMapper.toResponse(t,
                byThread.getOrDefault(t.getId(), List.of()), unread.getOrDefault(t.getId(), 0L)));
    }

    public MessageThreadResponse get(String threadId) {
        UUID me = actor(currentActor);
        MessageThread thread = requireAccessible(threadId, me);
        return toResponse(thread, me);
    }

    /** Wiadomosci rosnaco po `sentAt` (z `readByIds`). 404 brak watku, 403 nie-uczestnik. */
    public List<MessageResponse> messages(String threadId) {
        UUID me = actor(currentActor);
        MessageThread thread = requireAccessible(threadId, me);
        List<ThreadParticipant> members = participants.findByIdThreadIdIn(List.of(thread.getId()));
        return messages.findByThreadIdOrderBySentAtAscIdAsc(thread.getId()).stream()
                .map(m -> MessagingMapper.toResponse(m, members)).toList();
    }

    // --- zapis ---

    /** 404 (uczestnik/pacjent), 422 (walidacja). Tworca dolaczany jako uczestnik; pierwsza wiadomosc w tej samej transakcji. */
    @Transactional
    public MessageThreadResponse create(ThreadCreateRequest request) {
        UUID me = actor(currentActor);
        Set<UUID> memberIds = new LinkedHashSet<>();
        memberIds.add(me);
        memberIds.addAll(request.participantIds());
        for (UUID id : memberIds) {
            if (!staff.existsById(id)) {
                throw NotFoundException.of("Pracownik", id);
            }
        }
        if (request.patientId() != null && !patients.existsById(request.patientId())) {
            throw NotFoundException.of("Pacjent", request.patientId());
        }
        Instant now = Instant.now();
        MessageThread thread = threads.saveAndFlush(
                MessageThread.start(request.subject().trim(), request.patientId(), me, now));
        List<ThreadParticipant> members = memberIds.stream()
                // tworca widzi wlasna wiadomosc jako przeczytana; pozostali maja kursor pusty (wszystko nowe)
                .map(id -> ThreadParticipant.join(thread.getId(), id, now, id.equals(me) ? now : null)).toList();
        participants.saveAll(members);
        send(thread, me, request.firstMessage().body(), request.firstMessage().priority(), members, now);
        return MessagingMapper.toResponse(thread, members, 0);
    }

    /** 404, 403 (nie-uczestnik), 422. Aktualizuje `lastMessageAt`, publikuje {@link MessageSent}. */
    @Transactional
    public MessageResponse send(String threadId, MessageSendRequest request) {
        UUID me = actor(currentActor);
        MessageThread thread = requireAccessible(threadId, me);
        List<ThreadParticipant> members = participants.findByIdThreadIdIn(List.of(thread.getId()));
        return send(thread, me, request.body(), request.priority(), members, Instant.now());
    }

    /** Idempotentne: ustawia kursor odczytu uzytkownika na teraz (nigdy wstecz); odpowiedz z `unreadCount: 0`. */
    @Transactional
    public MessageThreadResponse markRead(String threadId) {
        UUID me = actor(currentActor);
        MessageThread thread = requireAccessible(threadId, me);
        List<ThreadParticipant> members = participants.findByIdThreadIdIn(List.of(thread.getId()));
        members.stream().filter(p -> p.staffId().equals(me)).findFirst()
                .ifPresent(p -> p.markReadUpTo(Instant.now()));
        participants.flush();
        MessageThreadResponse response = MessagingMapper.toResponse(thread, members, 0);
        events.publishEvent(new ThreadMarkedRead(me, response));
        return response;
    }

    // --- pomocnicze ---

    private MessageResponse send(MessageThread thread, UUID sender, String body, Priority priority,
            List<ThreadParticipant> members, Instant at) {
        Message saved = messages.saveAndFlush(Message.send(thread.getId(), sender, at, body.trim(), priority));
        thread.touch(at);
        threads.saveAndFlush(thread);
        List<UUID> recipients = members.stream().map(ThreadParticipant::staffId).filter(id -> !id.equals(sender))
                .toList();
        events.publishEvent(new MessageSent(saved.getId(), thread.getId(), thread.getSubject(),
                thread.getPatientId(), sender, priority, at, recipients));
        return MessagingMapper.toResponse(saved, members);
    }

    private MessageThreadResponse toResponse(MessageThread thread, UUID me) {
        List<ThreadParticipant> members = participants.findByIdThreadIdIn(List.of(thread.getId()));
        long unread = unreadOf(me, List.of(thread.getId())).getOrDefault(thread.getId(), 0L);
        return MessagingMapper.toResponse(thread, members, unread);
    }

    private Map<UUID, List<ThreadParticipant>> participantsOf(List<UUID> threadIds) {
        if (threadIds.isEmpty()) {
            return Map.of();
        }
        return participants.findByIdThreadIdIn(threadIds).stream()
                .collect(Collectors.groupingBy(ThreadParticipant::threadId));
    }

    private Map<UUID, Long> unreadOf(UUID me, List<UUID> threadIds) {
        if (threadIds.isEmpty()) {
            return Map.of();
        }
        return messages.countUnread(me, threadIds).stream()
                .collect(Collectors.toMap(ThreadUnread::threadId, ThreadUnread::count));
    }

    /** 404, gdy watek nie istnieje; 403, gdy `me` nie jest jego uczestnikiem. */
    private MessageThread requireAccessible(String threadId, UUID me) {
        UUID id = parse(threadId);
        MessageThread thread = (id == null ? java.util.Optional.<MessageThread>empty() : threads.findById(id))
                .orElseThrow(() -> NotFoundException.of("Watek", threadId));
        if (!participants.existsById(new ThreadParticipantId(thread.getId(), me))) {
            throw new ForbiddenException("Brak dostepu do watku - nie jestes jego uczestnikiem");
        }
        return thread;
    }
}
