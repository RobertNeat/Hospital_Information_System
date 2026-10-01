package robert_neat.ereceipt.prescription;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import robert_neat.ereceipt.prescription.ReceiptException.Kind;

/**
 * Logika e-receipt. Metody sa serializowane monitorem (symulator, maly ruch). Zmiana stanu z UI jest najpierw
 * przekazywana do HIS: odrzucenie przez HIS (4xx) blokuje zmiane lokalna, a niedostepnosc HIS zostawia zmiane
 * lokalna ze stanem synchronizacji `PENDING` (ponowienie z UI).
 */
@Service
public class ReceiptService {

    /** Format klucza zgodny z kolumna `erx_key char(44)` w HIS: 44 znaki A-Z0-9. */
    static final int KEY_LENGTH = 44;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final ReceiptStore store;
    private final HisSync his;
    private final SecureRandom random = new SecureRandom();

    ReceiptService(ReceiptStore store, HisSync his) {
        this.store = store;
        this.his = his;
    }

    public record Registration(Receipt receipt, boolean created) {
    }

    /** Idempotentne wzgledem `hisPrescriptionId`: powtorne wywolanie zwraca istniejaca recepte. */
    public synchronized Registration register(ReceiptDraft draft) {
        Optional<Receipt> existing = store.findByHisId(draft.hisPrescriptionId());
        if (existing.isPresent()) {
            return new Registration(existing.get().copy(), false);
        }
        Instant now = Instant.now();
        Receipt receipt = new Receipt(newKey(), draft.hisPrescriptionId(), draft.accessCode(), draft.patientRef(),
                draft.requesterRef(), draft.authoredOn(), draft.validFrom(), draft.validUntil(), draft.medication(),
                draft.dosages(), draft.note(), now);
        store.save(receipt);
        return new Registration(receipt.copy(), true);
    }

    public synchronized Receipt get(String erxKey) {
        return require(erxKey).copy();
    }

    public synchronized List<Receipt> list() {
        return store.findAllNewestFirst().stream().map(Receipt::copy).toList();
    }

    /** Zmiana stanu z UI: najpierw HIS (patrz opis klasy). */
    public synchronized Receipt changeFromUi(String erxKey, ReceiptStatus target) {
        Receipt receipt = require(erxKey);
        checkTransition(receipt, target);
        HisSync.Result result = his.pushStatus(receipt, target);
        if (result.outcome() == HisSync.Outcome.REJECTED) {
            throw new ReceiptException(Kind.REJECTED_BY_HIS, "HIS odrzucil zmiane: " + result.message());
        }
        receipt.update(target, stateOf(result), result.message(), Instant.now());
        return receipt.copy();
    }

    /** Zmiana stanu zainicjowana przez HIS (np. anulowanie w HIS): HIS juz zna stan, bez wywolania zwrotnego. */
    public synchronized Receipt changeFromHis(String erxKey, ReceiptStatus target) {
        Receipt receipt = require(erxKey);
        if (receipt.getStatus() == target) {
            return receipt.copy();
        }
        checkTransition(receipt, target);
        receipt.update(target, SyncState.SYNCED, null, Instant.now());
        return receipt.copy();
    }

    /** Ponowienie przekazania biezacego stanu do HIS. */
    public synchronized Receipt resync(String erxKey) {
        Receipt receipt = require(erxKey);
        HisSync.Result result = his.pushStatus(receipt, receipt.getStatus());
        if (result.outcome() == HisSync.Outcome.REJECTED) {
            receipt.updateSync(SyncState.PENDING, result.message(), Instant.now());
            throw new ReceiptException(Kind.REJECTED_BY_HIS, "HIS odrzucil zmiane: " + result.message());
        }
        receipt.updateSync(stateOf(result), result.message(), Instant.now());
        return receipt.copy();
    }

    private static SyncState stateOf(HisSync.Result result) {
        return switch (result.outcome()) {
            case SYNCED -> SyncState.SYNCED;
            case DISABLED -> SyncState.DISABLED;
            default -> SyncState.PENDING;
        };
    }

    private static void checkTransition(Receipt receipt, ReceiptStatus target) {
        if (!receipt.getStatus().canMoveTo(target)) {
            throw new ReceiptException(Kind.CONFLICT, "Niedozwolona zmiana stanu: "
                    + receipt.getStatus().wire() + " -> " + target.wire());
        }
    }

    private Receipt require(String erxKey) {
        return store.findByKey(erxKey)
                .orElseThrow(() -> new ReceiptException(Kind.NOT_FOUND, "Nie znaleziono recepty " + erxKey));
    }

    private String newKey() {
        String key;
        do {
            StringBuilder b = new StringBuilder(KEY_LENGTH);
            for (int i = 0; i < KEY_LENGTH; i++) {
                b.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            key = b.toString();
        } while (store.containsKey(key));
        return key;
    }
}
