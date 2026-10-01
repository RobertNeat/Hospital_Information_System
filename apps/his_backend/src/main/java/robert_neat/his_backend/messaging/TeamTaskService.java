package robert_neat.his_backend.messaging;

import static robert_neat.his_backend.messaging.MessagingSupport.actor;
import static robert_neat.his_backend.messaging.MessagingSupport.blankToNull;
import static robert_neat.his_backend.messaging.MessagingSupport.parse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.messaging.events.TaskAssigned;
import robert_neat.his_backend.messaging.events.TaskStatusChanged;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.staff.StaffMemberRepository;

/**
 * Zadania zespolu. Aktor z tokenu (`createdById` z zadania jest ignorowany). Utworzenie publikuje
 * {@link TaskAssigned}; zmiana statusu ({@link TeamTaskStateMachine}) publikuje {@link TaskStatusChanged} (bez
 * konsumentow). Status moze zmienic wylacznie osoba przypisana albo tworca (403); niedozwolone przejscie lub niezgodna
 * `version` = 409. Lista bez paginacji (`T[]`, jak w kontrakcie), sort: `createdAt` malejaco.
 */
@Service
@Transactional(readOnly = true)
public class TeamTaskService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));

    private final TeamTaskRepository tasks;
    private final StaffMemberRepository staff;
    private final PatientRepository patients;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;

    TeamTaskService(TeamTaskRepository tasks, StaffMemberRepository staff, PatientRepository patients,
            CurrentActor currentActor, ApplicationEventPublisher events) {
        this.tasks = tasks;
        this.staff = staff;
        this.patients = patients;
        this.currentActor = currentActor;
        this.events = events;
    }

    public List<TeamTaskResponse> list(UUID assignedToId, UUID createdById, TaskStatus status, UUID patientId) {
        List<Specification<TeamTask>> parts = new ArrayList<>();
        if (assignedToId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("assignedToId"), assignedToId));
        }
        if (createdById != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("createdById"), createdById));
        }
        if (status != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (patientId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("patientId"), patientId));
        }
        return tasks.findAll(Specification.allOf(parts), DEFAULT_SORT).stream().map(MessagingMapper::toResponse)
                .toList();
    }

    /** 404 (`assignedToId`/`patientId`), 422 (`status` inny niz `open`). */
    @Transactional
    public TeamTaskResponse create(TaskCreateRequest request) {
        UUID me = actor(currentActor);
        if (request.status() != null && request.status() != TaskStatus.OPEN) {
            throw new ValidationFailedException("status", "Nowe zadanie moze miec wylacznie status 'open'");
        }
        if (!staff.existsById(request.assignedToId())) {
            throw NotFoundException.of("Pracownik", request.assignedToId());
        }
        if (request.patientId() != null && !patients.existsById(request.patientId())) {
            throw NotFoundException.of("Pacjent", request.patientId());
        }
        Instant now = Instant.now();
        TeamTask saved = tasks.saveAndFlush(TeamTask.open(request.title().trim(),
                blankToNull(request.description()), request.patientId(), request.assignedToId(), request.dueAt(),
                request.priority()));
        events.publishEvent(new TaskAssigned(saved.getId(), saved.getAssignedToId(), me, saved.getPatientId(),
                saved.getTitle(), saved.getPriority(), saved.getDueAt(), now));
        return MessagingMapper.toResponse(saved);
    }

    /** 404, 403 (nie przypisany i nie tworca), 409 (przejscie / `version`). */
    @Transactional
    public TeamTaskResponse updateStatus(String taskId, TaskStatusUpdateRequest request) {
        UUID me = actor(currentActor);
        UUID id = parse(taskId);
        TeamTask task = (id == null ? java.util.Optional.<TeamTask>empty() : tasks.findById(id))
                .orElseThrow(() -> NotFoundException.of("Zadanie", taskId));
        if (!me.equals(task.getAssignedToId()) && !me.equals(task.getCreatedById())) {
            throw new ForbiddenException("Status zadania moze zmienic tylko osoba przypisana lub tworca zadania");
        }
        Long expected = request.version();
        if (expected != null && expected != task.getVersion()) {
            throw new ConflictException("Zadanie zostalo zmodyfikowane przez inna osobe (wersja "
                    + task.getVersion() + "); odswiez dane i sprobuj ponownie");
        }
        TaskStatus previous = task.getStatus();
        TaskStatus target = request.status();
        if (!TeamTaskStateMachine.canTransition(previous, target)) {
            throw new ConflictException("Niedozwolone przejscie statusu zadania z '" + previous.wire() + "' na '"
                    + target.wire() + "'");
        }
        task.transitionTo(target);
        TeamTask saved = tasks.saveAndFlush(task);
        events.publishEvent(new TaskStatusChanged(saved.getId(), saved.getAssignedToId(), saved.getCreatedById(),
                previous, target, me, Instant.now()));
        return MessagingMapper.toResponse(saved);
    }
}
