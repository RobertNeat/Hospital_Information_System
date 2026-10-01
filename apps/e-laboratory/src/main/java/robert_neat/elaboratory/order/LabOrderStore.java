package robert_neat.elaboratory.order;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

/**
 * Magazyn zlecen w pamieci (symulator): e-laboratory nie ma bazy, wiec restart czysci liste. Stan w HIS pozostaje
 * (HIS jest autorytatywny); nowe zlecenia trafiaja tu przy utworzeniu w HIS.
 */
@Repository
class LabOrderStore {

    private final ConcurrentHashMap<String, LabOrder> byId = new ConcurrentHashMap<>();

    Optional<LabOrder> findById(String hisOrderId) {
        return Optional.ofNullable(byId.get(hisOrderId));
    }

    void save(LabOrder order) {
        byId.put(order.getHisOrderId(), order);
    }

    List<LabOrder> findAllNewestFirst() {
        return byId.values().stream()
                .sorted(Comparator.comparing(LabOrder::getReceivedAt).reversed()
                        .thenComparing(LabOrder::getHisOrderId))
                .toList();
    }
}
