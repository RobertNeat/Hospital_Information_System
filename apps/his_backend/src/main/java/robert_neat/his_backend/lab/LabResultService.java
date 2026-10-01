package robert_neat.his_backend.lab;

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

import robert_neat.his_backend.catalog.LabAnalyteDefinition;
import robert_neat.his_backend.catalog.LabAnalyteDefinitionRepository;
import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.PageResponse;
import robert_neat.his_backend.common.api.SortWhitelist;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.patient.PatientService;
import robert_neat.his_backend.patient.PatientSummaryResponse;

/**
 * Wyniki laboratoryjne (odczyt, potwierdzenie, trendy, inbox). Lista pacjenta nie jest stronicowana (kontrakt:
 * `LabResult[]`, malejaco po `resultedAt`); inbox jest stronicowany (sort wg whitelisty, 422 dla nieznanego pola).
 * Potwierdzenie (`acknowledge`) jest idempotentne: kto/kiedy zapisuje pierwsze potwierdzenie, kolejne zwracaja wynik bez
 * zmian. Aktor z sesji. DTO mapowane w transakcji. Zapis wynikow: {@link LabResultRecordingService}.
 */
@Service
@Transactional(readOnly = true)
public class LabResultService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "resultedAt").and(Sort.by("id"));

    private static final SortWhitelist SORT = SortWhitelist.of(Sort.by(Sort.Direction.DESC, "resultedAt"),
            "resultedAt", "collectedAt", "testCode", "testName", "category", "status");

    private final LabResultRepository results;
    private final PatientRepository patients;
    private final PatientService patientService;
    private final LabAnalyteDefinitionRepository analyteDefinitions;
    private final CurrentActor currentActor;

    LabResultService(LabResultRepository results, PatientRepository patients, PatientService patientService,
            LabAnalyteDefinitionRepository analyteDefinitions, CurrentActor currentActor) {
        this.results = results;
        this.patients = patients;
        this.patientService = patientService;
        this.analyteDefinitions = analyteDefinitions;
        this.currentActor = currentActor;
    }

    /** 404 pacjent. Wyniki pacjenta (opcjonalny filtr nieprawidlowosci), od najnowszego wg `resultedAt`. */
    public List<LabResultResponse> listForPatient(String patientId, ResultAbnormalityFilter filter) {
        UUID id = requirePatient(patientId);
        return results.findAll(LabResultSpecifications.matching(id, filter), NEWEST_FIRST).stream()
                .map(LabResultMapper::toResponse).toList();
    }

    /** Inbox: wyniki wszystkich pacjentow (opcjonalnie jednego) z podsumowaniem pacjenta. */
    public PageResponse<LabResultWithPatientResponse> inbox(UUID patientId, ResultAbnormalityFilter filter,
            Pageable requested) {
        Pageable pageable = requested;
        if (requested.isPaged()) {
            Pageable mapped = SORT.apply(requested);
            // stabilna kolejnosc stron: remisy rozstrzyga id
            pageable = PageRequest.of(mapped.getPageNumber(), mapped.getPageSize(),
                    mapped.getSort().and(Sort.by("id")));
        }
        Page<LabResult> page = results.findAll(LabResultSpecifications.matching(patientId, filter), pageable);
        Map<UUID, PatientSummaryResponse> summaries = patientService
                .summariesByIds(page.getContent().stream().map(LabResult::getPatientId).distinct().toList());
        return PageResponse.from(page,
                r -> LabResultWithPatientResponse.of(LabResultMapper.toResponse(r), summaries.get(r.getPatientId())));
    }

    /** 404 wynik. */
    public LabResultResponse get(String resultId) {
        return LabResultMapper.toResponse(require(resultId));
    }

    /** 404 wynik; 403 brak powiazania sesji z pracownikiem. Ponowne potwierdzenie nie zmienia `reviewed*`. */
    @Transactional
    public LabResultResponse acknowledge(String resultId) {
        UUID actor = currentActor.staffId()
                .orElseThrow(() -> new ForbiddenException("Brak powiazania sesji z pracownikiem"));
        LabResult result = require(resultId);
        if (result.acknowledge(actor, Instant.now())) {
            results.saveAndFlush(result);
        }
        return LabResultMapper.toResponse(result);
    }

    /** 404 pacjent; brak punktow = pusta lista `points` (naglowek z katalogu albo kod jako nazwa). */
    public AnalyteTrendResponse trend(String patientId, String analyteCode) {
        UUID id = requirePatient(patientId);
        List<TrendRow> rows = results.trendRows(id, analyteCode);
        List<AnalyteTrendResponse.Point> points = rows.stream()
                .map(r -> new AnalyteTrendResponse.Point(r.collectedAt(), LabResultMapper.plain(r.value()), r.flag()))
                .toList();
        if (!rows.isEmpty()) {
            TrendRow latest = rows.getLast();
            return new AnalyteTrendResponse(analyteCode, latest.analyteName(), latest.unit(),
                    LabResultMapper.plain(latest.low()), LabResultMapper.plain(latest.high()), points);
        }
        Optional<LabAnalyteDefinition> definition = analyteDefinitions.findFirstByCodeOrderByTestCode(analyteCode);
        return definition
                .map(d -> new AnalyteTrendResponse(analyteCode, d.getName(), d.getUnit(),
                        LabResultMapper.plain(d.getLow()), LabResultMapper.plain(d.getHigh()), points))
                .orElseGet(() -> new AnalyteTrendResponse(analyteCode, analyteCode, "", null, null, points));
    }

    /** 404 pacjent. Anality pacjenta z wartoscia liczbowa (mozliwe do pokazania na wykresie), wg nazwy. */
    public List<LabAnalyteRef> trendableAnalytes(String patientId) {
        return results.trendableAnalytes(requirePatient(patientId));
    }

    private LabResult require(String resultId) {
        UUID id = parse(resultId);
        return (id == null ? Optional.<LabResult>empty() : results.findById(id))
                .orElseThrow(() -> NotFoundException.of("Wynik laboratoryjny", resultId));
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
