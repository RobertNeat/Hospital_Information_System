package robert_neat.his_backend.lab;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** `ReferenceRange` z kontraktu (osadzony w obserwacji: `ref_low`, `ref_high`, `ref_text`); wszystkie pola opcjonalne. */
@Embeddable
public record ReferenceRange(
        @Column(name = "ref_low", precision = 14, scale = 4) BigDecimal low,
        @Column(name = "ref_high", precision = 14, scale = 4) BigDecimal high,
        @Column(name = "ref_text", length = 200) String text) {
}
