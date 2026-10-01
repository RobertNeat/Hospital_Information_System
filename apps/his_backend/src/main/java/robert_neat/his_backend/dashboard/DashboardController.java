package robert_neat.his_backend.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard (API.md, par. 1): `GET /dashboard/stats`, uprawnienie `dashboard:read` (kazda rola). */
@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private final DashboardService service;

    DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public DashboardStatsResponse stats() {
        return service.stats();
    }
}
