package robert_neat.his_backend.catalog;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Lek z katalogu (`drug` + `drug_route`, `drug_reimbursement_option`, `drug_interacts_with_atc`, tylko do odczytu).
 * Recepty kopiuja z niego dane jako snapshot (nazwa, substancja, moc, postac).
 */
@Entity
@Immutable
@Table(name = "drug")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Drug {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "active_substance", nullable = false, length = 200)
    private String activeSubstance;

    @Column(name = "atc_code", nullable = false, length = 10)
    private String atcCode;

    @Column(name = "form", nullable = false, length = 15)
    private DrugForm form;

    @Column(name = "strength", nullable = false, length = 50)
    private String strength;

    @Column(name = "package_size", nullable = false)
    private int packageSize;

    @Column(name = "package_unit", nullable = false, length = 30)
    private String packageUnit;

    @Column(name = "default_dose_unit", nullable = false, length = 30)
    private String defaultDoseUnit;

    @Column(name = "rx_only", nullable = false)
    private boolean rxOnly;

    @Embedded
    private MaxDailyDose maxDailyDose;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "drug_route", joinColumns = @JoinColumn(name = "drug_id"))
    @Column(name = "route", nullable = false, length = 15)
    @BatchSize(size = 100)
    private Set<AdministrationRoute> routes = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "drug_reimbursement_option", joinColumns = @JoinColumn(name = "drug_id"))
    @Column(name = "reimbursement", nullable = false, length = 10)
    @BatchSize(size = 100)
    private Set<ReimbursementLevel> reimbursementOptions = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "drug_interacts_with_atc", joinColumns = @JoinColumn(name = "drug_id"))
    @Column(name = "atc_code", nullable = false, length = 10)
    @BatchSize(size = 100)
    private Set<String> interactsWithAtc = new HashSet<>();
}
