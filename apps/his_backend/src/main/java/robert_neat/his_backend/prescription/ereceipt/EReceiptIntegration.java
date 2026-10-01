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

import robert_neat.his_backend.prescription.PrescriptionKind;
import robert_neat.his_backend.prescription.PrescriptionRepository;
import robert_neat.his_backend.prescription.PrescriptionStatus;
import robert_neat.his_backend.prescription.events.PrescriptionCancelled;
import robert_neat.his_backend.prescription.events.PrescriptionIssued;

/**
 * Integracja z e-receipt na zdarzeniach domenowych, WYLACZNIE po commicie (`AFTER_COMMIT`): wystawienie i anulowanie
 * recepty nigdy nie zalezy od e-receipt - kazdy blad (siec, HTTP, nieoczekiwana odpowiedz) jest tylko logowany.
 * Do e-receipt trafiaja tylko recepty `e_prescription` (zlecenia szpitalne zostaja wewnetrzne).
 * <ul>
 *   <li>`PrescriptionIssued`: POST recepty, zapis zwroconego `eRxKey` w osobnej transakcji (`REQUIRES_NEW`, JPQL bez
 *       zmiany `version`). Odpowiedz REST wystawienia niesie jeszcze klucz lokalny - nowy klucz widac przy kolejnym odczycie.</li>
 *   <li>`PrescriptionCancelled` z aktorem (anulowanie w HIS): PUT stanu do e-receipt. Zdarzenie bez aktora (zmiana
 *       przyszla z e-receipt) nie jest odsylane - brak petli.</li>
 * </ul>
 * Wywolanie jest synchroniczne (limity czasu z konfiguracji), poza transakcja bazodanowa.
 */
@Component
public class EReceiptIntegration {

    private static final Logger log = LoggerFactory.getLogger(EReceiptIntegration.class);
    /** Kolumna `erx_key char(44)`: inny format klucza nie zostalby zapisany. */
    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Z0-9]{44}");

    private final EReceiptProperties properties;
    private final EReceiptClient client;
    private final PrescriptionFhirMapper mapper;
    private final PrescriptionRepository prescriptions;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    EReceiptIntegration(EReceiptProperties properties, EReceiptClient client, PrescriptionFhirMapper mapper,
            PrescriptionRepository prescriptions, PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.client = client;
        this.mapper = mapper;
        this.prescriptions = prescriptions;
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
        UUID id = event.prescriptionId();
        try {
            String payload = readTx.execute(tx -> prescriptions.findById(id)
                    .map(p -> mapper.encode(mapper.toResource(p, PrescriptionStatus.ISSUED))).orElse(null));
            if (payload == null) {
                return;
            }
            Optional<String> key = mapper.eRxKeyOf(client.submit(payload));
            if (key.isEmpty() || !KEY_FORMAT.matcher(key.get()).matches()) {
                log.warn("e-receipt nie zwrocil poprawnego eRxKey dla recepty {}; zostaje klucz lokalny", id);
                return;
            }
            writeTx.executeWithoutResult(tx -> prescriptions.updateERxKey(id, key.get()));
        } catch (RuntimeException e) {
            log.warn("Nie przekazano recepty {} do e-receipt (zostaje klucz lokalny): {}", id, e.getMessage());
        }
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
}
