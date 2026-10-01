package robert_neat.his_backend.imaging.eimg;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DiagnosticReport.DiagnosticReportStatus;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestIntent;
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestPriority;
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestStatus;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import robert_neat.his_backend.common.fhir.FhirException;
import robert_neat.his_backend.common.fhir.FhirSystems;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.imaging.ImagingOrder;
import robert_neat.his_backend.imaging.ImagingResult;
import robert_neat.his_backend.imaging.ImagingResultStatus;
import robert_neat.his_backend.imaging.RecordImagingResultCommand;

/**
 * Zlecenie / wynik obrazowy HIS <-> `ServiceRequest` / `DiagnosticReport` R4 (HAPI). Zasoby nie zawieraja danych
 * osobowych pacjenta (tylko referencje `Patient/{id}`, `Practitioner/{id}`) ani listy kontrolnej bezpieczenstwa.
 * Dokladny status zlecenia niesie rozszerzenie {@link FhirSystems#IMAGING_STATUS_EXTENSION}; opis badania i flaga
 * krytycznego wyniku to rozszerzenia raportu (R4 nie ma na nie pol).
 */
@Component
public class ImagingFhirMapper {

    private final FhirContext fhir;

    public ImagingFhirMapper(FhirContext fhir) {
        this.fhir = fhir;
    }

    public String encode(org.hl7.fhir.r4.model.Resource resource) {
        return fhir.newJsonParser().encodeResourceToString(resource);
    }

    // --- ServiceRequest ---

    public ServiceRequest toServiceRequest(ImagingOrder o, OrderStatus status) {
        ServiceRequest sr = new ServiceRequest();
        sr.setId(o.getId().toString());
        sr.addIdentifier(new Identifier().setSystem(FhirSystems.HIS_IMAGING_ORDER_ID).setValue(o.getId().toString()));
        sr.setStatus(fhirStatus(status));
        sr.addExtension(new Extension(FhirSystems.IMAGING_STATUS_EXTENSION, new StringType(status.wire())));
        sr.setIntent(ServiceRequestIntent.ORDER);
        sr.setPriority(switch (o.getUrgency()) {
            case ROUTINE -> ServiceRequestPriority.ROUTINE;
            case URGENT -> ServiceRequestPriority.URGENT;
            case STAT -> ServiceRequestPriority.STAT;
        });
        sr.setSubject(new Reference("Patient/" + o.getPatientId()));
        sr.setRequester(new Reference("Practitioner/" + o.getOrderedById()));
        sr.setAuthoredOnElement(dateTime(o.getOrderedAt()));
        if (o.getScheduledAt() != null) {
            sr.setOccurrence(dateTime(o.getScheduledAt()));
        }
        sr.getCode().addCoding().setSystem(FhirSystems.IMAGING_EXAM).setCode(o.getExamCode())
                .setDisplay(o.getExamName());
        sr.getCode().setText(o.getExamName());
        var detail = sr.addOrderDetail();
        detail.addCoding().setSystem(FhirSystems.IMAGING_MODALITY).setCode(o.getModality().wire());
        detail.addCoding().setSystem(FhirSystems.IMAGING_LATERALITY).setCode(o.getLaterality().wire());
        sr.addBodySite().setText(o.getBodyRegion());
        sr.addExtension(new Extension(FhirSystems.IMAGING_CONTRAST_EXTENSION, new BooleanType(o.isContrast())));
        if (o.getSlotId() != null) {
            sr.addExtension(new Extension(FhirSystems.IMAGING_SLOT_EXTENSION,
                    new StringType(o.getSlotId().toString())));
        }
        sr.addReasonCode().setText(o.getClinicalIndication());
        if (o.getDiagnosisCode() != null) {
            sr.addReasonCode().addCoding().setSystem(o.getDiagnosisCode().system().wire())
                    .setCode(o.getDiagnosisCode().code()).setDisplay(o.getDiagnosisCode().display());
        }
        if (o.getClinicalQuestion() != null) {
            sr.addNote().setText(o.getClinicalQuestion());
        }
        return sr;
    }

    /** Status R4 dla statusu HIS (etapy aktywnego zlecenia -> `active`; dokladnosc w rozszerzeniu). */
    static ServiceRequestStatus fhirStatus(OrderStatus status) {
        return switch (status) {
            case ORDERED, SCHEDULED, IN_PROGRESS, SPECIMEN_COLLECTED -> ServiceRequestStatus.ACTIVE;
            case COMPLETED -> ServiceRequestStatus.COMPLETED;
            case CANCELLED -> ServiceRequestStatus.REVOKED;
        };
    }

    /** Docelowy status z zasobu: rozszerzenie (kod HIS), a bez niego status R4; inaczej 400. */
    public OrderStatus statusOf(ServiceRequest sr) {
        Extension ext = sr.getExtensionByUrl(FhirSystems.IMAGING_STATUS_EXTENSION);
        if (ext != null && ext.getValue() != null) {
            String code = ext.getValue().primitiveValue();
            for (OrderStatus s : OrderStatus.values()) {
                if (s.wire().equals(code)) {
                    return s;
                }
            }
            throw FhirException.invalid("Nieznany status w rozszerzeniu " + FhirSystems.IMAGING_STATUS_EXTENSION
                    + ": " + code);
        }
        ServiceRequestStatus status = sr.getStatus();
        if (status == null) {
            throw FhirException.invalid("Brak statusu ServiceRequest");
        }
        return switch (status) {
            case ACTIVE -> OrderStatus.ORDERED;
            case COMPLETED -> OrderStatus.COMPLETED;
            case REVOKED -> OrderStatus.CANCELLED;
            default -> throw FhirException.invalid("Nieobslugiwany status ServiceRequest: " + status.toCode());
        };
    }

    public Optional<String> identifier(ServiceRequest sr, String system) {
        return sr.getIdentifier().stream().filter(i -> system.equals(i.getSystem()))
                .map(Identifier::getValue).filter(v -> v != null && !v.isBlank()).findFirst();
    }

    // --- DiagnosticReport ---

    public DiagnosticReport toReport(ImagingResult r) {
        DiagnosticReport report = new DiagnosticReport();
        report.setId(r.getId().toString());
        report.setStatus(switch (r.getStatus()) {
            case PRELIMINARY -> DiagnosticReportStatus.PRELIMINARY;
            case FINAL -> DiagnosticReportStatus.FINAL;
        });
        report.getCode().addCoding().setSystem(FhirSystems.IMAGING_MODALITY).setCode(r.getModality().wire());
        report.getCode().setText(r.getExamName());
        report.setSubject(new Reference("Patient/" + r.getPatientId()));
        if (r.getOrderId() != null) {
            report.addBasedOn(new Reference("ServiceRequest/" + r.getOrderId()));
        }
        report.setEffective(dateTime(r.getPerformedAt()));
        report.setIssuedElement(new InstantType(Date.from(r.getReportedAt()), TemporalPrecisionEnum.SECOND,
                TimeZone.getTimeZone("UTC")));
        report.addPerformer(new Reference().setDisplay(r.getRadiologistName()));
        report.setConclusion(r.getConclusion());
        report.addExtension(new Extension(FhirSystems.IMAGING_FINDINGS_EXTENSION, new StringType(r.getFindings())));
        report.addExtension(new Extension(FhirSystems.IMAGING_CRITICAL_EXTENSION, new BooleanType(r.isCritical())));
        return report;
    }

    /** Kod badania z raportu (`urn:his:imaging-exam`); routing gwarantuje jego obecnosc. */
    public String examCode(DiagnosticReport report) {
        return report.getCode().getCoding().stream().filter(c -> FhirSystems.IMAGING_EXAM.equals(c.getSystem()))
                .map(Coding::getCode).findFirst()
                .orElseThrow(() -> FhirException.invalid("Brak kodu badania (" + FhirSystems.IMAGING_EXAM + ")"));
    }

    /**
     * Polecenie zapisu wyniku z raportu. Wymagane: `subject` (`Patient/{uuid}`), `basedOn` (`ServiceRequest/{uuid}`),
     * status (`preliminary`/`final`), `effectiveDateTime` (wykonanie) i `issued` (opis). Brakujacy opis lub wnioski
     * to 422 z serwisu zapisu; radiolog z `performer[0].display`.
     */
    public RecordImagingResultCommand toCommand(DiagnosticReport report) {
        UUID patientId = uuidRef(report.getSubject(), "Patient", "subject");
        if (report.getBasedOn().isEmpty()) {
            throw FhirException.invalid("Brak basedOn (ServiceRequest/{id zlecenia HIS})");
        }
        UUID orderId = uuidRef(report.getBasedOnFirstRep(), "ServiceRequest", "basedOn");
        ImagingResultStatus status = switch (report.getStatus() == null ? DiagnosticReportStatus.NULL
                : report.getStatus()) {
            case PRELIMINARY -> ImagingResultStatus.PRELIMINARY;
            case FINAL -> ImagingResultStatus.FINAL;
            default -> throw FhirException.invalid("Nieobslugiwany status DiagnosticReport (preliminary/final)");
        };
        if (!report.hasEffectiveDateTimeType() || !report.hasIssued()) {
            throw FhirException.invalid("Wymagane effectiveDateTime (wykonanie) i issued (opis)");
        }
        Instant performedAt = report.getEffectiveDateTimeType().getValue().toInstant();
        Instant reportedAt = report.getIssued().toInstant();
        String radiologist = report.getPerformer().isEmpty() ? null : report.getPerformerFirstRep().getDisplay();
        return new RecordImagingResultCommand(patientId, orderId, null, null, null, performedAt, reportedAt,
                radiologist, null, null, extensionString(report, FhirSystems.IMAGING_FINDINGS_EXTENSION),
                report.getConclusion(), status, null,
                "true".equals(extensionString(report, FhirSystems.IMAGING_CRITICAL_EXTENSION)));
    }

    private static String extensionString(DiagnosticReport report, String url) {
        Extension ext = report.getExtensionByUrl(url);
        return ext == null || ext.getValue() == null ? null : ext.getValue().primitiveValue();
    }

    private static UUID uuidRef(Reference ref, String type, String field) {
        String value = ref.getReference();
        String prefix = type + "/";
        if (value == null || !value.startsWith(prefix)) {
            throw FhirException.invalid("Wymagane " + field + " w formie " + prefix + "{uuid}");
        }
        try {
            return UUID.fromString(value.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            throw FhirException.invalid("Niepoprawny identyfikator w " + field + ": " + value);
        }
    }

    private static DateTimeType dateTime(Instant at) {
        return new DateTimeType(Date.from(at), TemporalPrecisionEnum.SECOND, TimeZone.getTimeZone("UTC"));
    }
}
