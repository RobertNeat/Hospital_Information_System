package robert_neat.his_backend.messaging;

import static robert_neat.his_backend.messaging.MessagingSupport.actor;
import static robert_neat.his_backend.messaging.MessagingSupport.blankToNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.patient.Patient;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.staff.StaffMemberRepository;
import robert_neat.his_backend.staff.WardRepository;

/**
 * Przekazania zmiany. `fromId` z tokenu (z zadania ignorowany). Odwolania w ciele (oddzial, odbierajacy, pacjenci)
 * musza istniec, pacjenci nie moga sie powtarzac - bledy zbiorczo jako 422 z `errors[]`. Lista bez paginacji (`T[]`),
 * sort: `shiftDate` malejaco, potem `createdAt` malejaco.
 */
@Service
@Transactional(readOnly = true)
public class HandoffNoteService {

    private static final Sort SORT = Sort.by(Sort.Direction.DESC, "shiftDate", "createdAt").and(Sort.by("id"));

    private final HandoffNoteRepository notes;
    private final WardRepository wards;
    private final StaffMemberRepository staff;
    private final PatientRepository patients;
    private final CurrentActor currentActor;

    HandoffNoteService(HandoffNoteRepository notes, WardRepository wards, StaffMemberRepository staff,
            PatientRepository patients, CurrentActor currentActor) {
        this.notes = notes;
        this.wards = wards;
        this.staff = staff;
        this.patients = patients;
        this.currentActor = currentActor;
    }

    public List<HandoffNoteResponse> list(UUID wardId, LocalDate shiftDate) {
        List<Specification<HandoffNote>> parts = new ArrayList<>();
        if (wardId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("wardId"), wardId));
        }
        if (shiftDate != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("shiftDate"), shiftDate));
        }
        return notes.findAll(Specification.allOf(parts), SORT).stream().map(MessagingMapper::toResponse).toList();
    }

    /** 422: nieistniejacy oddzial / odbierajacy / pacjent, powtorzony pacjent. */
    @Transactional
    public HandoffNoteResponse create(HandoffNoteCreateRequest request) {
        UUID me = actor(currentActor);
        List<FieldError> errors = new ArrayList<>();
        if (!wards.existsById(request.wardId())) {
            errors.add(new FieldError("wardId", "Oddzial nie istnieje", "notFound"));
        }
        if (!staff.existsById(request.toId())) {
            errors.add(new FieldError("toId", "Pracownik odbierajacy zmiane nie istnieje", "notFound"));
        }
        List<HandoffPatientNoteDto> requested = request.patientNotes();
        Set<UUID> known = patients.findAllById(requested.stream().map(HandoffPatientNoteDto::patientId).distinct()
                .toList()).stream().map(Patient::getId).collect(Collectors.toSet());
        Set<UUID> seen = new HashSet<>();
        List<HandoffPatientNote> patientNotes = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            HandoffPatientNoteDto n = requested.get(i);
            if (!known.contains(n.patientId())) {
                errors.add(new FieldError("patientNotes[" + i + "].patientId", "Pacjent nie istnieje", "notFound"));
            } else if (!seen.add(n.patientId())) {
                errors.add(new FieldError("patientNotes[" + i + "].patientId",
                        "Pacjent wystepuje w przekazaniu wielokrotnie", "duplicate"));
            } else {
                patientNotes.add(HandoffPatientNote.of(n.patientId(), n.situation().trim(), n.background().trim(),
                        n.assessment().trim(), n.recommendation().trim()));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        HandoffNote saved = notes.saveAndFlush(HandoffNote.create(request.wardId(), request.shiftDate(),
                request.shift(), me, request.toId(), Instant.now(), blankToNull(request.generalNotes()),
                patientNotes));
        return MessagingMapper.toResponse(saved);
    }
}
