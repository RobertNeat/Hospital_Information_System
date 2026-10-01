package robert_neat.his_backend.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Badanie laboratoryjne (`lab_test`, katalog tylko do odczytu) z dozwolonymi materialami (`lab_test_specimen`)
 * i analitami (`lab_analyte_definition`). Klucz glowny to kod (np. `CRP`), do ktorego odwoluja sie zlecenia.
 */
@Entity
@Immutable
@Table(name = "lab_test")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabTest {

    @Id
    @Column(name = "code", length = 30)
    private String code;

    @Column(name = "loinc", length = 20)
    private String loinc;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "category", nullable = false, length = 20)
    private LabCategory category;

    @Column(name = "default_specimen", nullable = false, length = 10)
    private SpecimenType defaultSpecimen;

    @Column(name = "turnaround_hours", nullable = false)
    private int turnaroundHours;

    @Column(name = "fasting_required", nullable = false)
    private boolean fastingRequired;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "lab_test_specimen", joinColumns = @JoinColumn(name = "test_code"))
    @Column(name = "specimen_type", nullable = false, length = 10)
    @BatchSize(size = 100)
    private Set<SpecimenType> specimenTypes = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_code", insertable = false, updatable = false)
    @BatchSize(size = 100)
    private List<LabAnalyteDefinition> analytes = List.of();
}
