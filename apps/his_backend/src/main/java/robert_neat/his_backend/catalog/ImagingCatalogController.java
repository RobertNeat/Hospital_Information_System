package robert_neat.his_backend.catalog;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Katalog obrazowy (API.md, par. 5). Odczyt: `imaging-order:read` (lekarz, radiolog, admin). */
@RestController
@RequestMapping("/api/v1")
public class ImagingCatalogController {

    private static final String READ = "hasAuthority('imaging-order:read')";

    private final ImagingCatalogService service;

    ImagingCatalogController(ImagingCatalogService service) {
        this.service = service;
    }

    @GetMapping("/imaging-exams")
    @PreAuthorize(READ)
    public List<ImagingExamResponse> exams(@RequestParam(required = false) ImagingModality modality) {
        return service.exams(modality);
    }

    /** `modality` i `date` (`YYYY-MM-DD`) wymagane; brak lub zla wartosc to 422. */
    @GetMapping("/imaging-slots")
    @PreAuthorize(READ)
    public List<ScheduleSlotResponse> slots(@RequestParam ImagingModality modality,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.slots(modality, date);
    }
}
