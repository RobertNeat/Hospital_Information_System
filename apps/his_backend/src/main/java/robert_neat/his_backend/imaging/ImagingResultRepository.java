package robert_neat.his_backend.imaging;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ImagingResultRepository
        extends JpaRepository<ImagingResult, UUID>, JpaSpecificationExecutor<ImagingResult> {

    /** Czy zlecenie ma juz wynik w danym statusie (np. ostateczny - kolejny wynik jest wtedy konfliktem). */
    boolean existsByOrderIdAndStatus(UUID orderId, ImagingResultStatus status);
}
