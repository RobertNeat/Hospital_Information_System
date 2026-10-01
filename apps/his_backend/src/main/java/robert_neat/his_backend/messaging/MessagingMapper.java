package robert_neat.his_backend.messaging;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Reczne mapowanie encja -> DTO; kolekcje leniwe, wiec w transakcji serwisu (open-in-view wylaczone). */
final class MessagingMapper {

    private static final Comparator<ThreadParticipant> PARTICIPANT_ORDER = Comparator
            .comparing(ThreadParticipant::getJoinedAt).thenComparing(ThreadParticipant::staffId);

    private MessagingMapper() {
    }

    static MessageThreadResponse toResponse(MessageThread t, List<ThreadParticipant> participants, long unreadCount) {
        return new MessageThreadResponse(t.getId(), participantIds(participants), t.getSubject(), t.getPatientId(),
                t.getCreatedById(), t.getLastMessageAt(), unreadCount);
    }

    /** `readByIds`: nadawca (zawsze) + uczestnicy, ktorych kursor `lastReadAt` >= `sentAt`. */
    static MessageResponse toResponse(Message m, List<ThreadParticipant> participants) {
        Set<UUID> readBy = new LinkedHashSet<>();
        readBy.add(m.getSenderId());
        participants.stream().sorted(PARTICIPANT_ORDER)
                .filter(p -> p.getLastReadAt() != null && !p.getLastReadAt().isBefore(m.getSentAt()))
                .forEach(p -> readBy.add(p.staffId()));
        return new MessageResponse(m.getId(), m.getThreadId(), m.getSenderId(), m.getSentAt(), m.getBody(),
                m.getPriority(), List.copyOf(readBy));
    }

    static List<UUID> participantIds(List<ThreadParticipant> participants) {
        return participants.stream().sorted(PARTICIPANT_ORDER).map(ThreadParticipant::staffId).toList();
    }

    static TeamTaskResponse toResponse(TeamTask t) {
        return new TeamTaskResponse(t.getId(), t.getTitle(), t.getDescription(), t.getPatientId(),
                t.getAssignedToId(), t.getCreatedById(), t.getCreatedAt(), t.getDueAt(), t.getPriority(),
                t.getStatus(), t.getUpdatedAt(), t.getUpdatedById(), t.getVersion());
    }

    static HandoffNoteResponse toResponse(HandoffNote n) {
        return new HandoffNoteResponse(n.getId(), n.getWardId(), n.getShiftDate(), n.getShift(), n.getFromId(),
                n.getToId(), n.getCreatedAt(), n.getGeneralNotes(),
                n.getPatientNotes().stream()
                        .map(p -> new HandoffPatientNoteDto(p.getPatientId(), p.getSituation(), p.getBackground(),
                                p.getAssessment(), p.getRecommendation()))
                        .toList());
    }
}
