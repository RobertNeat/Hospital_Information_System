package robert_neat.his_backend.imaging.eimg;

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
import robert_neat.his_backend.imaging.ImagingOrderRepository;
import robert_neat.his_backend.imaging.events.ImagingOrderPlaced;
import robert_neat.his_backend.imaging.events.ImagingOrderStatusChanged;

/**
 * Integracja z e-imaging na zdarzeniach domenowych, WYLACZNIE po commicie (`AFTER_COMMIT`): zlecanie i zmiana statusu
 * nigdy nie zalezy od e-imaging - kazdy blad (siec, HTTP) jest tylko logowany, bez ponawiania.
 * <ul>
 *   <li>`ImagingOrderPlaced`: POST zlecenia (`ServiceRequest` z badaniem obrazowym).</li>
 *   <li>`ImagingOrderStatusChanged` z aktorem (zmiana w HIS, dowolny docelowy status - w tym anulowanie): PUT stanu.
 *       Zdarzenie bez aktora (zmiana przyszla z e-imaging albo automatyczne `completed` po wyniku) nie jest odsylane -
 *       brak petli.</li>
 * </ul>
 * Wywolanie jest synchroniczne (limity czasu z konfiguracji), poza transakcja bazodanowa.
 */
@Component
public class EImgIntegration {

    private static final Logger log = LoggerFactory.getLogger(EImgIntegration.class);

    private final EImgProperties properties;
    private final EImgClient client;
    private final ImagingFhirMapper mapper;
    private final ImagingOrderRepository orders;
    private final TransactionTemplate readTx;

    EImgIntegration(EImgProperties properties, EImgClient client, ImagingFhirMapper mapper, ImagingOrderRepository orders,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.client = client;
        this.mapper = mapper;
        this.orders = orders;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTx.setReadOnly(true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPlaced(ImagingOrderPlaced event) {
        if (!properties.enabled()) {
            return;
        }
        UUID id = event.orderId();
        try {
            String payload = readTx.execute(tx -> orders.findById(id)
                    .map(o -> mapper.encode(mapper.toServiceRequest(o, o.getStatus()))).orElse(null));
            if (payload != null) {
                client.submit(payload);
            }
        } catch (RuntimeException e) {
            log.warn("Nie przekazano zlecenia obrazowego {} do e-imaging: {}", id, e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onStatusChanged(ImagingOrderStatusChanged event) {
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
                log.info("e-imaging nie zna zlecenia {} (dane mock/zlecenie sprzed integracji); zmiana statusu tylko w HIS", id);
            } else {
                log.warn("e-imaging odrzucilo zmiane statusu zlecenia {} na '{}': HTTP {}", id, status.wire(),
                        e.getStatusCode().value());
            }
        } catch (RuntimeException e) {
            log.warn("Nie przekazano zmiany statusu zlecenia {} na '{}' do e-imaging: {}", id, status.wire(),
                    e.getMessage());
        }
    }
}
