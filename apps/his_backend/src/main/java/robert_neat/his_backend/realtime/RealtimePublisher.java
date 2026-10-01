package robert_neat.his_backend.realtime;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import robert_neat.his_backend.alert.AlertResponse;
import robert_neat.his_backend.alert.AlertTarget;
import robert_neat.his_backend.alert.AlertTargetDto;
import robert_neat.his_backend.alert.events.AlertAcknowledged;
import robert_neat.his_backend.alert.events.AlertCreated;
import robert_neat.his_backend.messaging.MessagingPushProjection;
import robert_neat.his_backend.messaging.events.MessageSent;
import robert_neat.his_backend.messaging.events.TaskAssigned;
import robert_neat.his_backend.messaging.events.TaskStatusChanged;
import robert_neat.his_backend.messaging.events.ThreadMarkedRead;

/**
 * Push STOMP wg API.md, par. 10: zdarzenia domenowe -> tematy, WYLACZNIE po commit transakcji zrodlowej
 * (`AFTER_COMMIT`; rollback nic nie wysyla, a klient po odebraniu pushu zastaje juz zapisane dane). Payloady to
 * te same DTO co w REST (serializacja Jackson jak REST, bez kopert). Blad pushu jest logowany i nie wplywa na
 * wynik zapisu, ktory juz zostal zatwierdzony.
 */
@Component
class RealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    private final SimpMessagingTemplate template;
    private final MessagingPushProjection projection;

    RealtimePublisher(SimpMessagingTemplate template, MessagingPushProjection projection) {
        this.template = template;
        this.projection = projection;
    }

    /** `/user/queue/alerts` dla kazdego adresata (`acknowledged:false`) i `/topic/alerts/{wardId}`, gdy znany. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AlertCreated e) {
        AlertResponse payload = alertPayload(e);
        for (UUID recipient : e.recipientIds()) {
            toUser(recipient, StompDestinations.QUEUE_ALERTS, payload);
        }
        if (e.wardId() != null) {
            send(StompDestinations.wardAlerts(e.wardId()), payload);
        }
    }

    /** Zmiana stanu potwierdzenia: tylko sesje tego uzytkownika. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AlertAcknowledged e) {
        toUser(e.staffId(), StompDestinations.QUEUE_ALERTS, e.alert());
    }

    /** `/user/queue/messages` dla adresatow (bez nadawcy) + `/user/queue/threads` (viewer-scoped) dla wszystkich uczestnikow. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MessageSent e) {
        guarded(() -> projection.message(e.messageId())
                .ifPresent(m -> e.recipientIds().forEach(r -> toUser(r, StompDestinations.QUEUE_MESSAGES, m))));
        Set<UUID> participants = new LinkedHashSet<>(e.recipientIds());
        participants.add(e.senderId());
        for (UUID viewer : participants) {
            guarded(() -> projection.thread(e.threadId(), viewer)
                    .ifPresent(t -> toUser(viewer, StompDestinations.QUEUE_THREADS, t)));
        }
    }

    /** Po `read`: synchronizacja innych sesji tego uzytkownika. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ThreadMarkedRead e) {
        toUser(e.staffId(), StompDestinations.QUEUE_THREADS, e.thread());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(TaskAssigned e) {
        pushTask(e.taskId(), e.assignedToId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(TaskStatusChanged e) {
        pushTask(e.taskId(), e.assignedToId());
    }

    private void pushTask(UUID taskId, UUID assignedToId) {
        guarded(() -> projection.task(taskId).ifPresent(t -> toUser(assignedToId, StompDestinations.QUEUE_TASKS, t)));
    }

    /** Alert dla adresata (`ClinicalAlert`): `acknowledged:false`, bez danych `acknowledgedBy*`. */
    static AlertResponse alertPayload(AlertCreated e) {
        AlertTarget t = e.target();
        AlertTargetDto target = t == null || t.kind() == null ? null : new AlertTargetDto(t.kind(), t.id(), t.patientId());
        return new AlertResponse(e.alertId(), e.type(), e.severity(), e.patientId(), e.message(), e.createdAt(), false,
                null, null, target);
    }

    private void toUser(UUID staffId, String queue, Object payload) {
        guarded(() -> template.convertAndSendToUser(staffId.toString(), queue, payload));
    }

    private void send(String destination, Object payload) {
        guarded(() -> template.convertAndSend(destination, payload));
    }

    private static void guarded(Runnable push) {
        try {
            push.run();
        } catch (RuntimeException ex) {
            log.warn("Push STOMP nie powiodl sie (zapis juz zatwierdzony): {}", ex.toString());
        }
    }
}
