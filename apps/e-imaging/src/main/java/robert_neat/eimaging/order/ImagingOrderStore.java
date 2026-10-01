package robert_neat.eimaging.order;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

/**
 * Magazyn zlecen w pamieci (symulator): e-imaging nie ma bazy, wiec restart czysci liste. Stan w HIS pozostaje
 * (HIS jest autorytatywny); nowe zlecenia trafiaja tu przy utworzeniu w HIS.
 */
@Repository
class ImagingOrderStore {

    private final ConcurrentHashMap<String, ImagingOrder> byId = new ConcurrentHashMap<>();

    Optional<ImagingOrder> findById(String hisOrderId) {
        return Optional.ofNullable(byId.get(hisOrderId));
    }

    void save(ImagingOrder order) {
        byId.put(order.getHisOrderId(), order);
    }

    List<ImagingOrder> findAllNewestFirst() {
        return byId.values().stream()
                .sorted(Comparator.comparing(ImagingOrder::getReceivedAt).reversed()
                        .thenComparing(ImagingOrder::getHisOrderId))
                .toList();
    }
}
