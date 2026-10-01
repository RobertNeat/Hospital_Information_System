package robert_neat.ereceipt.prescription;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

/**
 * Magazyn recept w pamieci (symulator): e-receipt nie ma bazy, wiec restart czysci liste. Stan w HIS pozostaje
 * (HIS jest autorytatywny); nowe recepty trafiaja tu przy wystawieniu w HIS.
 */
@Repository
class ReceiptStore {

    private final ConcurrentHashMap<String, Receipt> byKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> keyByHisId = new ConcurrentHashMap<>();

    Optional<Receipt> findByKey(String erxKey) {
        return Optional.ofNullable(byKey.get(erxKey));
    }

    Optional<Receipt> findByHisId(String hisId) {
        return Optional.ofNullable(keyByHisId.get(hisId)).map(byKey::get);
    }

    void save(Receipt receipt) {
        byKey.put(receipt.getErxKey(), receipt);
        keyByHisId.put(receipt.getHisPrescriptionId(), receipt.getErxKey());
    }

    boolean containsKey(String erxKey) {
        return byKey.containsKey(erxKey);
    }

    List<Receipt> findAllNewestFirst() {
        return byKey.values().stream()
                .sorted(Comparator.comparing(Receipt::getReceivedAt).reversed()
                        .thenComparing(Receipt::getErxKey))
                .toList();
    }
}
