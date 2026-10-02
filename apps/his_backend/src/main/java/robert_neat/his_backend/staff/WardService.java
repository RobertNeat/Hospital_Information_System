package robert_neat.his_backend.staff;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.NotFoundException;

/** Oddzialy: odczyt oraz zapis administratora (`ward:write`). Bez usuwania - FK z `staff_member`/`admission`. */
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

    @Transactional
    public WardResponse create(WardCreateRequest request) {
        if (wards.existsByShortNameIgnoreCase(request.shortName())) {
            throw new ConflictException("Oddzial o podanym skrocie juz istnieje");
        }
        Ward saved = wards.saveAndFlush(Ward.create(request.name().trim(), request.shortName().trim(),
                request.floor().trim(), request.beds()));
        return StaffMapper.toResponse(saved);
    }

    @Transactional
    public WardResponse update(String id, WardUpdateRequest request) {
        Ward ward = require(id);
        if (wards.existsByShortNameIgnoreCaseAndIdNot(request.shortName(), ward.getId())) {
            throw new ConflictException("Oddzial o podanym skrocie juz istnieje");
        }
        ward.update(request.name().trim(), request.shortName().trim(), request.floor().trim(), request.beds());
        wards.saveAndFlush(ward);
        return StaffMapper.toResponse(ward);
    }

    /** `id` jest nieprzezroczysty dla klienta: niepoprawny format to po prostu "nie istnieje" (404). */
    private Ward require(String id) {
        UUID uuid = parse(id);
        return (uuid == null ? java.util.Optional.<Ward>empty() : wards.findById(uuid))
                .orElseThrow(() -> NotFoundException.of("Oddzial", id));
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
