package robert_neat.elaboratory.order;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import robert_neat.elaboratory.order.LabOrderException.Kind;

/**
 * Logika e-laboratory. Metody sa serializowane monitorem (symulator, maly ruch). Zmiana stanu i wynik z UI sa
 * najpierw przekazywane do HIS: odrzucenie przez HIS (4xx) blokuje zmiane lokalna, a niedostepnosc HIS zostawia ja
 * lokalnie ze stanem synchronizacji `PENDING` (ponowienie z UI). Gdy kazda pozycja ma zatwierdzony (`final`/`corrected`)
 * i przekazany wynik, zlecenie przechodzi do `completed` (HIS robi to samo automatycznie po ostatnim wyniku).
 */
@Service
public class LabOrderService {

    private final LabOrderStore store;
    private final HisSync his;

    LabOrderService(LabOrderStore store, HisSync his) {
        this.store = store;
        this.his = his;
    }

    public record Registration(LabOrder order, boolean created) {
    }

    /** Wprowadzany wynik pozycji; `observations` bez wartosci sa pomijane przez wywolujacego. */
    public record ResultInput(ResultStatus status, String performer, String comment, List<Observation> observations) {
    }

    /** Idempotentne wzgledem id zlecenia HIS: powtorne wywolanie zwraca istniejace zlecenie. */
    public synchronized Registration register(LabOrderDraft draft) {
        Optional<LabOrder> existing = store.findById(draft.hisOrderId());
        if (existing.isPresent()) {
            return new Registration(existing.get().copy(), false);
        }
        LabOrder order = new LabOrder(draft, Instant.now());
        store.save(order);
        return new Registration(order.copy(), true);
    }

    public synchronized LabOrder get(String id) {
        return require(id).copy();
    }

    public synchronized List<LabOrder> list() {
        return store.findAllNewestFirst().stream().map(LabOrder::copy).toList();
    }

    /** Zmiana stanu z UI: najpierw HIS (patrz opis klasy). */
    public synchronized LabOrder changeFromUi(String id, LabOrderStatus target) {
        LabOrder order = require(id);
        checkTransition(order, target);
        HisSync.Result result = his.pushStatus(order, target);
        if (result.outcome() == HisSync.Outcome.REJECTED) {
            throw new LabOrderException(Kind.REJECTED_BY_HIS, "HIS odrzucil zmiane: " + result.message());
        }
        order.update(target, stateOf(result), result.message(), Instant.now());
        return order.copy();
    }

    /** Zmiana stanu zainicjowana przez HIS (np. anulowanie w HIS): HIS juz zna stan, bez wywolania zwrotnego. */
    public synchronized LabOrder changeFromHis(String id, LabOrderStatus target) {
        LabOrder order = require(id);
        if (order.getStatus() == target) {
            return order.copy();
        }
        checkTransition(order, target);
        order.update(target, SyncState.SYNCED, null, Instant.now());
        return order.copy();
    }

    /** Ponowienie przekazania biezacego stanu zlecenia do HIS. */
    public synchronized LabOrder resync(String id) {
        LabOrder order = require(id);
        HisSync.Result result = his.pushStatus(order, order.getStatus());
        if (result.outcome() == HisSync.Outcome.REJECTED) {
            order.updateSync(SyncState.PENDING, result.message(), Instant.now());
            throw new LabOrderException(Kind.REJECTED_BY_HIS, "HIS odrzucil zmiane: " + result.message());
        }
        order.updateSync(stateOf(result), result.message(), Instant.now());
        return order.copy();
    }

    /**
     * Wynik badania z pozycji zlecenia: walidacja lokalna (stan zlecenia, pozycja, wartosci), przekazanie do HIS,
     * zapis lokalny. Wynik ostateczny istniejacy dla pozycji dopuszcza tylko korekte (`corrected`).
     */
    public synchronized LabOrder recordResult(String id, String testCode, ResultInput input) {
        LabOrder order = require(id);
        LabItem item = order.getItems().stream().filter(i -> i.testCode().equals(testCode)).findFirst()
                .orElseThrow(() -> new LabOrderException(Kind.INVALID, "Zlecenie nie zawiera badania " + testCode));
        if (input.observations().isEmpty()) {
            throw new LabOrderException(Kind.INVALID, "Wynik musi zawierac co najmniej jedna obserwacje");
        }
        LabOrderStatus status = order.getStatus();
        boolean correction = input.status() == ResultStatus.CORRECTED;
        if (!(status.acceptsResults() || (status == LabOrderStatus.COMPLETED && correction))) {
            throw new LabOrderException(Kind.CONFLICT, "Zlecenie w stanie '" + status.wire() + "' nie przyjmuje wyniku"
                    + (status == LabOrderStatus.COMPLETED ? " (dozwolona tylko korekta)" : ""));
        }
        if (!correction && hasFinalised(order, item)) {
            throw new LabOrderException(Kind.CONFLICT,
                    "Badanie " + testCode + " ma juz wynik ostateczny; dozwolona tylko korekta");
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant collected = order.getCollectedAt() == null ? order.getReceivedAt() : order.getCollectedAt();
        collected = collected.truncatedTo(ChronoUnit.SECONDS);
        ResultEntry entry = new ResultEntry(UUID.randomUUID().toString(), testCode, input.status(),
                blankToDefault(input.performer(), "e-laboratory"), blankToNull(input.comment()),
                collected.isAfter(now) ? now : collected, now, input.observations());
        HisSync.Result result = his.pushResult(order, entry);
        if (result.outcome() == HisSync.Outcome.REJECTED) {
            throw new LabOrderException(Kind.REJECTED_BY_HIS, "HIS odrzucil wynik: " + result.message());
        }
        entry.updateSync(stateOf(result), result.message());
        order.addResult(entry, now);
        completeIfAllFinalised(order);
        return order.copy();
    }

    /** Ponowienie przekazania wyniku do HIS. */
    public synchronized LabOrder resyncResult(String id, String resultId) {
        LabOrder order = require(id);
        ResultEntry entry = order.getResults().stream().filter(r -> r.getId().equals(resultId)).findFirst()
                .orElseThrow(() -> new LabOrderException(Kind.NOT_FOUND, "Nie znaleziono wyniku " + resultId));
        HisSync.Result result = his.pushResult(order, entry);
        if (result.outcome() == HisSync.Outcome.REJECTED) {
            entry.updateSync(SyncState.PENDING, result.message());
            throw new LabOrderException(Kind.REJECTED_BY_HIS, "HIS odrzucil wynik: " + result.message());
        }
        entry.updateSync(stateOf(result), result.message());
        completeIfAllFinalised(order);
        return order.copy();
    }

    private static boolean hasFinalised(LabOrder order, LabItem item) {
        return order.getResults().stream()
                .anyMatch(r -> r.getTestCode().equals(item.testCode()) && r.getStatus().isFinalised());
    }

    /** Lustro auto-`completed` z HIS: wszystkie pozycje maja zatwierdzony i przekazany wynik. */
    private static void completeIfAllFinalised(LabOrder order) {
        if (!order.getStatus().acceptsResults()) {
            return;
        }
        boolean all = order.getItems().stream().allMatch(item -> order.getResults().stream().anyMatch(
                r -> r.getTestCode().equals(item.testCode()) && r.getStatus().isFinalised()
                        && r.getSyncState() != SyncState.PENDING));
        if (all) {
            order.update(LabOrderStatus.COMPLETED, SyncState.SYNCED, null, Instant.now());
        }
    }

    private static SyncState stateOf(HisSync.Result result) {
        return switch (result.outcome()) {
            case SYNCED -> SyncState.SYNCED;
            case DISABLED -> SyncState.DISABLED;
            default -> SyncState.PENDING;
        };
    }

    private static void checkTransition(LabOrder order, LabOrderStatus target) {
        if (!order.getStatus().canMoveTo(target)) {
            throw new LabOrderException(Kind.CONFLICT, "Niedozwolona zmiana stanu: " + order.getStatus().wire()
                    + " -> " + target.wire());
        }
    }

    private LabOrder require(String id) {
        return store.findById(id)
                .orElseThrow(() -> new LabOrderException(Kind.NOT_FOUND, "Nie znaleziono zlecenia " + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        String v = blankToNull(value);
        return v == null ? fallback : v;
    }
}
