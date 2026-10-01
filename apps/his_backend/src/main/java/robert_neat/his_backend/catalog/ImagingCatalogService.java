package robert_neat.his_backend.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.ValidationFailedException;

/** Katalog badan obrazowych i grafik pracowni (`GET /imaging-exams`, `GET /imaging-slots`), tylko odczyt. */
@Service
@Transactional(readOnly = true)
public class ImagingCatalogService {

    /** Strefa kliniki: `date` w `GET /imaging-slots` to dzien kalendarzowy w tej strefie (jak w danych mock). */
    public static final ZoneId CLINIC_ZONE = ZoneId.of("Europe/Warsaw");

    private final ImagingExamRepository exams;
    private final ScheduleSlotRepository slots;

    ImagingCatalogService(ImagingExamRepository exams, ScheduleSlotRepository slots) {
        this.exams = exams;
        this.slots = slots;
    }

    /** Badania (opcjonalnie jednej modalnosci) wg modalnosci (kolejnosc kontraktu), potem nazwy. */
    public List<ImagingExamResponse> exams(ImagingModality modality) {
        List<ImagingExam> found = modality == null ? exams.findAll() : exams.findByModality(modality);
        return found.stream()
                .sorted(Comparator.comparing(ImagingExam::getModality)
                        .thenComparing(CatalogMapper.<ImagingExam>byText(ImagingExam::getName))
                        .thenComparing(ImagingExam::getCode))
                .map(CatalogMapper::toResponse).toList();
    }

    /**
     * Wszystkie sloty modalnosci (takze zajete, z `available=false`) o poczatku w dobie `date` strefy
     * {@link #CLINIC_ZONE}, wg poczatku i sali. Brak parametru to 422.
     */
    public List<ScheduleSlotResponse> slots(ImagingModality modality, LocalDate date) {
        if (modality == null) {
            throw new ValidationFailedException("modality", "Parametr jest wymagany");
        }
        if (date == null) {
            throw new ValidationFailedException("date", "Parametr jest wymagany");
        }
        Instant from = date.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        return slots.findByModalityAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAscRoomAscIdAsc(
                modality, from, to).stream().map(CatalogMapper::toResponse).toList();
    }
}
