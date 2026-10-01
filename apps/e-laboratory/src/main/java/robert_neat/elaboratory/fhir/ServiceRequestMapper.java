package robert_neat.elaboratory.fhir;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import org.hl7.fhir.r4.model.CodeableConcept;
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
import robert_neat.elaboratory.order.LabItem;
import robert_neat.elaboratory.order.LabItem.Analyte;
import robert_neat.elaboratory.order.LabOrder;
import robert_neat.elaboratory.order.LabOrderDraft;
import robert_neat.elaboratory.order.LabOrderException;
import robert_neat.elaboratory.order.LabOrderException.Kind;
import robert_neat.elaboratory.order.LabOrderStatus;

/** Odwzorowanie `ServiceRequest` (R4) <-> model e-laboratory oraz (de)serializacja JSON. */
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
            throw new LabOrderException(Kind.INVALID, "Niepoprawny zasob ServiceRequest (JSON R4): " + e.getMessage());
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

    /** Zlecenie z HIS (POST). Wymagane: identyfikator zlecenia HIS, status `active`, co najmniej jedno badanie. */
    public LabOrderDraft toDraft(ServiceRequest sr) {
        String hisId = identifier(sr, FhirSystems.HIS_LAB_ORDER_ID).orElseThrow(() -> new LabOrderException(
                Kind.INVALID, "Brak identyfikatora " + FhirSystems.HIS_LAB_ORDER_ID));
        if (sr.getStatus() != ServiceRequestStatus.ACTIVE) {
            throw new LabOrderException(Kind.INVALID, "Nowe zlecenie musi miec status 'active'");
        }
        List<LabItem> items = new ArrayList<>();
        for (CodeableConcept detail : sr.getOrderDetail()) {
            Coding test = coding(detail, FhirSystems.LAB_TEST);
            if (test == null || test.getCode() == null) {
                continue;
            }
            Coding specimen = coding(detail, FhirSystems.SPECIMEN_TYPE);
            List<Analyte> analytes = new ArrayList<>();
            for (Extension ext : detail.getExtensionsByUrl(FhirSystems.LAB_ANALYTE_EXTENSION)) {
                analytes.add(analyte(ext));
            }
            items.add(new LabItem(test.getCode(), test.hasDisplay() ? test.getDisplay() : test.getCode(),
                    specimen == null ? null : specimen.getCode(), analytes));
        }
        if (items.isEmpty()) {
            throw new LabOrderException(Kind.INVALID, "Brak badan w orderDetail (" + FhirSystems.LAB_TEST + ")");
        }
        Instant now = Instant.now();
        return new LabOrderDraft(hisId, sr.getSubject().getReference(), sr.getRequester().getReference(),
                sr.getPriority() == null ? null : sr.getPriority().toCode(),
                sr.hasAuthoredOn() ? sr.getAuthoredOn().toInstant() : now,
                sr.hasOccurrenceDateTimeType() ? sr.getOccurrenceDateTimeType().getValue().toInstant() : null,
                sr.hasPatientInstruction(), sr.getNote().stream().map(n -> n.getText()).toList(), items);
    }

    private static Analyte analyte(Extension ext) {
        return new Analyte(sub(ext, "code"), sub(ext, "name"), sub(ext, "unit"), decimal(sub(ext, "low")),
                decimal(sub(ext, "high")));
    }

    private static String sub(Extension ext, String url) {
        Extension e = ext.getExtensionByUrl(url);
        return e == null || e.getValue() == null ? null : e.getValue().primitiveValue();
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new LabOrderException(Kind.INVALID, "Niepoprawna wartosc liczbowa w definicji analitu: " + value);
        }
    }

    private static Coding coding(CodeableConcept concept, String system) {
        return concept.getCoding().stream().filter(c -> system.equals(c.getSystem())).findFirst().orElse(null);
    }

    /** Docelowy stan z zasobu: rozszerzenie (dokladny kod HIS), a bez niego status R4. */
    public LabOrderStatus statusOf(ServiceRequest sr) {
        Extension ext = sr.getExtensionByUrl(FhirSystems.LAB_STATUS_EXTENSION);
        if (ext != null && ext.getValue() != null) {
            String code = ext.getValue().primitiveValue();
            return LabOrderStatus.fromWire(code).orElseThrow(() -> new LabOrderException(Kind.INVALID,
                    "Nieznany status w rozszerzeniu: " + code));
        }
        return LabOrderStatus.fromFhir(sr.getStatus()).orElseThrow(() -> new LabOrderException(Kind.INVALID,
                "Nieobslugiwany status ServiceRequest: " + sr.getStatus()));
    }

    public Optional<String> identifier(ServiceRequest sr, String system) {
        return sr.getIdentifier().stream().filter(i -> system.equals(i.getSystem()))
                .map(Identifier::getValue).filter(v -> v != null && !v.isBlank()).findFirst();
    }

    /** Zasob z biezacego zlecenia; `status` nadpisywany (przekazanie planowanej zmiany do HIS). */
    public ServiceRequest toResource(LabOrder o, LabOrderStatus status) {
        ServiceRequest sr = new ServiceRequest();
        sr.setId(o.getHisOrderId());
        sr.addIdentifier(new Identifier().setSystem(FhirSystems.HIS_LAB_ORDER_ID).setValue(o.getHisOrderId()));
        sr.setStatus(status.fhir());
        sr.addExtension(new Extension(FhirSystems.LAB_STATUS_EXTENSION, new StringType(status.wire())));
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
        sr.setAuthoredOnElement(new DateTimeType(Date.from(o.getAuthoredOn()), TemporalPrecisionEnum.SECOND,
                TimeZone.getTimeZone("UTC")));
        for (LabItem item : o.getItems()) {
            CodeableConcept detail = sr.addOrderDetail();
            detail.addCoding().setSystem(FhirSystems.LAB_TEST).setCode(item.testCode()).setDisplay(item.testName());
            if (item.specimenType() != null) {
                detail.addCoding().setSystem(FhirSystems.SPECIMEN_TYPE).setCode(item.specimenType());
            }
            for (Analyte a : item.analytes()) {
                Extension ext = new Extension(FhirSystems.LAB_ANALYTE_EXTENSION);
                ext.addExtension(new Extension("code", new StringType(a.code())));
                ext.addExtension(new Extension("name", new StringType(a.name())));
                ext.addExtension(new Extension("unit", new StringType(a.unit())));
                if (a.low() != null) {
                    ext.addExtension(new Extension("low", new StringType(a.low().toPlainString())));
                }
                if (a.high() != null) {
                    ext.addExtension(new Extension("high", new StringType(a.high().toPlainString())));
                }
                detail.addExtension(ext);
            }
        }
        if (o.isFasting()) {
            sr.setPatientInstruction("Na czczo");
        }
        o.getNotes().forEach(n -> sr.addNote().setText(n));
        return sr;
    }
}
