package robert_neat.his_backend.prescription.ereceipt;

import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestIntent;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestStatus;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import robert_neat.his_backend.common.fhir.FhirException;
import robert_neat.his_backend.common.fhir.FhirSystems;
import robert_neat.his_backend.prescription.DosageInstruction;
import robert_neat.his_backend.prescription.Prescription;
import robert_neat.his_backend.prescription.PrescriptionItem;
import robert_neat.his_backend.prescription.PrescriptionStatus;

/**
 * Recepta HIS <-> `MedicationRequest` R4 (HAPI). Zasob nie zawiera danych osobowych pacjenta (tylko referencje
 * `Patient/{id}`, `Practitioner/{id}`). Dokladny status HIS niesie rozszerzenie {@link FhirSystems#STATUS_EXTENSION},
 * bo status R4 nie rozroznia `issued` i `partially_dispensed`.
 */
@Component
public class PrescriptionFhirMapper {

    private final FhirContext fhir;

    public PrescriptionFhirMapper(FhirContext fhir) {
        this.fhir = fhir;
    }

    /** Wymagane pola recepty musza byc zaladowane (pozycje sa leniwe: wolac w transakcji). */
    public MedicationRequest toResource(Prescription p, PrescriptionStatus status) {
        MedicationRequest mr = new MedicationRequest();
        mr.setId(p.getERxKey());
        mr.addIdentifier(new Identifier().setSystem(FhirSystems.HIS_PRESCRIPTION_ID).setValue(p.getId().toString()));
        mr.addIdentifier(new Identifier().setSystem(FhirSystems.ERX_KEY).setValue(p.getERxKey()));
        mr.addIdentifier(new Identifier().setSystem(FhirSystems.ACCESS_CODE).setValue(p.getAccessCode()));
        mr.setStatus(fhirStatus(status));
        mr.addExtension(new Extension(FhirSystems.STATUS_EXTENSION, new StringType(status.wire())));
        mr.setIntent(MedicationRequestIntent.ORDER);
        mr.setSubject(new Reference("Patient/" + p.getPatientId()));
        mr.setRequester(new Reference("Practitioner/" + p.getPrescriberId()));
        mr.setAuthoredOnElement(new DateTimeType(Date.from(p.getIssuedAt()), TemporalPrecisionEnum.SECOND,
                TimeZone.getTimeZone("UTC")));
        mr.getMedicationCodeableConcept().setText(p.getItems().stream()
                .map(i -> i.getDrugName() + " " + i.getStrength()).collect(Collectors.joining("; ")));
        for (PrescriptionItem item : p.getItems()) {
            mr.addDosageInstruction(new Dosage().setText(item.getDrugName() + ": " + dosageText(item.getDosage())
                    + ", opak.: " + item.getQuantityPackages()));
        }
        mr.getDispenseRequest().getValidityPeriod()
                .setStartElement(new DateTimeType(p.getValidFrom().toString()))
                .setEndElement(new DateTimeType(p.getValidUntil().toString()));
        if (p.getNotes() != null) {
            mr.addNote().setText(p.getNotes());
        }
        return mr;
    }

    private static String dosageText(DosageInstruction d) {
        String dose = d.getDose().stripTrailingZeros().toPlainString();
        StringBuilder text = new StringBuilder(dose).append(' ').append(d.getDoseUnit()).append(' ')
                .append(d.getFrequency().wire()).append(' ').append(d.getRoute().wire()).append(", ")
                .append(d.getDurationDays()).append(" dni");
        if (d.isAsNeeded()) {
            text.append(", doraznie");
        }
        if (d.getInstructions() != null) {
            text.append(" (").append(d.getInstructions()).append(')');
        }
        return text.toString();
    }

    /** Status R4 dla statusu HIS (`partially_dispensed` -> `active`; dokladnosc w rozszerzeniu). */
    static MedicationRequestStatus fhirStatus(PrescriptionStatus status) {
        return switch (status) {
            case ISSUED, PARTIALLY_DISPENSED -> MedicationRequestStatus.ACTIVE;
            case DISPENSED -> MedicationRequestStatus.COMPLETED;
            case CANCELLED -> MedicationRequestStatus.CANCELLED;
            case EXPIRED -> MedicationRequestStatus.STOPPED;
        };
    }

    /** Docelowy status z zasobu: rozszerzenie (kod HIS), a bez niego status R4; inaczej 400. */
    public PrescriptionStatus statusOf(MedicationRequest mr) {
        Extension ext = mr.getExtensionByUrl(FhirSystems.STATUS_EXTENSION);
        if (ext != null && ext.getValue() != null) {
            String code = ext.getValue().primitiveValue();
            for (PrescriptionStatus s : PrescriptionStatus.values()) {
                if (s.wire().equals(code)) {
                    return s;
                }
            }
            throw FhirException.invalid("Nieznany status w rozszerzeniu " + FhirSystems.STATUS_EXTENSION + ": "
                    + code);
        }
        MedicationRequestStatus status = mr.getStatus();
        if (status == null) {
            throw FhirException.invalid("Brak statusu MedicationRequest");
        }
        return switch (status) {
            case ACTIVE -> PrescriptionStatus.ISSUED;
            case COMPLETED -> PrescriptionStatus.DISPENSED;
            case CANCELLED -> PrescriptionStatus.CANCELLED;
            case STOPPED -> PrescriptionStatus.EXPIRED;
            default -> throw FhirException.invalid("Nieobslugiwany status MedicationRequest: " + status.toCode());
        };
    }

    public Optional<String> identifier(MedicationRequest mr, String system) {
        return mr.getIdentifier().stream().filter(i -> system.equals(i.getSystem()))
                .map(Identifier::getValue).filter(v -> v != null && !v.isBlank()).findFirst();
    }

    public MedicationRequest parse(String json) {
        try {
            return fhir.newJsonParser().parseResource(MedicationRequest.class, json);
        } catch (RuntimeException e) {
            throw FhirException.invalid("Niepoprawny zasob MedicationRequest (JSON R4): " + e.getMessage());
        }
    }

    public String encode(MedicationRequest resource) {
        return fhir.newJsonParser().encodeResourceToString(resource);
    }

    /** Klucz `eRxKey` z odpowiedzi e-receipt: identyfikator {@link FhirSystems#ERX_KEY}, a w razie braku `id` zasobu. */
    public Optional<String> eRxKeyOf(String json) {
        MedicationRequest mr = parse(json);
        return identifier(mr, FhirSystems.ERX_KEY)
                .or(() -> Optional.ofNullable(mr.getIdElement().getIdPart()).filter(s -> !s.isBlank()));
    }
}
