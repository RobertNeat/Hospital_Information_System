package robert_neat.eimaging.order;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import robert_neat.eimaging.order.ImagingOrderException.Kind;

/**
 * Logika e-imaging. Metody sa serializowane monitorem (symulator, maly ruch). Zmiana stanu i wynik z UI sa
 * najpierw przekazywane do HIS: odrzucenie przez HIS (4xx) blokuje zmiane lokalna, a niedostepnosc HIS zostawia ja
 * lokalnie ze stanem synchronizacji `PENDING` (ponowienie z UI). Wynik ostateczny (`final`) przekazany do HIS (albo przy
 * wylaczonej integracji) przenosi zlecenie do `completed` (HIS robi to samo automatycznie).
 */
@Service
public class ImagingOrderService {

    private final ImagingOrderStore store;
    private final HisSync his;

    ImagingOrderService(ImagingOrderStore store, HisSync his) {
        this.store = store;
        this.his = his;
    }

    public record Registration(ImagingOrder order, boolean created) {
    }

    /** Wprowadzany wynik (opis); `findings` i `conclusion` wymagane. */
    public record ResultInput(ResultStatus status, String radiologist, String findings, String conclusion,
            boolean critical) {
    }

    /** Idempotentne wzgledem id zlecenia HIS: powtorne wywolanie zwraca istniejace zlecenie. */
    public synchronized Registration register(ImagingOrderDraft draft) {
        Optional<ImagingOrder> existing = store.findById(draft.hisOrderId());
        if (existing.isPresent()) {
            return new Registration(existing.get().copy(), false);
        }
        ImagingOrder order = new ImagingOrder(draft, Instant.now());
        store.save(order);
        return new Registration(order.copy(), true);
    }

    public synchronized ImagingOrder get(String id) {
        return require(id).copy();
    }

    public synchronized List<ImagingOrder> list() {
        return store.findAllNewestFirst().stream().map(ImagingOrder::copy).toList();
    }

    /** Zmiana stanu z UI: najpierw HIS (patrz opis klasy). */
    public synchronized ImagingOrder changeFromUi(String id, ImagingOrderStatus target) {
        ImagingOrder order = require(id);
        checkTransition(order, target);
        HisSync.Result result = his.pushStatus(order, target);
        if (result.outcome() == HisSync.Outcome.REJECTED) {
            throw new ImagingOrderException(Kind.REJECTED_BY_HIS, "HIS odrzucil zmiane: " + result.message());
        }
        order.update(target, stateOf(result), result.message(), Instant.now());
        return order.copy();
    }

    /** Zmiana stanu zainicjowana przez HIS (np. anulowanie w HIS): HIS juz zna stan, bez wywolania zwrotnego. */
    public synchronized ImagingOrder changeFromHis(String id, ImagingOrderStatus target) {
        ImagingOrder order = require(id);
        if (order.getStatus() == target) {
            return order.copy();
        }
        checkTransition(order, target);
        order.update(target, SyncState.SYNCED, null, Instant.now());
        return order.copy();
    }

    /** Ponowienie przekazania biezacego stanu zlecenia do HIS. */
    public synchronized ImagingOrder resync(String id) {
        ImagingOrder order = require(id);
        HisSync.Result result = his.pushStatus(order, order.getStatus());
        if (result.outcome() == HisSync.Outcome.REJECTED) {
            order.updateSync(SyncState.PENDING, result.message(), Instant.now());
            throw new ImagingOrderException(Kind.REJECTED_BY_HIS, "HIS odrzucil zmiane: " + result.message());
        }
        order.updateSync(stateOf(result), result.message(), Instant.now());
        return order.copy();
    }

    /**
     * Wynik badania: walidacja lokalna (stan zlecenia, opis i wnioski, brak wyniku ostatecznego), przekazanie do HIS,
     * zapis lokalny. Czas opisu jest rosnacy w obrebie zlecenia (HIS rozpoznaje powtorzenia po statusie i czasie opisu).
     */
    public synchronized ImagingOrder recordResult(String id, ResultInput input) {
        ImagingOrder order = require(id);
        String findings = blankToNull(input.findings());
        String conclusion = blankToNull(input.conclusion());
        if (findings == null || conclusion == null) {
            throw new ImagingOrderException(Kind.INVALID, "Opis badania i wnioski sa wymagane");
        }
        if (!order.getStatus().acceptsResults()) {
            throw new ImagingOrderException(Kind.CONFLICT,
                    "Zlecenie w stanie '" + order.getStatus().wire() + "' nie przyjmuje wyniku");
        }
        if (hasFinal(order)) {
            throw new ImagingOrderException(Kind.CONFLICT, "Badanie ma juz wynik ostateczny");
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant last = order.getResults().stream().map(ResultEntry::getReportedAt).max(Instant::compareTo)
                .orElse(null);
        Instant reported = last != null && !now.isAfter(last) ? last.plusSeconds(1) : now;
        Instant performed = (order.getStartedAt() == null ? now : order.getStartedAt())
                .truncatedTo(ChronoUnit.SECONDS);
        if (performed.isAfter(reported)) {
            performed = reported;
        }
        ResultEntry entry = new ResultEntry(UUID.randomUUID().toString(), input.status(),
                blankToDefault(input.radiologist(), "e-imaging"), findings, conclusion, input.critical(), performed,
                reported);
        HisSync.Result result = his.pushResult(order, entry);
        if (result.outcome() == HisSync.Outcome.REJECTED) {
            throw new ImagingOrderException(Kind.REJECTED_BY_HIS, "HIS odrzucil wynik: " + result.message());
        }
        entry.updateSync(stateOf(result), result.message());
        order.addResult(entry, now);
        completeIfFinalised(order);
        return order.copy();
    }

    /** Ponowienie przekazania wyniku do HIS. */
    public synchronized ImagingOrder resyncResult(String id, String resultId) {
        ImagingOrder order = require(id);
        ResultEntry entry = order.getResults().stream().filter(r -> r.getId().equals(resultId)).findFirst()
                .orElseThrow(() -> new ImagingOrderException(Kind.NOT_FOUND, "Nie znaleziono wyniku " + resultId));
        HisSync.Result result = his.pushResult(order, entry);
        if (result.outcome() == HisSync.Outcome.REJECTED) {
            entry.updateSync(SyncState.PENDING, result.message());
            throw new ImagingOrderException(Kind.REJECTED_BY_HIS, "HIS odrzucil wynik: " + result.message());
        }
        entry.updateSync(stateOf(result), result.message());
        completeIfFinalised(order);
        return order.copy();
    }

    private static boolean hasFinal(ImagingOrder order) {
        return order.getResults().stream().anyMatch(r -> r.getStatus() == ResultStatus.FINAL);
    }

    /** Lustro auto-`completed` z HIS: wynik ostateczny jest przekazany (albo integracja wylaczona). */
    private static void completeIfFinalised(ImagingOrder order) {
        if (!order.getStatus().acceptsResults()) {
            return;
        }
        boolean done = order.getResults().stream()
                .anyMatch(r -> r.getStatus() == ResultStatus.FINAL && r.getSyncState() != SyncState.PENDING);
        if (done) {
            order.update(ImagingOrderStatus.COMPLETED, SyncState.SYNCED, null, Instant.now());
        }
    }

    private static SyncState stateOf(HisSync.Result result) {
        return switch (result.outcome()) {
            case SYNCED -> SyncState.SYNCED;
            case DISABLED -> SyncState.DISABLED;
            default -> SyncState.PENDING;
        };
    }

    private static void checkTransition(ImagingOrder order, ImagingOrderStatus target) {
        if (!order.getStatus().canMoveTo(target)) {
            throw new ImagingOrderException(Kind.CONFLICT, "Niedozwolona zmiana stanu: " + order.getStatus().wire()
                    + " -> " + target.wire());
        }
    }

    private ImagingOrder require(String id) {
        return store.findById(id)
                .orElseThrow(() -> new ImagingOrderException(Kind.NOT_FOUND, "Nie znaleziono zlecenia " + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        String v = blankToNull(value);
        return v == null ? fallback : v;
    }
}
