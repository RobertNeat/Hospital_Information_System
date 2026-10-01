package robert_neat.his_backend.catalog;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Panel badan (`lab_panel` + `lab_panel_test`, katalog tylko do odczytu): nazwany zestaw kodow badan. */
@Entity
@Immutable
@Table(name = "lab_panel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabPanel {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "lab_panel_test", joinColumns = @JoinColumn(name = "panel_id"))
    @Column(name = "test_code", nullable = false, length = 30)
    @BatchSize(size = 100)
    private Set<String> testCodes = new HashSet<>();
}
