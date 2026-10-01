package robert_neat.his_backend.lab.elab;

import java.time.Instant;
import java.util.UUID;

import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.fhir.FhirException;
import robert_neat.his_backend.common.fhir.FhirSystems;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.lab.LabOrder;
import robert_neat.his_backend.lab.LabOrderRepository;
import robert_neat.his_backend.lab.LabOrderStateMachine;
import robert_neat.his_backend.lab.events.LabOrderStatusChanged;

/**
 * Zmiana stanu zlecenia laboratoryjnego zainicjowana przez e-laboratory (bez uzytkownika HIS: wpis historii bez
 * autora, audyt `updatedBy` = null, zdarzenie z `actorId = null`). Reguly: powtorzenie biezacego stanu jest
 * idempotentne (200); `ordered` nie jest zmiana (422); zlecenie w stanie koncowym (`completed`/`cancelled`) lub
 * przejscie niedozwolone przez {@link LabOrderStateMachine} to 409; niezgodny identyfikator zlecenia w ciele to 422.
 */
@Service
@Transactional
public class LabOrderExternalService {

    static final String EXTERNAL_CANCEL_NOTE = "Anulowano w e-laboratory";
    static final String EXTERNAL_CHANGE_NOTE = "Zmiana stanu w e-laboratory";

    private final LabOrderRepository orders;
    private final LabFhirMapper mapper;
    private final ApplicationEventPublisher events;

    LabOrderExternalService(LabOrderRepository orders, LabFhirMapper mapper, ApplicationEventPublisher events) {
        this.orders = orders;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public boolean exists(String id) {
        try {
            return orders.existsById(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public ServiceRequest read(String id) {
        LabOrder o = require(id);
        return mapper.toServiceRequest(o, o.getStatus());
    }

    public ServiceRequest applyStatus(String id, ServiceRequest request) {
        OrderStatus target = mapper.statusOf(request);
        LabOrder order = require(id);
        mapper.identifier(request, FhirSystems.HIS_LAB_ORDER_ID).ifPresent(value -> {
            if (!value.equals(order.getId().toString())) {
                throw FhirException.unprocessable("Identyfikator zlecenia nie zgadza sie z zleceniem " + id);
            }
        });
        OrderStatus previous = order.getStatus();
        if (previous == target) {
            return mapper.toServiceRequest(order, previous);
        }
        if (target == OrderStatus.ORDERED) {
            throw FhirException.unprocessable("Status 'ordered' nie jest zmiana stanu");
        }
        if (previous.isTerminal()) {
            throw FhirException.conflict("Zlecenie jest w stanie koncowym '" + previous.wire() + "'");
        }
        if (!LabOrderStateMachine.canTransition(previous, target)) {
            throw FhirException.conflict("Niedozwolone przejscie statusu zlecenia z '" + previous.wire() + "' na '"
                    + target.wire() + "'");
        }
        Instant now = Instant.now();
        String note = target == OrderStatus.CANCELLED ? EXTERNAL_CANCEL_NOTE : EXTERNAL_CHANGE_NOTE;
        order.applyExternalStatus(target, now, note);
        LabOrder saved = orders.saveAndFlush(order);
        events.publishEvent(new LabOrderStatusChanged(saved.getId(), saved.getPatientId(), saved.getOrderedById(),
                previous, target, now, null, note));
        return mapper.toServiceRequest(saved, saved.getStatus());
    }

    private LabOrder require(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw FhirException.notFound("Zlecenie '" + id + "' nie istnieje");
        }
        return orders.findById(uuid).orElseThrow(() -> FhirException.notFound("Zlecenie '" + id + "' nie istnieje"));
    }
}
