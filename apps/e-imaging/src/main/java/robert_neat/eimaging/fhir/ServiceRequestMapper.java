package robert_neat.eimaging.fhir;

import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestIntent;
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestStatus;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import robert_neat.eimaging.order.ImagingOrder;
import robert_neat.eimaging.order.ImagingOrderDraft;
import robert_neat.eimaging.order.ImagingOrderException;
import robert_neat.eimaging.order.ImagingOrderException.Kind;
import robert_neat.eimaging.order.ImagingOrderStatus;

/** Odwzorowanie `ServiceRequest` (R4) <-> model e-imaging oraz (de)serializacja JSON. */
@Component
public class ServiceRequestMapper {

    private final FhirContext fhir;

    public ServiceRequestMapper(FhirContext fhir) {
        this.fhir = fhir;
    }

    public ServiceRequest parse(String json) {
        try {
            return fhir.newJsonParser().parseResource(ServiceRequest.class, json);
        } catch (RuntimeException e) {
            throw new ImagingOrderException(Kind.INVALID,
                    "Niepoprawny zasob ServiceRequest (JSON R4): " + e.getMessage());
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

    public String encode(Resource resource) {
        return fhir.newJsonParser().encodeResourceToString(resource);
    }

    /**
     * Zlecenie z HIS (POST). Wymagane: identyfikator zlecenia HIS, status `active` (etap z rozszerzenia: `ordered`,
     * `scheduled` albo `in_progress`) i kod badania.
     */
    public ImagingOrderDraft toDraft(ServiceRequest sr) {
        String hisId = identifier(sr, FhirSystems.HIS_IMAGING_ORDER_ID).orElseThrow(() -> new ImagingOrderException(
                Kind.INVALID, "Brak identyfikatora " + FhirSystems.HIS_IMAGING_ORDER_ID));
        if (sr.getStatus() != ServiceRequestStatus.ACTIVE) {
            throw new ImagingOrderException(Kind.INVALID, "Nowe zlecenie musi miec status 'active'");
        }
        ImagingOrderStatus status = statusOf(sr);
        if (status == ImagingOrderStatus.COMPLETED || status == ImagingOrderStatus.CANCELLED) {
            throw new ImagingOrderException(Kind.INVALID,
                    "Nowe zlecenie musi byc aktywne, a nie '" + status.wire() + "'");
        }
        Coding exam = coding(sr.getCode().getCoding(), FhirSystems.IMAGING_EXAM);
        if (exam == null || exam.getCode() == null) {
            throw new ImagingOrderException(Kind.INVALID, "Brak kodu badania (" + FhirSystems.IMAGING_EXAM + ")");
        }
        Coding modality = coding(sr.getOrderDetailFirstRep().getCoding(), FhirSystems.IMAGING_MODALITY);
        Coding laterality = coding(sr.getOrderDetailFirstRep().getCoding(), FhirSystems.IMAGING_LATERALITY);
        Extension contrast = sr.getExtensionByUrl(FhirSystems.IMAGING_CONTRAST_EXTENSION);
        Extension slot = sr.getExtensionByUrl(FhirSystems.IMAGING_SLOT_EXTENSION);
        return new ImagingOrderDraft(hisId, sr.getSubject().getReference(), sr.getRequester().getReference(),
                sr.getPriority() == null ? null : sr.getPriority().toCode(),
                sr.hasAuthoredOn() ? sr.getAuthoredOn().toInstant() : java.time.Instant.now(),
                sr.hasOccurrenceDateTimeType() ? sr.getOccurrenceDateTimeType().getValue().toInstant() : null,
                slot == null || slot.getValue() == null ? null : slot.getValue().primitiveValue(),
                exam.getCode(), exam.hasDisplay() ? exam.getDisplay() : exam.getCode(),
                modality == null ? null : modality.getCode(), laterality == null ? null : laterality.getCode(),
                sr.getBodySiteFirstRep().getText(),
                contrast != null && contrast.getValue() instanceof BooleanType b && b.booleanValue(),
                sr.getReasonCodeFirstRep().getText(), sr.getNote().stream().map(n -> n.getText()).toList(), status);
    }

    private static Coding coding(java.util.List<Coding> codings, String system) {
        return codings.stream().filter(c -> system.equals(c.getSystem())).findFirst().orElse(null);
    }

    /** Docelowy stan z zasobu: rozszerzenie (dokladny kod HIS), a bez niego status R4. */
    public ImagingOrderStatus statusOf(ServiceRequest sr) {
        Extension ext = sr.getExtensionByUrl(FhirSystems.IMAGING_STATUS_EXTENSION);
        if (ext != null && ext.getValue() != null) {
            String code = ext.getValue().primitiveValue();
            return ImagingOrderStatus.fromWire(code).orElseThrow(() -> new ImagingOrderException(Kind.INVALID,
                    "Nieznany status w rozszerzeniu: " + code));
        }
        return ImagingOrderStatus.fromFhir(sr.getStatus()).orElseThrow(() -> new ImagingOrderException(Kind.INVALID,
                "Nieobslugiwany status ServiceRequest: " + sr.getStatus()));
    }

    public Optional<String> identifier(ServiceRequest sr, String system) {
        return sr.getIdentifier().stream().filter(i -> system.equals(i.getSystem()))
                .map(Identifier::getValue).filter(v -> v != null && !v.isBlank()).findFirst();
    }

    /** Zasob z biezacego zlecenia; `status` nadpisywany (przekazanie planowanej zmiany do HIS). */
    public ServiceRequest toResource(ImagingOrder o, ImagingOrderStatus status) {
        ServiceRequest sr = new ServiceRequest();
        sr.setId(o.getHisOrderId());
        sr.addIdentifier(new Identifier().setSystem(FhirSystems.HIS_IMAGING_ORDER_ID).setValue(o.getHisOrderId()));
        sr.setStatus(status.fhir());
        sr.addExtension(new Extension(FhirSystems.IMAGING_STATUS_EXTENSION, new StringType(status.wire())));
        sr.setIntent(ServiceRequestIntent.ORDER);
        if (o.getPriority() != null) {
            try {
                sr.setPriority(ServiceRequest.ServiceRequestPriority.fromCode(o.getPriority()));
            } catch (Exception e) {
                // priorytet spoza R4 pomijany (tylko informacyjny)
            }
        }
        if (o.getPatientRef() != null) {
            sr.setSubject(new Reference(o.getPatientRef()));
        }
        if (o.getRequesterRef() != null) {
            sr.setRequester(new Reference(o.getRequesterRef()));
        }
        sr.setAuthoredOnElement(dateTime(o.getAuthoredOn()));
        if (o.getScheduledAt() != null) {
            sr.setOccurrence(dateTime(o.getScheduledAt()));
        }
        sr.getCode().addCoding().setSystem(FhirSystems.IMAGING_EXAM).setCode(o.getExamCode())
                .setDisplay(o.getExamName());
        sr.getCode().setText(o.getExamName());
        var detail = sr.addOrderDetail();
        if (o.getModality() != null) {
            detail.addCoding().setSystem(FhirSystems.IMAGING_MODALITY).setCode(o.getModality());
        }
        if (o.getLaterality() != null) {
            detail.addCoding().setSystem(FhirSystems.IMAGING_LATERALITY).setCode(o.getLaterality());
        }
        if (o.getBodyRegion() != null) {
            sr.addBodySite().setText(o.getBodyRegion());
        }
        sr.addExtension(new Extension(FhirSystems.IMAGING_CONTRAST_EXTENSION, new BooleanType(o.isContrast())));
        if (o.getSlotId() != null) {
            sr.addExtension(new Extension(FhirSystems.IMAGING_SLOT_EXTENSION, new StringType(o.getSlotId())));
        }
        if (o.getIndication() != null && !o.getIndication().isBlank()) {
            sr.addReasonCode().setText(o.getIndication());
        }
        o.getNotes().forEach(n -> sr.addNote().setText(n));
        return sr;
    }

    private static DateTimeType dateTime(java.time.Instant at) {
        return new DateTimeType(Date.from(at), TemporalPrecisionEnum.SECOND, TimeZone.getTimeZone("UTC"));
    }
}
