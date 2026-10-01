package robert_neat.his_backend.catalog;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Katalog lekow. Do ponownego uzycia przez recepty (snapshot nazwy, substancji, mocy, postaci). */
public interface DrugRepository extends JpaRepository<Drug, UUID>, JpaSpecificationExecutor<Drug> {
}
