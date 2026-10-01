package robert_neat.his_backend.catalog;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Definicje analitow (katalog tylko do odczytu); wyszukiwanie po kodzie analitu (np. naglowek trendu bez punktow). */
public interface LabAnalyteDefinitionRepository extends JpaRepository<LabAnalyteDefinition, UUID> {

    /** Pierwsza (wg kodu badania) definicja analitu o danym kodzie. */
    Optional<LabAnalyteDefinition> findFirstByCodeOrderByTestCode(String code);
}
