package robert_neat.his_backend.imaging;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.PageResponse;
import robert_neat.his_backend.common.api.SortWhitelist;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.lab.ResultAbnormalityFilter;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.patient.PatientService;
import robert_neat.his_backend.patient.PatientSummaryResponse;

/**
 * Wyniki badan obrazowych (odczyt, potwierdzenie, inbox, zapis REST). Lista pacjenta nie jest stronicowana (kontrakt:
 * `ImagingResult[]`, malejaco po `reportedAt`); inbox jest stronicowany (sort wg whitelisty, 422 dla nieznanego pola).
 * Potwierdzenie (`acknowledge`) jest idempotentne: kto/kiedy zapisuje pierwsze potwierdzenie, kolejne zwracaja wynik
 * bez zmian. Aktor z sesji. Zapis wynikow deleguje do {@link ImagingResultRecordingService} (ta sama sciezka co
 * `POST /fhir/DiagnosticReport`).
 */
@Service
@Transactional(readOnly = true)
public class ImagingResultService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "reportedAt").and(Sort.by("id"));

    private static final SortWhitelist SORT = SortWhitelist.of(Sort.by(Sort.Direction.DESC, "reportedAt"),
            "reportedAt", "performedAt", "modality", "examName", "status", "critical");

    private final ImagingResultRepository results;
    private final ImagingOrderRepository orders;
    private final PatientRepository patients;
    private final PatientService patientService;
    private final CurrentActor currentActor;
    private final ImagingResultRecordingService recording;

    ImagingResultService(ImagingResultRepository results, ImagingOrderRepository orders, PatientRepository patients,
            PatientService patientService, CurrentActor currentActor, ImagingResultRecordingService recording) {
        this.results = results;
        this.orders = orders;
        this.patients = patients;
        this.patientService = patientService;
        this.currentActor = currentActor;
        this.recording = recording;
    }

    /** 404 zlecenie (niepoprawny UUID = "nie istnieje"); reszta walidacji (422/409) w {@link ImagingResultRecordingService}. */
    @Transactional
    public ImagingResultResponse recordForOrder(String orderId, ImagingResultCreateRequest request) {
        UUID id = parse(orderId);
        ImagingOrder order = (id == null ? Optional.<ImagingOrder>empty() : orders.findById(id))
                .orElseThrow(() -> NotFoundException.of("Zlecenie obrazowe", orderId));
        return recording.recordResult(
                ImagingResultMapper.toCommand(request, order.getPatientId(), order.getId(), order.getModality()));
    }

    /** 404 pacjent. Wyniki pacjenta (opcjonalny filtr nieprawidlowosci), od najnowszego wg `reportedAt`. */
    public List<ImagingResultResponse> listForPatient(String patientId, ResultAbnormalityFilter filter) {
        UUID id = requirePatient(patientId);
        return results.findAll(ImagingResultSpecifications.matching(id, filter), NEWEST_FIRST).stream()
                .map(ImagingResultMapper::toResponse).toList();
    }

    /** Inbox: wyniki wszystkich pacjentow (opcjonalnie jednego) z podsumowaniem pacjenta. */
    public PageResponse<ImagingResultWithPatientResponse> inbox(UUID patientId, ResultAbnormalityFilter filter,
            Pageable requested) {
        Pageable pageable = requested;
        if (requested.isPaged()) {
            Pageable mapped = SORT.apply(requested);
            // stabilna kolejnosc stron: remisy rozstrzyga id
            pageable = PageRequest.of(mapped.getPageNumber(), mapped.getPageSize(),
                    mapped.getSort().and(Sort.by("id")));
        }
        Page<ImagingResult> page = results.findAll(ImagingResultSpecifications.matching(patientId, filter), pageable);
        Map<UUID, PatientSummaryResponse> summaries = patientService.summariesByIds(
                page.getContent().stream().map(ImagingResult::getPatientId).distinct().toList());
        return PageResponse.from(page, r -> ImagingResultWithPatientResponse.of(ImagingResultMapper.toResponse(r),
                summaries.get(r.getPatientId())));
    }

    /** 404 wynik. */
    public ImagingResultResponse get(String resultId) {
        return ImagingResultMapper.toResponse(require(resultId));
    }

    /**
     * 404 wynik; 403 brak powiazania sesji z pracownikiem; 409 gdy podana `version` nie zgadza sie z biezaca.
     * Ponowne potwierdzenie nie zmienia `reviewed*`.
     */
    @Transactional
    public ImagingResultResponse acknowledge(String resultId, Long expectedVersion) {
        UUID actor = currentActor.staffId()
                .orElseThrow(() -> new ForbiddenException("Brak powiazania sesji z pracownikiem"));
        ImagingResult result = require(resultId);
        if (expectedVersion != null && expectedVersion != result.getVersion()) {
            throw new ConflictException("Wynik obrazowy zostal zmodyfikowany przez inna osobe (wersja "
                    + result.getVersion() + "); odswiez dane i sprobuj ponownie");
        }
        if (result.acknowledge(actor, Instant.now())) {
            results.saveAndFlush(result);
        }
        return ImagingResultMapper.toResponse(result);
    }

    private ImagingResult require(String resultId) {
        UUID id = parse(resultId);
        return (id == null ? Optional.<ImagingResult>empty() : results.findById(id))
                .orElseThrow(() -> NotFoundException.of("Wynik obrazowy", resultId));
    }

    /** `patientId` jest nieprzezroczysty dla klienta: niepoprawny format to po prostu "nie istnieje" (404). */
    private UUID requirePatient(String patientId) {
        UUID id = parse(patientId);
        if (id == null || !patients.existsById(id)) {
            throw NotFoundException.of("Pacjent", patientId);
        }
        return id;
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
