package robert_neat.his_backend.lab;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import robert_neat.his_backend.common.api.PageResponse;

/**
 * Wyniki laboratoryjne (API.md, par. 4). Odczyt: `lab-result:read`; potwierdzenie: `lab-result:acknowledge`.
 * Wprowadzanie wynikow nie ma endpointu (poza kontraktem UI) - {@link LabResultRecordingService}.
 */
@RestController
@RequestMapping("/api/v1")
public class LabResultController {

    private final LabResultService service;

    LabResultController(LabResultService service) {
        this.service = service;
    }

    /** Wyniki pacjenta (bez paginacji), od najnowszego; `filter`: all (domyslnie) / abnormal / critical. */
    @GetMapping("/patients/{patientId}/lab-results")
    @PreAuthorize("hasAuthority('lab-result:read')")
    public List<LabResultResponse> listForPatient(@PathVariable String patientId,
            @RequestParam(required = false) ResultAbnormalityFilter filter) {
        return service.listForPatient(patientId, filter);
    }

    @GetMapping("/patients/{patientId}/lab-results/trends/{analyteCode}")
    @PreAuthorize("hasAuthority('lab-result:read')")
    public AnalyteTrendResponse trend(@PathVariable String patientId, @PathVariable String analyteCode) {
        return service.trend(patientId, analyteCode);
    }

    @GetMapping("/patients/{patientId}/lab-results/analytes")
    @PreAuthorize("hasAuthority('lab-result:read')")
    public List<LabAnalyteRef> trendableAnalytes(@PathVariable String patientId) {
        return service.trendableAnalytes(patientId);
    }

    /** Inbox wynikow (`ResultWithPatient`), stronicowany; sort domyslny `resultedAt,desc`. */
    @GetMapping("/lab-results")
    @PreAuthorize("hasAuthority('lab-result:read')")
    public PageResponse<LabResultWithPatientResponse> inbox(@RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) ResultAbnormalityFilter filter, Pageable pageable) {
        return service.inbox(patientId, filter, pageable);
    }

    @GetMapping("/lab-results/{resultId}")
    @PreAuthorize("hasAuthority('lab-result:read')")
    public LabResultResponse get(@PathVariable String resultId) {
        return service.get(resultId);
    }

    /** Idempotentne; cialo opcjonalne (`version` ignorowane - wynik nie ma wersji). */
    @PostMapping("/lab-results/{resultId}/acknowledge")
    @PreAuthorize("hasAuthority('lab-result:acknowledge')")
    public LabResultResponse acknowledge(@PathVariable String resultId,
            @RequestBody(required = false) ResultAcknowledgeRequest request) {
        return service.acknowledge(resultId);
    }
}
