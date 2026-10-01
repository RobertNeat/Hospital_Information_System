package robert_neat.his_backend.patient;

import java.util.UUID;

/**
 * Port: sprawdzenie, czy notatka (epikryza) istnieje i nalezy do pacjenta. Definiowany w module `patient`, a
 * implementowany w module `ehr` (notatki klinicznie) - dzieki temu nie ma cyklicznej zaleznosci modulow.
 */
public interface DischargeSummaryNotes {

    boolean existsForPatient(UUID noteId, UUID patientId);
}
