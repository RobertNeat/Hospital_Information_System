package robert_neat.his_backend.catalog;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Katalog laboratoryjny (API.md, par. 4). Odczyt: `lab-order:read` (lekarz, pielegniarka, laborant, admin). */
@RestController
@RequestMapping("/api/v1")
public class LabCatalogController {

    private static final String READ = "hasAuthority('lab-order:read')";

    private final LabCatalogService service;

    LabCatalogController(LabCatalogService service) {
        this.service = service;
    }

    @GetMapping("/lab-tests")
    @PreAuthorize(READ)
    public List<LabTestResponse> tests() {
        return service.tests();
    }

    @GetMapping("/lab-panels")
    @PreAuthorize(READ)
    public List<LabPanelResponse> panels() {
        return service.panels();
    }
}
