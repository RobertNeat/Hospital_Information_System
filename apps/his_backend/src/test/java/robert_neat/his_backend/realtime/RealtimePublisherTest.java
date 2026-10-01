package robert_neat.his_backend.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import robert_neat.his_backend.alert.AlertResponse;
import robert_neat.his_backend.alert.AlertSeverity;
import robert_neat.his_backend.alert.AlertTarget;
import robert_neat.his_backend.alert.AlertTargetKind;
import robert_neat.his_backend.alert.AlertType;
import robert_neat.his_backend.alert.events.AlertAcknowledged;
import robert_neat.his_backend.alert.events.AlertCreated;
import robert_neat.his_backend.messaging.MessageResponse;
import robert_neat.his_backend.messaging.MessageThreadResponse;
import robert_neat.his_backend.messaging.MessagingPushProjection;
import robert_neat.his_backend.messaging.Priority;
import robert_neat.his_backend.messaging.events.MessageSent;
import robert_neat.his_backend.messaging.events.ThreadMarkedRead;

/** Mapowanie zdarzen domenowych na tematy STOMP (API.md, par. 10) - bez brokera. */
class RealtimePublisherTest {

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final MessagingPushProjection projection = mock(MessagingPushProjection.class);
    private final RealtimePublisher publisher = new RealtimePublisher(template, projection);

    private final UUID alertId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID wardId = UUID.randomUUID();
    private final UUID doctor = UUID.randomUUID();
    private final UUID orderer = UUID.randomUUID();

    private AlertCreated alert(UUID ward) {
        return new AlertCreated(alertId, AlertType.CRITICAL_RESULT, AlertSeverity.CRITICAL, patientId, "Krytyczny wynik",
                Instant.parse("2026-05-01T10:00:00Z"), new AlertTarget(AlertTargetKind.LAB_RESULT, UUID.randomUUID(),
                        patientId), ward, List.of(orderer, doctor));
    }

    @Test
    void alertGoesToEveryRecipientAndToWardTopic() {
        publisher.on(alert(wardId));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSendToUser(eq(orderer.toString()), eq("/queue/alerts"), payload.capture());
        verify(template).convertAndSendToUser(eq(doctor.toString()), eq("/queue/alerts"), any(Object.class));
        verify(template).convertAndSend(eq("/topic/alerts/" + wardId), any(Object.class));
        assertThat(payload.getValue()).isInstanceOfSatisfying(AlertResponse.class, a -> {
            assertThat(a.id()).isEqualTo(alertId);
            assertThat(a.acknowledged()).isFalse();
            assertThat(a.acknowledgedById()).isNull();
            assertThat(a.acknowledgedAt()).isNull();
            assertThat(a.target().kind()).isEqualTo(AlertTargetKind.LAB_RESULT);
        });
    }

    @Test
    void alertWithoutWardSkipsWardTopic() {
        publisher.on(alert(null));

        verify(template, never()).convertAndSend(any(String.class), any(Object.class));
        verify(template).convertAndSendToUser(eq(orderer.toString()), eq("/queue/alerts"), any(Object.class));
    }

    @Test
    void acknowledgementIsSentOnlyToThatUser() {
        AlertResponse acked = new AlertResponse(alertId, AlertType.TASK, AlertSeverity.INFO, null, "m", Instant.now(),
                true, doctor, Instant.now(), null);

        publisher.on(new AlertAcknowledged(doctor, acked));

        verify(template).convertAndSendToUser(doctor.toString(), "/queue/alerts", acked);
        verifyNoInteractions(projection);
    }

    @Test
    void messageGoesToRecipientsAndThreadIsViewerScopedForAllParticipants() {
        UUID messageId = UUID.randomUUID();
        UUID threadId = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        MessageResponse message = new MessageResponse(messageId, threadId, sender, Instant.now(), "tresc",
                Priority.NORMAL, List.of(sender));
        when(projection.message(messageId)).thenReturn(Optional.of(message));
        MessageThreadResponse forDoctor = new MessageThreadResponse(threadId, List.of(sender, doctor), "t", null, sender,
                Instant.now(), 1);
        MessageThreadResponse forSender = new MessageThreadResponse(threadId, List.of(sender, doctor), "t", null, sender,
                Instant.now(), 0);
        when(projection.thread(threadId, doctor)).thenReturn(Optional.of(forDoctor));
        when(projection.thread(threadId, sender)).thenReturn(Optional.of(forSender));

        publisher.on(new MessageSent(messageId, threadId, "t", null, sender, Priority.NORMAL, Instant.now(),
                List.of(doctor)));

        verify(template).convertAndSendToUser(doctor.toString(), "/queue/messages", message);
        verify(template, never()).convertAndSendToUser(eq(sender.toString()), eq("/queue/messages"), any(Object.class));
        verify(template).convertAndSendToUser(doctor.toString(), "/queue/threads", forDoctor);
        verify(template).convertAndSendToUser(sender.toString(), "/queue/threads", forSender);
    }

    @Test
    void markedReadSyncsOnlyTheReadersSessions() {
        MessageThreadResponse thread = new MessageThreadResponse(UUID.randomUUID(), List.of(doctor), "t", null, doctor,
                Instant.now(), 0);

        publisher.on(new ThreadMarkedRead(doctor, thread));

        verify(template).convertAndSendToUser(doctor.toString(), "/queue/threads", thread);
    }

    @Test
    void failingBrokerDoesNotPropagate() {
        org.mockito.Mockito.doThrow(new IllegalStateException("broker down")).when(template)
                .convertAndSendToUser(any(String.class), any(String.class), any(Object.class));

        publisher.on(alert(wardId));

        // adresaci zawiodli, ale temat oddzialu nadal obsluzony (blad jednego pushu nie przerywa pozostalych)
        verify(template).convertAndSend(eq("/topic/alerts/" + wardId), any(Object.class));
    }
}
