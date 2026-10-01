package robert_neat.his_backend.ehr;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.patient.DischargeSummaryNotes;

/** Implementacja portu modulu `patient`: epikryza musi byc istniejaca notatka tego pacjenta. */
@Component
class ClinicalNoteDischargeLookup implements DischargeSummaryNotes {

    private final ClinicalNoteRepository notes;

    ClinicalNoteDischargeLookup(ClinicalNoteRepository notes) {
        this.notes = notes;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsForPatient(UUID noteId, UUID patientId) {
        return notes.existsByIdAndPatientId(noteId, patientId);
    }
}
