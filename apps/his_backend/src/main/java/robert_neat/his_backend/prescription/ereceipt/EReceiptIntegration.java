package robert_neat.his_backend.prescription.ereceipt;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import robert_neat.his_backend.common.outbox.OutboxIntegration;
import robert_neat.his_backend.common.outbox.OutboxOperation;
import robert_neat.his_backend.common.outbox.OutboxRetryable;
import robert_neat.his_backend.common.outbox.OutboxRetrySupport;
import robert_neat.his_backend.prescription.PrescriptionKind;
import robert_neat.his_backend.prescription.PrescriptionRepository;
import robert_neat.his_backend.prescription.PrescriptionStatus;
import robert_neat.his_backend.prescription.events.PrescriptionCancelled;
import robert_neat.his_backend.prescription.events.PrescriptionIssued;

/**
 * Integracja z e-receipt na zdarzeniach domenowych, WYLACZNIE po commicie (`AFTER_COMMIT`): wystawienie i anulowanie
 * recepty nigdy nie zalezy od e-receipt - kazdy blad (siec, HTTP, nieoczekiwana odpowiedz) jest logowany, a nieudany
 * `POST` trafia do outboxa ({@link OutboxRetrySupport}, {@link IntegrationOutboxScheduler}) do ponowienia.
 * <ul>
 *   <li>`PrescriptionIssued`: synchroniczna proba `POST` recepty (ta sama sciezka co retry - {@link #trySubmit}),
 *       zapis zwroconego `eRxKey` w osobnej transakcji (`REQUIRES_NEW`, JPQL bez zmiany `version`). Listener dziala
 *       synchronicznie w watku requestu (po commicie, przed odpowiedzia HTTP), wiec kontroler odczytuje recepte
 *       ponownie po `issue()` i odpowiedz REST niesie juz docelowy `eRxKey` - pod warunkiem ze pierwsza proba sie
 *       powiedzie; nieudana zostaje dla schedulera, a klient widzi klucz lokalny (bez zmiany zachowania/API).</li>
 *   <li>`PrescriptionCancelled` z aktorem (anulowanie w HIS): PUT stanu do e-receipt. Zdarzenie bez aktora (zmiana
 *       przyszla z e-receipt) nie jest odsylane - brak petli. PUT nie jest ponawiany przez outbox (poza zakresem:
 *       patrz dokumentacja biezacego stanu w `docs/deployment-and-config.md`).</li>
 * </ul>
 * Wywolanie jest synchroniczne (limity czasu z konfiguracji), poza transakcja bazodanowa.
 * <p>
 * <b>Wyscig `erx_key` (rozwiazany):</b> {@link #trySubmit} czyta `eRxKey` TUZ przed wywolaniem `POST` i podmienia go
 * warunkowym JPQL (`erx_key = :staleKey`, patrz {@link PrescriptionRepository#updateERxKeyIfStillLocal}) - jesli w
 * miedzyczasie recepte juz zaktualizowal inny watek (np. wczesniejsza proba zdazyla sie powiesc), podmiana nie
 * nadpisuje nowszej wartosci (0 zaktualizowanych wierszy = brak akcji). Po udanej podmianie klucza retry sprawdza
 * tez, czy recepta nie zostala miedzyczasie anulowana w HIS (PUT anulowania mogl dostac 404, bo e-receipt jeszcze
 * nie znal recepty) - jesli tak, natychmiast wysyla PUT anulowania nowym kluczem, zeby e-receipt nie trzymal
 * recepty jako aktywnej mimo anulowania w HIS.
 */
@Component
public class EReceiptIntegration implements OutboxRetryable {

    private static final Logger log = LoggerFactory.getLogger(EReceiptIntegration.class);
    /** Kolumna `erx_key char(44)`: inny format klucza nie zostalby zapisany. */
    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Z0-9]{44}");

    private final EReceiptProperties properties;
    private final EReceiptClient client;
    private final PrescriptionFhirMapper mapper;
    private final PrescriptionRepository prescriptions;
    private final OutboxRetrySupport outbox;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    EReceiptIntegration(EReceiptProperties properties, EReceiptClient client, PrescriptionFhirMapper mapper,
            PrescriptionRepository prescriptions, OutboxRetrySupport outbox,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.client = client;
        this.mapper = mapper;
        this.prescriptions = prescriptions;
        this.outbox = outbox;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onIssued(PrescriptionIssued event) {
        if (!properties.enabled() || event.kind() != PrescriptionKind.E_PRESCRIPTION) {
            return;
        }
        outbox.enqueue(OutboxIntegration.ERECEIPT, OutboxOperation.SUBMIT, event.prescriptionId());
        trySubmit(event.prescriptionId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCancelled(PrescriptionCancelled event) {
        if (!properties.enabled() || event.actorId() == null) {
            return;
        }
        UUID id = event.prescriptionId();
        try {
            record Payload(String eRxKey, String json) {
            }
            Payload payload = readTx.execute(tx -> prescriptions.findById(id)
                    .filter(p -> p.getKind() == PrescriptionKind.E_PRESCRIPTION)
                    .map(p -> new Payload(p.getERxKey(),
                            mapper.encode(mapper.toResource(p, PrescriptionStatus.CANCELLED))))
                    .orElse(null));
            if (payload != null) {
                client.updateStatus(payload.eRxKey(), payload.json());
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                log.info("e-receipt nie zna recepty {} (klucz lokalny/dane mock); anulowanie tylko w HIS", id);
            } else {
                log.warn("e-receipt odrzucil anulowanie recepty {}: HTTP {}", id, e.getStatusCode().value());
            }
        } catch (RuntimeException e) {
            log.warn("Nie przekazano anulowania recepty {} do e-receipt: {}", id, e.getMessage());
        }
    }

    @Override
    public OutboxIntegration integration() {
        return OutboxIntegration.ERECEIPT;
    }

    @Override
    public boolean retry(OutboxOperation operation, UUID entityId) {
        // Jedyna operacja ponawiana przez outbox jest SUBMIT (patrz klasa-javadoc - PUT anulowania poza zakresem).
        return operation == OutboxOperation.SUBMIT && trySubmit(entityId);
    }

    /**
     * Pojedyncza proba `POST`, wspolna dla sciezki synchronicznej i schedulera retry. Zwraca `true`, gdy wysylke
     * mozna uznac za zakonczona (sukces albo stan juz nieaktualny - "superseded"), `false` gdy nalezy ponowic.
     */
    boolean trySubmit(UUID id) {
        record Snapshot(PrescriptionStatus status, String eRxKey, String payload) {
        }
        try {
            Snapshot snapshot = readTx.execute(tx -> prescriptions.findById(id)
                    .map(p -> new Snapshot(p.getStatus(), p.getERxKey(),
                            mapper.encode(mapper.toResource(p, PrescriptionStatus.ISSUED))))
                    .orElse(null));
            if (snapshot == null) {
                outbox.markSuperseded(OutboxIntegration.ERECEIPT, OutboxOperation.SUBMIT, id);
                return true;
            }
            if (!snapshot.status().isOpen()) {
                // Recepta juz nie jest "zywa" (np. anulowana zanim POST w ogole dotarl) - wyslanie teraz tylko
                // wprowadziloby e-receipt w rozjazd ze stanem HIS (aktywna recepta, ktora w HIS jest anulowana).
                // `onIssued` wysyla zawsze status ISSUED (recepta dopiero co wystawiona), wiec jedyny sposob na
                // "nie-open" status w tym miejscu to zmiana po wystawieniu, zanim retry zdazyl wystartowac.
                outbox.markSuperseded(OutboxIntegration.ERECEIPT, OutboxOperation.SUBMIT, id);
                return true;
            }
            Optional<String> key = mapper.eRxKeyOf(client.submit(snapshot.payload()));
            if (key.isEmpty() || !KEY_FORMAT.matcher(key.get()).matches()) {
                log.warn("e-receipt nie zwrocil poprawnego eRxKey dla recepty {}; zostaje klucz lokalny", id);
                outbox.markSuperseded(OutboxIntegration.ERECEIPT, OutboxOperation.SUBMIT, id);
                return true;
            }
            Integer updated = writeTx.execute(tx -> prescriptions.updateERxKeyIfStillLocal(id, snapshot.eRxKey(), key.get()));
            if (updated != null && updated == 0) {
                log.info("Recepta {} juz ma inny eRxKey niz przy odczycie - retry pominiety (wyscig rozstrzygniety)", id);
            } else {
                reconcileAfterKeySwap(id, key.get());
            }
            outbox.markSucceeded(OutboxIntegration.ERECEIPT, OutboxOperation.SUBMIT, id);
            return true;
        } catch (RuntimeException e) {
            log.warn("Nie przekazano recepty {} do e-receipt (zostaje klucz lokalny): {}", id, e.getMessage());
            outbox.recordFailure(OutboxIntegration.ERECEIPT, OutboxOperation.SUBMIT, id, e.getMessage());
            return false;
        }
    }

    /**
     * Po udanej podmianie klucza sprawdza, czy recepta nie zostala anulowana w HIS, zanim e-receipt poznal jej
     * istnienie (PUT anulowania wczesniej dostalby 404, patrz {@link #onCancelled}) - jesli tak, wysyla PUT
     * anulowania teraz, nowym kluczem, zeby e-receipt nie trzymal recepty jako aktywnej mimo anulowania w HIS.
     */
    private void reconcileAfterKeySwap(UUID id, String eRxKey) {
        // Naprawiony blad: budowanie payloadu (mapper.toResource czyta kolekcje `items`, LAZY) MUSI nastapic
        // wewnatrz tej samej transakcji odczytu co findById - encja odlaczona od sesji poza `readTx.execute`
        // rzuca LazyInitializationException przy pierwszym dostepie do `items` (patrz wzorzec `Payload` w
        // `onCancelled` nizej - encja nigdy nie powinna "uciekac" poza granice transakcji, w ktorej zyje).
        String payload = readTx.execute(tx -> prescriptions.findById(id)
                .filter(p -> p.getStatus() == PrescriptionStatus.CANCELLED)
                .map(p -> mapper.encode(mapper.toResource(p, PrescriptionStatus.CANCELLED)))
                .orElse(null));
        if (payload == null) {
            return;
        }
        try {
            client.updateStatus(eRxKey, payload);
        } catch (RuntimeException e) {
            log.warn("Rekoncyliacja anulowania recepty {} po opoznionym POST nie powiodla sie: {}", id, e.getMessage());
        }
    }
}
