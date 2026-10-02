package robert_neat.his_backend.catalog;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Progi parametrow zyciowych (API.md, par. 7). Odczyt: `vital-threshold:read` (lekarz, pielegniarka, admin);
 * edycja progu: `vital-threshold:write` (admin). `{type}` to wartosc na drucie `VitalType` (np. `heartRate`).
 */
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

    @PutMapping("/{type}")
    @PreAuthorize("hasAuthority('vital-threshold:write')")
    public VitalThresholdResponse update(@PathVariable String type,
            @Valid @RequestBody VitalThresholdUpdateRequest request) {
        return service.update(type, request);
    }
}
