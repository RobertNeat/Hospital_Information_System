package robert_neat.his_backend.staff;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WardService {

    private static final Sort ORDER = Sort.by("name", "id");

    private final WardRepository wards;

    WardService(WardRepository wards) {
        this.wards = wards;
    }

    public List<WardResponse> list() {
        return wards.findAll(ORDER).stream().map(StaffMapper::toResponse).toList();
    }
}
