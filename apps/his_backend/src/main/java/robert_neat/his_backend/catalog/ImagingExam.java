package robert_neat.his_backend.catalog;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Badanie obrazowe (`imaging_exam`, katalog tylko do odczytu); klucz glowny to kod badania. */
@Entity
@Immutable
@Table(name = "imaging_exam")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImagingExam {

    @Id
    @Column(name = "code", length = 30)
    private String code;

    @Column(name = "modality", nullable = false, length = 15)
    private ImagingModality modality;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "body_region", nullable = false, length = 100)
    private String bodyRegion;

    @Column(name = "contrast_possible", nullable = false)
    private boolean contrastPossible;

    @Column(name = "requires_laterality", nullable = false)
    private boolean requiresLaterality;

    @Column(name = "preparation", columnDefinition = "text")
    private String preparation;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
}
