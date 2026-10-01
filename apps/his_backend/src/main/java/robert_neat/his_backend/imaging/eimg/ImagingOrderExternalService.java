package robert_neat.his_backend.imaging.eimg;

import java.time.Instant;
import java.util.UUID;

import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.catalog.ScheduleSlotRepository;
import robert_neat.his_backend.common.fhir.FhirException;
import robert_neat.his_backend.common.fhir.FhirSystems;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.imaging.ImagingOrder;
import robert_neat.his_backend.imaging.ImagingOrderRepository;
import robert_neat.his_backend.imaging.ImagingOrderStateMachine;
import robert_neat.his_backend.imaging.events.ImagingOrderStatusChanged;

/**
 * Zmiana stanu zlecenia obrazowego zainicjowana przez e-imaging (bez uzytkownika HIS: wpis historii bez autora, audyt
 * `updatedBy` = null, zdarzenie z `actorId = null`). Reguly jak dla laboratorium: powtorzenie biezacego stanu jest
 * idempotentne (200); `ordered` i `specimen_collected` nie sa zmiana (422); zlecenie w stanie koncowym lub przejscie
 * niedozwolone przez {@link ImagingOrderStateMachine} to 409; niezgodny identyfikator zlecenia w ciele to 422.
 * Anulowanie zwalnia zarezerwowany slot (jak anulowanie w HIS).
 */
@Service
@Transactional
public class ImagingOrderExternalService {

    static final String EXTERNAL_CANCEL_NOTE = "Anulowano w e-imaging";
    static final String EXTERNAL_CHANGE_NOTE = "Zmiana stanu w e-imaging";

    private final ImagingOrderRepository orders;
    private final ScheduleSlotRepository slots;
    private final ImagingFhirMapper mapper;
    private final ApplicationEventPublisher events;

    ImagingOrderExternalService(ImagingOrderRepository orders, ScheduleSlotRepository slots, ImagingFhirMapper mapper,
            ApplicationEventPublisher events) {
        this.orders = orders;
        this.slots = slots;
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
        ImagingOrder o = require(id);
        return mapper.toServiceRequest(o, o.getStatus());
    }

    public ServiceRequest applyStatus(String id, ServiceRequest request) {
        OrderStatus target = mapper.statusOf(request);
        ImagingOrder order = require(id);
        mapper.identifier(request, FhirSystems.HIS_IMAGING_ORDER_ID).ifPresent(value -> {
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
        if (target == OrderStatus.SPECIMEN_COLLECTED) {
            throw FhirException.unprocessable("Status 'specimen_collected' nie dotyczy zlecen obrazowych");
        }
        if (previous.isTerminal()) {
            throw FhirException.conflict("Zlecenie jest w stanie koncowym '" + previous.wire() + "'");
        }
        if (!ImagingOrderStateMachine.canTransition(previous, target)) {
            throw FhirException.conflict("Niedozwolone przejscie statusu zlecenia z '" + previous.wire() + "' na '"
                    + target.wire() + "'");
        }
        Instant now = Instant.now();
        String note = target == OrderStatus.CANCELLED ? EXTERNAL_CANCEL_NOTE : EXTERNAL_CHANGE_NOTE;
        order.applyExternalStatus(target, now, note);
        ImagingOrder saved = orders.saveAndFlush(order);
        if (target == OrderStatus.CANCELLED && saved.getSlotId() != null) {
            slots.findByIdForUpdate(saved.getSlotId()).ifPresent(slot -> {
                slot.release();
                slots.saveAndFlush(slot);
            });
        }
        events.publishEvent(new ImagingOrderStatusChanged(saved.getId(), saved.getPatientId(),
                saved.getOrderedById(), previous, target, now, null, note));
        return mapper.toServiceRequest(saved, saved.getStatus());
    }

    private ImagingOrder require(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw FhirException.notFound("Zlecenie '" + id + "' nie istnieje");
        }
        return orders.findById(uuid).orElseThrow(() -> FhirException.notFound("Zlecenie '" + id + "' nie istnieje"));
    }
}
