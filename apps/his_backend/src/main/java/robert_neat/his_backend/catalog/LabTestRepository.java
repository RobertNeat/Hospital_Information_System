package robert_neat.his_backend.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

/** Katalog badan laboratoryjnych; klucz = kod badania. Do ponownego uzycia przez zlecenia laboratoryjne. */
public interface LabTestRepository extends JpaRepository<LabTest, String> {
}
