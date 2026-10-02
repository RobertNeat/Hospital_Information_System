package robert_neat.his_backend.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.wire.WireEnums;

/** Progi parametrow zyciowych: odczyt (`GET /vital-thresholds`) i edycja progow administratora (`vital-threshold:write`). */
@Service
@Transactional(readOnly = true)
public class VitalThresholdService {

    private final VitalThresholdRepository thresholds;

    VitalThresholdService(VitalThresholdRepository thresholds) {
        this.thresholds = thresholds;
    }

    /** Progi wszystkich parametrow w kolejnosci `VitalType`. */
    public List<VitalThresholdResponse> list() {
        return thresholds.findAll().stream()
                .sorted(Comparator.comparing(VitalThreshold::getType))
                .map(CatalogMapper::toResponse).toList();
    }

    /**
     * Nadpisuje prog danego parametru. 404 nieznany typ (sciezka spoza `VitalType`); 422 niezachowana kolejnosc
     * `min &lt;= criticalLow &lt;= low &lt;= high &lt;= criticalHigh &lt;= max`.
     */
    @Transactional
    public VitalThresholdResponse update(String typeCode, VitalThresholdUpdateRequest request) {
        VitalType type;
        try {
            type = WireEnums.fromWire(VitalType.class, typeCode);
        } catch (IllegalArgumentException e) {
            throw NotFoundException.of("Prog parametru zyciowego", typeCode);
        }
        VitalThreshold threshold = thresholds.findById(type.wire())
                .orElseThrow(() -> NotFoundException.of("Prog parametru zyciowego", typeCode));

        List<FieldError> errors = new ArrayList<>();
        requireOrder(request.min(), request.criticalLow(), "criticalLow", errors);
        requireOrder(request.criticalLow(), request.low(), "low", errors);
        requireOrder(request.low(), request.high(), "high", errors);
        requireOrder(request.high(), request.criticalHigh(), "criticalHigh", errors);
        requireOrder(request.criticalHigh(), request.max(), "max", errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        threshold.update(request.label().trim(), request.unit().trim(), request.low(), request.high(),
                request.criticalLow(), request.criticalHigh(), request.min(), request.max());
        thresholds.saveAndFlush(threshold);
        return CatalogMapper.toResponse(threshold);
    }

    private static void requireOrder(BigDecimal lower, BigDecimal upper, String field, List<FieldError> errors) {
        if (lower.compareTo(upper) > 0) {
            errors.add(new FieldError(field, "Wartosc musi byc wieksza lub rowna poprzedniemu progowi", "range"));
        }
    }
}
