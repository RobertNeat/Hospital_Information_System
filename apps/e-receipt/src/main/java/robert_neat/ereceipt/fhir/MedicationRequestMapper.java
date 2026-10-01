package robert_neat.ereceipt.fhir;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestIntent;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.context.FhirContext;
import robert_neat.ereceipt.prescription.Receipt;
import robert_neat.ereceipt.prescription.ReceiptDraft;
import robert_neat.ereceipt.prescription.ReceiptException;
import robert_neat.ereceipt.prescription.ReceiptException.Kind;
import robert_neat.ereceipt.prescription.ReceiptStatus;

/** Odwzorowanie `MedicationRequest` (R4) <-> model e-receipt oraz (de)serializacja JSON. */
@Component
public class MedicationRequestMapper {

    private final FhirContext fhir;

    public MedicationRequestMapper(FhirContext fhir) {
        this.fhir = fhir;
    }

    public MedicationRequest parse(String json) {
        try {
            return fhir.newJsonParser().parseResource(MedicationRequest.class, json);
        } catch (RuntimeException e) {
            throw new ReceiptException(Kind.INVALID, "Niepoprawny zasob MedicationRequest (JSON R4): "
                    + e.getMessage());
        }
    }

    /** Pierwszy komunikat `OperationOutcome.issue.diagnostics` (pusty, gdy cialo nie jest OperationOutcome). */
    public String diagnostics(String json) {
        try {
            return fhir.newJsonParser().parseResource(OperationOutcome.class, json).getIssue().stream()
                    .map(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
                    .filter(d -> d != null && !d.isBlank()).findFirst().orElse("");
        } catch (RuntimeException e) {
            return "";
        }
    }

    public String encode(MedicationRequest resource) {
        return fhir.newJsonParser().encodeResourceToString(resource);
    }

    /** Recepta z HIS (POST). Wymagane: identyfikator recepty w HIS, status `active`, lek. */
    public ReceiptDraft toDraft(MedicationRequest mr) {
        String hisId = identifier(mr, FhirSystems.HIS_PRESCRIPTION_ID)
                .orElseThrow(() -> new ReceiptException(Kind.INVALID,
                        "Brak identyfikatora " + FhirSystems.HIS_PRESCRIPTION_ID));
        if (mr.getStatus() != MedicationRequest.MedicationRequestStatus.ACTIVE) {
            throw new ReceiptException(Kind.INVALID, "Nowa recepta musi miec status 'active'");
        }
        String medication = mr.getMedicationCodeableConcept().getText();
        if (medication == null || medication.isBlank()) {
            throw new ReceiptException(Kind.INVALID, "Brak medicationCodeableConcept.text");
        }
        List<String> dosages = mr.getDosageInstruction().stream().map(Dosage::getText)
                .filter(t -> t != null && !t.isBlank()).toList();
        String note = mr.getNote().isEmpty() ? null : mr.getNoteFirstRep().getText();
        return new ReceiptDraft(hisId, identifier(mr, FhirSystems.ACCESS_CODE).orElse(null),
                mr.getSubject().getReference(), mr.getRequester().getReference(),
                mr.hasAuthoredOn() ? mr.getAuthoredOn().toInstant() : Instant.now(),
                date(mr.getDispenseRequest().getValidityPeriod().getStartElement()),
                date(mr.getDispenseRequest().getValidityPeriod().getEndElement()), medication, dosages, note);
    }

    /** Docelowy stan z zasobu: rozszerzenie (dokladny kod HIS), a bez niego status R4. */
    public ReceiptStatus statusOf(MedicationRequest mr) {
        Extension ext = mr.getExtensionByUrl(FhirSystems.STATUS_EXTENSION);
        if (ext != null && ext.getValue() != null) {
            String code = ext.getValue().primitiveValue();
            return ReceiptStatus.fromWire(code).orElseThrow(() -> new ReceiptException(Kind.INVALID,
                    "Nieznany status w rozszerzeniu: " + code));
        }
        return ReceiptStatus.fromFhir(mr.getStatus()).orElseThrow(() -> new ReceiptException(Kind.INVALID,
                "Nieobslugiwany status MedicationRequest: " + mr.getStatus()));
    }

    public Optional<String> identifier(MedicationRequest mr, String system) {
        return mr.getIdentifier().stream().filter(i -> system.equals(i.getSystem()))
                .map(Identifier::getValue).filter(v -> v != null && !v.isBlank()).findFirst();
    }

    /** Zasob z biezacego stanu recepty; `status` nadpisywany (przekazanie planowanej zmiany do HIS). */
    public MedicationRequest toResource(Receipt r, ReceiptStatus status) {
        MedicationRequest mr = new MedicationRequest();
        mr.setId(r.getErxKey());
        mr.addIdentifier(new Identifier().setSystem(FhirSystems.HIS_PRESCRIPTION_ID)
                .setValue(r.getHisPrescriptionId()));
        mr.addIdentifier(new Identifier().setSystem(FhirSystems.ERX_KEY).setValue(r.getErxKey()));
        if (r.getAccessCode() != null) {
            mr.addIdentifier(new Identifier().setSystem(FhirSystems.ACCESS_CODE).setValue(r.getAccessCode()));
        }
        mr.setStatus(status.fhir());
        mr.addExtension(new Extension(FhirSystems.STATUS_EXTENSION, new StringType(status.wire())));
        mr.setIntent(MedicationRequestIntent.ORDER);
        if (r.getPatientRef() != null) {
            mr.setSubject(new Reference(r.getPatientRef()));
        }
        if (r.getRequesterRef() != null) {
            mr.setRequester(new Reference(r.getRequesterRef()));
        }
        mr.setAuthoredOnElement(new DateTimeType(Date.from(r.getAuthoredOn()), TemporalPrecisionEnum.SECOND,
                TimeZone.getTimeZone("UTC")));
        mr.getMedicationCodeableConcept().setText(r.getMedication());
        r.getDosages().forEach(d -> mr.addDosageInstruction(new Dosage().setText(d)));
        if (r.getValidFrom() != null && r.getValidUntil() != null) {
            mr.getDispenseRequest().getValidityPeriod()
                    .setStartElement(new DateTimeType(r.getValidFrom().toString()))
                    .setEndElement(new DateTimeType(r.getValidUntil().toString()));
        }
        if (r.getNote() != null) {
            mr.addNote().setText(r.getNote());
        }
        return mr;
    }

    /** Daty okresu maja precyzje dnia: czytamy ISO `yyyy-MM-dd` bez przeliczen stref. */
    private static LocalDate date(DateTimeType element) {
        String value = element.getValueAsString();
        if (value == null || value.length() < 10) {
            throw new ReceiptException(Kind.INVALID, "Brak dispenseRequest.validityPeriod (start/end)");
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (RuntimeException e) {
            throw new ReceiptException(Kind.INVALID, "Niepoprawna data okresu waznosci: " + value);
        }
    }
}
