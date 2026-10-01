package robert_neat.his_backend.imaging;

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
import robert_neat.his_backend.lab.ResultAbnormalityFilter;
import robert_neat.his_backend.lab.ResultAcknowledgeRequest;

/**
 * Wyniki badan obrazowych (API.md, par. 5). Odczyt: `imaging-result:read`; potwierdzenie: `imaging-result:acknowledge`.
 * Wprowadzanie wynikow nie ma endpointu (poza kontraktem UI) - {@link ImagingResultRecordingService}.
 */
@RestController
@RequestMapping("/api/v1")
public class ImagingResultController {

    private final ImagingResultService service;

    ImagingResultController(ImagingResultService service) {
        this.service = service;
    }

    /** Wyniki pacjenta (bez paginacji), od najnowszego; `filter`: all (domyslnie) / abnormal / critical. */
    @GetMapping("/patients/{patientId}/imaging-results")
    @PreAuthorize("hasAuthority('imaging-result:read')")
    public List<ImagingResultResponse> listForPatient(@PathVariable String patientId,
            @RequestParam(required = false) ResultAbnormalityFilter filter) {
        return service.listForPatient(patientId, filter);
    }

    /** Inbox wynikow (`ResultWithPatient`), stronicowany; sort domyslny `reportedAt,desc`. */
    @GetMapping("/imaging-results")
    @PreAuthorize("hasAuthority('imaging-result:read')")
    public PageResponse<ImagingResultWithPatientResponse> inbox(@RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) ResultAbnormalityFilter filter, Pageable pageable) {
        return service.inbox(patientId, filter, pageable);
    }

    @GetMapping("/imaging-results/{resultId}")
    @PreAuthorize("hasAuthority('imaging-result:read')")
    public ImagingResultResponse get(@PathVariable String resultId) {
        return service.get(resultId);
    }

    /** Idempotentne; cialo opcjonalne (`version` ignorowane - wynik nie ma wersji). */
    @PostMapping("/imaging-results/{resultId}/acknowledge")
    @PreAuthorize("hasAuthority('imaging-result:acknowledge')")
    public ImagingResultResponse acknowledge(@PathVariable String resultId,
            @RequestBody(required = false) ResultAcknowledgeRequest request) {
        return service.acknowledge(resultId);
    }
}
