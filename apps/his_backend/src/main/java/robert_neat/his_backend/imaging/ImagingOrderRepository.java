package robert_neat.his_backend.imaging;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import robert_neat.his_backend.common.order.OrderStatus;

public interface ImagingOrderRepository
        extends JpaRepository<ImagingOrder, UUID>, JpaSpecificationExecutor<ImagingOrder> {

    /** Czy slot ma zlecenie w innym stanie niz anulowane (odpowiednik czesciowego indeksu unikalnego). */
    boolean existsBySlotIdAndStatusNot(UUID slotId, OrderStatus status);
}
