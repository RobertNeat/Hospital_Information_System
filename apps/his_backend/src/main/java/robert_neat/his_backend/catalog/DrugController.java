package robert_neat.his_backend.catalog;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Katalog lekow (API.md, par. 6). Odczyt: `drug:read` (lekarz, pielegniarka, farmaceuta, admin). */
@RestController
@RequestMapping("/api/v1/drugs")
public class DrugController {

    private static final String READ = "hasAuthority('drug:read')";

    private final DrugCatalogService service;

    DrugController(DrugCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<DrugResponse> search(@RequestParam(required = false) String term) {
        return service.search(term);
    }

    @GetMapping("/{drugId}")
    @PreAuthorize(READ)
    public DrugResponse get(@PathVariable String drugId) {
        return service.get(drugId);
    }
}
