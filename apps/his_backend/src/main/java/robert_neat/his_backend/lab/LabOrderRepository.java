package robert_neat.his_backend.lab;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LabOrderRepository extends JpaRepository<LabOrder, UUID>, JpaSpecificationExecutor<LabOrder> {

    /** Zlecenie zawierajace pozycje o danym id (zapis wyniku wskazanego tylko pozycja). */
    java.util.Optional<LabOrder> findByItemsId(UUID itemId);
}
