package robert_neat.his_backend.lab.elab;

import java.util.UUID;

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

import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.outbox.OutboxIntegration;
import robert_neat.his_backend.common.outbox.OutboxOperation;
import robert_neat.his_backend.common.outbox.OutboxRetryable;
import robert_neat.his_backend.common.outbox.OutboxRetrySupport;
import robert_neat.his_backend.lab.LabOrderRepository;
import robert_neat.his_backend.lab.events.LabOrderPlaced;
import robert_neat.his_backend.lab.events.LabOrderStatusChanged;

/**
 * Integracja z e-laboratory na zdarzeniach domenowych, WYLACZNIE po commicie (`AFTER_COMMIT`): zlecanie i zmiana
 * statusu nigdy nie zalezy od e-laboratory - kazdy blad (siec, HTTP) jest logowany, a nieudany `POST` trafia do
 * outboxa ({@link OutboxRetrySupport}, `IntegrationOutboxScheduler`) do ponowienia.
 * <ul>
 *   <li>`LabOrderPlaced`: synchroniczna proba `POST` zlecenia (`ServiceRequest` z pozycjami badan), ta sama sciezka
 *       co retry - {@link #trySubmit}. POST jest idempotentny wzgledem id zlecenia HIS (e-laboratory zwraca
 *       istniejacy zasob zamiast tworzyc duplikat), wiec ponowienie po czesciowym sukcesie jest bezpieczne.</li>
 *   <li>`LabOrderStatusChanged` z aktorem (zmiana w HIS, dowolny docelowy status - w tym anulowanie): PUT stanu.
 *       Zdarzenie bez aktora (zmiana przyszla z e-laboratory) nie jest odsylane - brak petli. PUT nie jest
 *       ponawiany przez outbox (poza zakresem: patrz `docs/deployment-and-config.md`).</li>
 * </ul>
 * Wywolanie jest synchroniczne (limity czasu z konfiguracji), poza transakcja bazodanowa.
 * <p>
 * Retry nigdy nie wysyla zlecenia, ktore w HIS jest juz w stanie koncowym (`completed`/`cancelled`) - {@link #trySubmit}
 * czyta aktualny status tuz przed `POST` i w takim przypadku oznacza wpis jako "superseded" bez wywolania, zeby
 * opozniony retry nie "wskrzesil" w e-laboratory zlecenia anulowanego w HIS w miedzyczasie.
 */
@Component
public class ELabIntegration implements OutboxRetryable {

    private static final Logger log = LoggerFactory.getLogger(ELabIntegration.class);

    private final ELabProperties properties;
    private final ELabClient client;
    private final LabFhirMapper mapper;
    private final LabOrderRepository orders;
    private final OutboxRetrySupport outbox;
    private final TransactionTemplate readTx;

    ELabIntegration(ELabProperties properties, ELabClient client, LabFhirMapper mapper, LabOrderRepository orders,
            OutboxRetrySupport outbox, PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.client = client;
        this.mapper = mapper;
        this.orders = orders;
        this.outbox = outbox;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTx.setReadOnly(true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPlaced(LabOrderPlaced event) {
        if (!properties.enabled()) {
            return;
        }
        outbox.enqueue(OutboxIntegration.ELAB, OutboxOperation.SUBMIT, event.orderId());
        trySubmit(event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onStatusChanged(LabOrderStatusChanged event) {
        if (!properties.enabled() || event.actorId() == null) {
            return;
        }
        UUID id = event.orderId();
        OrderStatus status = event.status();
        try {
            String payload = readTx.execute(tx -> orders.findById(id)
                    .map(o -> mapper.encode(mapper.toServiceRequest(o, status))).orElse(null));
            if (payload != null) {
                client.updateStatus(id.toString(), payload);
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                log.info("e-laboratory nie zna zlecenia {} (dane mock/zlecenie sprzed integracji); zmiana statusu tylko w HIS", id);
            } else {
                log.warn("e-laboratory odrzucilo zmiane statusu zlecenia {} na '{}': HTTP {}", id, status.wire(),
                        e.getStatusCode().value());
            }
        } catch (RuntimeException e) {
            log.warn("Nie przekazano zmiany statusu zlecenia {} na '{}' do e-laboratory: {}", id, status.wire(),
                    e.getMessage());
        }
    }

    @Override
    public OutboxIntegration integration() {
        return OutboxIntegration.ELAB;
    }

    @Override
    public boolean retry(OutboxOperation operation, UUID entityId) {
        return operation == OutboxOperation.SUBMIT && trySubmit(entityId);
    }

    /**
     * Pojedyncza proba `POST`, wspolna dla sciezki synchronicznej i schedulera retry. Zwraca `true`, gdy wysylke
     * mozna uznac za zakonczona (sukces albo stan juz nieaktualny - "superseded"), `false` gdy nalezy ponowic.
     */
    boolean trySubmit(UUID id) {
        try {
            record Snapshot(OrderStatus status, String payload) {
            }
            Snapshot snapshot = readTx.execute(tx -> orders.findById(id)
                    .map(o -> new Snapshot(o.getStatus(), mapper.encode(mapper.toServiceRequest(o, o.getStatus()))))
                    .orElse(null));
            if (snapshot == null) {
                outbox.markSuperseded(OutboxIntegration.ELAB, OutboxOperation.SUBMIT, id);
                return true;
            }
            if (snapshot.status().isTerminal()) {
                // Zlecenie anulowane/zakonczone w HIS, zanim POST dotarl - wyslanie teraz wprowadziloby
                // e-laboratory w rozjazd ze stanem HIS (aktywne zlecenie, ktore w HIS juz jest zamkniete).
                outbox.markSuperseded(OutboxIntegration.ELAB, OutboxOperation.SUBMIT, id);
                return true;
            }
            client.submit(snapshot.payload());
            outbox.markSucceeded(OutboxIntegration.ELAB, OutboxOperation.SUBMIT, id);
            return true;
        } catch (RuntimeException e) {
            log.warn("Nie przekazano zlecenia laboratoryjnego {} do e-laboratory: {}", id, e.getMessage());
            outbox.recordFailure(OutboxIntegration.ELAB, OutboxOperation.SUBMIT, id, e.getMessage());
            return false;
        }
    }
}
