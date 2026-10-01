package robert_neat.his_backend.prescription.ereceipt;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hl7.fhir.r4.model.MedicationRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.fhir.FhirException;
import robert_neat.his_backend.common.fhir.FhirSystems;
import robert_neat.his_backend.prescription.Prescription;
import robert_neat.his_backend.prescription.PrescriptionRepository;
import robert_neat.his_backend.prescription.PrescriptionStatus;
import robert_neat.his_backend.prescription.events.PrescriptionCancelled;

/**
 * Zmiana stanu recepty zainicjowana przez e-receipt (bez uzytkownika HIS: audyt `updatedBy` = null, zdarzenie
 * anulowania z `actorId = null`). Reguly: stan zapisany musi byc "zywy" (`issued`/`partially_dispensed`); powtorzenie
 * biezacego stanu jest idempotentne (200); `issued` nie jest zmiana; recepta po terminie (wygasla efektywnie) przyjmuje
 * tylko `expired`; niezgodny `eRxKey` w ciele to 422. Wygasniecie nadal jest tez wyliczane przy odczycie.
 */
@Service
@Transactional
public class PrescriptionExternalService {

    static final String EXTERNAL_CANCEL_REASON = "Anulowano w e-receipt";

    private final PrescriptionRepository prescriptions;
    private final PrescriptionFhirMapper mapper;
    private final ApplicationEventPublisher events;

    PrescriptionExternalService(PrescriptionRepository prescriptions, PrescriptionFhirMapper mapper,
            ApplicationEventPublisher events) {
        this.prescriptions = prescriptions;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public MedicationRequest read(String id) {
        Prescription p = require(id);
        return mapper.toResource(p, p.effectiveStatus(Prescription.today()));
    }

    public MedicationRequest applyStatus(String id, MedicationRequest request) {
        PrescriptionStatus target = mapper.statusOf(request);
        Prescription p = require(id);
        mapper.identifier(request, FhirSystems.ERX_KEY).ifPresent(key -> {
            if (!key.equals(p.getERxKey())) {
                throw FhirException.unprocessable("eRxKey nie zgadza sie z recepta " + id);
            }
        });
        LocalDate today = Prescription.today();
        if (p.getStatus() == target) {
            return mapper.toResource(p, p.effectiveStatus(today));
        }
        if (target == PrescriptionStatus.ISSUED) {
            throw FhirException.unprocessable("Status 'issued' nie jest zmiana stanu");
        }
        if (!p.getStatus().isOpen()) {
            throw FhirException.conflict("Recepta jest w stanie koncowym '" + p.getStatus().wire() + "'");
        }
        PrescriptionStatus effective = p.effectiveStatus(today);
        if (effective == PrescriptionStatus.EXPIRED && target != PrescriptionStatus.EXPIRED) {
            throw FhirException.conflict("Recepta wygasla (termin waznosci minal)");
        }
        Instant now = Instant.now();
        p.applyExternalStatus(target, now, target == PrescriptionStatus.CANCELLED ? EXTERNAL_CANCEL_REASON : null);
        Prescription saved = prescriptions.saveAndFlush(p);
        if (target == PrescriptionStatus.CANCELLED) {
            events.publishEvent(new PrescriptionCancelled(saved.getId(), saved.getPatientId(),
                    saved.getPrescriberId(), null, EXTERNAL_CANCEL_REASON, now));
        }
        return mapper.toResource(saved, saved.effectiveStatus(today));
    }

    private Prescription require(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw FhirException.notFound("Recepta '" + id + "' nie istnieje");
        }
        return prescriptions.findById(uuid)
                .orElseThrow(() -> FhirException.notFound("Recepta '" + id + "' nie istnieje"));
    }
}
