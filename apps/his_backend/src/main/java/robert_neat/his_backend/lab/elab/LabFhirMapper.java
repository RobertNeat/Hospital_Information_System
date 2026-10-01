package robert_neat.his_backend.lab.elab;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DiagnosticReport.DiagnosticReportStatus;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestIntent;
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestPriority;
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestStatus;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import robert_neat.his_backend.catalog.LabAnalyteDefinition;
import robert_neat.his_backend.catalog.LabTest;
import robert_neat.his_backend.catalog.LabTestRepository;
import robert_neat.his_backend.common.fhir.FhirException;
import robert_neat.his_backend.common.fhir.FhirSystems;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.lab.LabObservation;
import robert_neat.his_backend.lab.LabOrder;
import robert_neat.his_backend.lab.LabOrderItem;
import robert_neat.his_backend.lab.LabResult;
import robert_neat.his_backend.lab.ObservationFlag;
import robert_neat.his_backend.lab.RecordLabResultCommand;
import robert_neat.his_backend.lab.RecordLabResultCommand.ObservationInput;
import robert_neat.his_backend.lab.ResultStatus;

/**
 * Zlecenie / wynik HIS <-> `ServiceRequest` / `DiagnosticReport` R4 (HAPI). Zasoby nie zawieraja danych osobowych
 * pacjenta (tylko referencje `Patient/{id}`, `Practitioner/{id}`). Dokladny status zlecenia niesie rozszerzenie
 * {@link FhirSystems#LAB_STATUS_EXTENSION} (status R4 nie rozroznia etapow aktywnego zlecenia). Wyniki: obserwacje
 * jako zasoby `contained` w `DiagnosticReport`.
 */
@Component
public class LabFhirMapper {

    private final FhirContext fhir;
    private final LabTestRepository tests;

    public LabFhirMapper(FhirContext fhir, LabTestRepository tests) {
        this.fhir = fhir;
        this.tests = tests;
    }

    // --- ServiceRequest ---

    /** Wolac w transakcji (pozycje zlecenia i anality katalogu sa leniwe). */
    public ServiceRequest toServiceRequest(LabOrder o, OrderStatus status) {
        ServiceRequest sr = new ServiceRequest();
        sr.setId(o.getId().toString());
        sr.addIdentifier(new Identifier().setSystem(FhirSystems.HIS_LAB_ORDER_ID).setValue(o.getId().toString()));
        sr.setStatus(fhirStatus(status));
        sr.addExtension(new Extension(FhirSystems.LAB_STATUS_EXTENSION, new StringType(status.wire())));
        sr.setIntent(ServiceRequestIntent.ORDER);
        sr.setPriority(switch (o.getUrgency()) {
            case ROUTINE -> ServiceRequestPriority.ROUTINE;
            case URGENT -> ServiceRequestPriority.URGENT;
            case STAT -> ServiceRequestPriority.STAT;
        });
        sr.setSubject(new Reference("Patient/" + o.getPatientId()));
        sr.setRequester(new Reference("Practitioner/" + o.getOrderedById()));
        sr.setAuthoredOnElement(dateTime(o.getOrderedAt()));
        sr.setOccurrence(dateTime(o.getPlannedCollectionAt()));
        sr.getCode().setText(o.getItems().stream().map(LabOrderItem::getTestCode)
                .collect(Collectors.joining("; ")));

        Map<String, LabTest> catalog = new HashMap<>();
        tests.findAllById(o.getItems().stream().map(LabOrderItem::getTestCode).toList())
                .forEach(t -> catalog.put(t.getCode(), t));
        for (LabOrderItem item : o.getItems()) {
            CodeableConcept detail = sr.addOrderDetail();
            detail.addCoding().setSystem(FhirSystems.LAB_TEST).setCode(item.getTestCode())
                    .setDisplay(item.getTestName());
            detail.addCoding().setSystem(FhirSystems.SPECIMEN_TYPE).setCode(item.getSpecimenType().wire());
            LabTest test = catalog.get(item.getTestCode());
            if (test != null) {
                test.getAnalytes().forEach(a -> detail.addExtension(analyteExtension(a)));
            }
        }
        if (o.isFasting()) {
            sr.setPatientInstruction("Na czczo");
        }
        if (o.getDiagnosisCode() != null) {
            sr.addReasonCode().addCoding().setSystem(o.getDiagnosisCode().system().wire())
                    .setCode(o.getDiagnosisCode().code()).setDisplay(o.getDiagnosisCode().display());
        }
        sr.addNote().setText(o.getClinicalInfo());
        if (o.getNotes() != null) {
            sr.addNote().setText(o.getNotes());
        }
        return sr;
    }

    private static Extension analyteExtension(LabAnalyteDefinition a) {
        Extension ext = new Extension(FhirSystems.LAB_ANALYTE_EXTENSION);
        ext.addExtension(new Extension("code", new StringType(a.getCode())));
        ext.addExtension(new Extension("name", new StringType(a.getName())));
        ext.addExtension(new Extension("unit", new StringType(a.getUnit())));
        if (a.getLow() != null) {
            ext.addExtension(new Extension("low", new StringType(a.getLow().toPlainString())));
        }
        if (a.getHigh() != null) {
            ext.addExtension(new Extension("high", new StringType(a.getHigh().toPlainString())));
        }
        return ext;
    }

    /** Status R4 dla statusu HIS (etapy aktywnego zlecenia -> `active`; dokladnosc w rozszerzeniu). */
    static ServiceRequestStatus fhirStatus(OrderStatus status) {
        return switch (status) {
            case ORDERED, SCHEDULED, SPECIMEN_COLLECTED, IN_PROGRESS -> ServiceRequestStatus.ACTIVE;
            case COMPLETED -> ServiceRequestStatus.COMPLETED;
            case CANCELLED -> ServiceRequestStatus.REVOKED;
        };
    }

    /** Docelowy status z zasobu: rozszerzenie (kod HIS), a bez niego status R4; inaczej 400. */
    public OrderStatus statusOf(ServiceRequest sr) {
        Extension ext = sr.getExtensionByUrl(FhirSystems.LAB_STATUS_EXTENSION);
        if (ext != null && ext.getValue() != null) {
            String code = ext.getValue().primitiveValue();
            for (OrderStatus s : OrderStatus.values()) {
                if (s.wire().equals(code)) {
                    return s;
                }
            }
            throw FhirException.invalid("Nieznany status w rozszerzeniu " + FhirSystems.LAB_STATUS_EXTENSION + ": "
                    + code);
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

    /** Wynik HIS jako raport (obserwacje `contained`); wolac w transakcji. */
    public DiagnosticReport toReport(LabResult r) {
        DiagnosticReport report = new DiagnosticReport();
        report.setId(r.getId().toString());
        report.setStatus(switch (r.getStatus()) {
            case PRELIMINARY -> DiagnosticReportStatus.PRELIMINARY;
            case FINAL -> DiagnosticReportStatus.FINAL;
            case CORRECTED -> DiagnosticReportStatus.CORRECTED;
        });
        report.getCode().addCoding().setSystem(FhirSystems.LAB_TEST).setCode(r.getTestCode())
                .setDisplay(r.getTestName());
        report.setSubject(new Reference("Patient/" + r.getPatientId()));
        if (r.getOrderId() != null) {
            report.addBasedOn(new Reference("ServiceRequest/" + r.getOrderId()));
        }
        report.setEffective(dateTime(r.getCollectedAt()));
        report.setIssuedElement(new InstantType(Date.from(r.getResultedAt()), TemporalPrecisionEnum.SECOND,
                TimeZone.getTimeZone("UTC")));
        report.addPerformer(new Reference().setDisplay(r.getPerformerName()));
        if (r.getComment() != null) {
            report.setConclusion(r.getComment());
        }
        int index = 0;
        for (LabObservation o : r.getObservations()) {
            Observation obs = new Observation();
            obs.setId("obs" + ++index);
            obs.setStatus(ObservationStatus.FINAL);
            obs.getCode().addCoding().setSystem(FhirSystems.LAB_ANALYTE).setCode(o.getAnalyteCode())
                    .setDisplay(o.getAnalyteName());
            if (o.getValueNumeric() != null) {
                obs.setValue(new Quantity().setValue(o.getValueNumeric()).setUnit(o.getUnit()));
            } else {
                obs.setValue(new StringType(o.getValueText()));
            }
            obs.addInterpretation().addCoding().setSystem(FhirSystems.OBSERVATION_INTERPRETATION)
                    .setCode(o.getFlag().wire());
            if (o.getReferenceRange() != null) {
                var range = obs.addReferenceRange();
                if (o.getReferenceRange().low() != null) {
                    range.setLow(new Quantity().setValue(o.getReferenceRange().low()).setUnit(o.getUnit()));
                }
                if (o.getReferenceRange().high() != null) {
                    range.setHigh(new Quantity().setValue(o.getReferenceRange().high()).setUnit(o.getUnit()));
                }
                if (o.getReferenceRange().text() != null) {
                    range.setText(o.getReferenceRange().text());
                }
            }
            report.addContained(obs);
            report.addResult(new Reference("#" + obs.getIdElement().getIdPart()));
        }
        return report;
    }

    /**
     * Polecenie zapisu wyniku z raportu. Wymagane: `subject` (`Patient/{uuid}`), `basedOn` (`ServiceRequest/{uuid}`),
     * kod badania, status, obserwacje. Jednostka i zakres z raportu sa pomijane (HIS zapisuje snapshot z katalogu).
     */
    public RecordLabResultCommand toCommand(DiagnosticReport report) {
        UUID patientId = uuidRef(report.getSubject(), "Patient", "subject");
        if (report.getBasedOn().isEmpty()) {
            throw FhirException.invalid("Brak basedOn (ServiceRequest/{id zlecenia HIS})");
        }
        UUID orderId = uuidRef(report.getBasedOnFirstRep(), "ServiceRequest", "basedOn");
        String testCode = report.getCode().getCoding().stream()
                .filter(c -> FhirSystems.LAB_TEST.equals(c.getSystem())).map(Coding::getCode).findFirst()
                .orElseThrow(() -> FhirException.invalid("Brak kodu badania (" + FhirSystems.LAB_TEST + ")"));
        ResultStatus status = switch (report.getStatus() == null ? DiagnosticReportStatus.NULL : report.getStatus()) {
            case PRELIMINARY -> ResultStatus.PRELIMINARY;
            case FINAL -> ResultStatus.FINAL;
            case CORRECTED -> ResultStatus.CORRECTED;
            default -> throw FhirException.invalid("Nieobslugiwany status DiagnosticReport (preliminary/final/corrected)");
        };
        if (!report.hasEffectiveDateTimeType() || !report.hasIssued()) {
            throw FhirException.invalid("Wymagane effectiveDateTime (pobranie) i issued (wynik)");
        }
        Instant collectedAt = report.getEffectiveDateTimeType().getValue().toInstant();
        Instant resultedAt = report.getIssued().toInstant();
        String performer = report.getPerformer().isEmpty() ? null : report.getPerformerFirstRep().getDisplay();

        List<ObservationInput> observations = new ArrayList<>();
        for (Reference ref : report.getResult()) {
            observations.add(observation(contained(report, ref)));
        }
        return new RecordLabResultCommand(patientId, orderId, null, testCode, collectedAt, resultedAt, status,
                performer, report.getConclusion(), observations);
    }

    private static Observation contained(DiagnosticReport report, Reference ref) {
        String id = localId(ref.getReference());
        for (Resource c : report.getContained()) {
            if (c instanceof Observation o && id != null && id.equals(localId(o.getId()))) {
                return o;
            }
        }
        throw FhirException.invalid("DiagnosticReport.result musi wskazywac obserwacje 'contained': "
                + ref.getReference());
    }

    /** Lokalny identyfikator zasobu `contained` bez prefiksu `#` (parser HAPI zachowuje go w `id`). */
    private static String localId(String id) {
        return id == null ? null : id.startsWith("#") ? id.substring(1) : id;
    }

    private static ObservationInput observation(Observation o) {
        String code = o.getCode().getCoding().stream().filter(c -> FhirSystems.LAB_ANALYTE.equals(c.getSystem()))
                .map(Coding::getCode).findFirst()
                .orElseThrow(() -> FhirException.invalid("Obserwacja bez kodu analitu (" + FhirSystems.LAB_ANALYTE
                        + ")"));
        ObservationFlag flag = null;
        String flagCode = o.getInterpretation().stream().flatMap(cc -> cc.getCoding().stream())
                .filter(c -> FhirSystems.OBSERVATION_INTERPRETATION.equals(c.getSystem())).map(Coding::getCode)
                .findFirst().orElse(null);
        if (flagCode != null) {
            for (ObservationFlag f : ObservationFlag.values()) {
                if (f.wire().equals(flagCode)) {
                    flag = f;
                }
            }
            if (flag == null) {
                throw FhirException.invalid("Nieznana flaga obserwacji: " + flagCode);
            }
        }
        if (o.hasValueQuantity()) {
            BigDecimal value = o.getValueQuantity().getValue();
            if (value == null) {
                throw FhirException.invalid("Obserwacja '" + code + "' bez wartosci");
            }
            return new ObservationInput(code, value, null, flag);
        }
        if (o.hasValueStringType()) {
            return new ObservationInput(code, null, o.getValueStringType().getValue(), flag);
        }
        throw FhirException.invalid("Obserwacja '" + code + "': wymagana wartosc (valueQuantity albo valueString)");
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

    public String encode(Resource resource) {
        return fhir.newJsonParser().encodeResourceToString(resource);
    }
}
