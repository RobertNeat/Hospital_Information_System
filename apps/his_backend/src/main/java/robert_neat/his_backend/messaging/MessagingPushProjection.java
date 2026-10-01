package robert_neat.his_backend.messaging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projekcje DTO dla warstwy push STOMP, budowane po commit zdarzenia zrodlowego (poza jego transakcja, stad
 * `REQUIRES_NEW`; tylko odczyt). Aktor nie pochodzi z sesji HTTP, wiec widz (`viewerId`) jest podawany jawnie.
 */
@Service
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
public class MessagingPushProjection {

    private final MessageRepository messages;
    private final MessageThreadRepository threads;
    private final ThreadParticipantRepository participants;
    private final TeamTaskRepository tasks;

    MessagingPushProjection(MessageRepository messages, MessageThreadRepository threads,
            ThreadParticipantRepository participants, TeamTaskRepository tasks) {
        this.messages = messages;
        this.threads = threads;
        this.participants = participants;
        this.tasks = tasks;
    }

    public Optional<MessageResponse> message(UUID messageId) {
        return messages.findById(messageId).map(m -> MessagingMapper.toResponse(m,
                participants.findByIdThreadIdIn(List.of(m.getThreadId()))));
    }

    /** Watek z `unreadCount` liczonym dla `viewerId` (`@viewerScoped`). */
    public Optional<MessageThreadResponse> thread(UUID threadId, UUID viewerId) {
        return threads.findById(threadId).map(t -> {
            long unread = messages.countUnread(viewerId, List.of(threadId)).stream()
                    .filter(u -> u.threadId().equals(threadId)).mapToLong(ThreadUnread::count).sum();
            return MessagingMapper.toResponse(t, participants.findByIdThreadIdIn(List.of(threadId)), unread);
        });
    }

    public Optional<TeamTaskResponse> task(UUID taskId) {
        return tasks.findById(taskId).map(MessagingMapper::toResponse);
    }
}
