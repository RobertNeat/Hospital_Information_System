package robert_neat.his_backend.imaging;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ImagingResultRepository
        extends JpaRepository<ImagingResult, UUID>, JpaSpecificationExecutor<ImagingResult> {

    /** Czy zlecenie ma juz wynik w danym statusie (np. ostateczny - kolejny wynik jest wtedy konfliktem). */
    boolean existsByOrderIdAndStatus(UUID orderId, ImagingResultStatus status);

    /** Wynik zlecenia o tym statusie i czasie opisu (powtorzone przekazanie z e-imaging jest idempotentne). */
    java.util.Optional<ImagingResult> findFirstByOrderIdAndStatusAndReportedAt(UUID orderId,
            ImagingResultStatus status, java.time.Instant reportedAt);
}
