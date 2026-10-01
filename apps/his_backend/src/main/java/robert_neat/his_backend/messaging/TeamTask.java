package robert_neat.his_backend.messaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import robert_neat.his_backend.common.persistence.VersionedEntity;

/**
 * Zadanie zespolu (`team_task`). `createdAt`/`createdById`/`updatedAt`/`updatedById` wypelnia audyt (aktor z tokenu).
 * Status zmienia wylacznie {@link #transitionTo}; poprawnosc przejscia sprawdza serwis ({@link TeamTaskStateMachine}).
 */
@Entity
@Table(name = "team_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamTask extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "assigned_to_id", nullable = false)
    private UUID assignedToId;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "priority", nullable = false, length = 10)
    private Priority priority;

    @Column(name = "status", nullable = false, length = 15)
    private TaskStatus status;

    /** Nowe zadanie w stanie `open`. */
    static TeamTask open(String title, String description, UUID patientId, UUID assignedToId, Instant dueAt,
            Priority priority) {
        TeamTask task = new TeamTask();
        task.title = title;
        task.description = description;
        task.patientId = patientId;
        task.assignedToId = assignedToId;
        task.dueAt = dueAt;
        task.priority = priority;
        task.status = TaskStatus.OPEN;
        return task;
    }

    void transitionTo(TaskStatus next) {
        this.status = next;
    }
}
