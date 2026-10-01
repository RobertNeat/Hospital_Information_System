package robert_neat.his_backend.catalog;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Progi parametrow zyciowych (API.md, par. 7). Odczyt: `vital-threshold:read` (lekarz, pielegniarka, admin). */
@RestController
@RequestMapping("/api/v1/vital-thresholds")
public class VitalThresholdController {

    private final VitalThresholdService service;

    VitalThresholdController(VitalThresholdService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('vital-threshold:read')")
    public List<VitalThresholdResponse> list() {
        return service.list();
    }
}
