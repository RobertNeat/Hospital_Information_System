package robert_neat.his_backend.staff;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wards")
public class WardController {

    private final WardService service;

    WardController(WardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ward:read')")
    public List<WardResponse> list() {
        return service.list();
    }
}
