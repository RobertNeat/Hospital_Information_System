package robert_neat.elaboratory.fhir;

import java.util.Date;
import java.util.TimeZone;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import robert_neat.elaboratory.order.LabItem;
import robert_neat.elaboratory.order.LabOrder;
import robert_neat.elaboratory.order.Observation;
import robert_neat.elaboratory.order.ResultEntry;

/** Wynik e-laboratory -> `DiagnosticReport` R4 z obserwacjami `contained` (wysylany do HIS). */
@Component
public class DiagnosticReportMapper {

    private final FhirContext fhir;

    public DiagnosticReportMapper(FhirContext fhir) {
        this.fhir = fhir;
    }

    public String encode(LabOrder order, ResultEntry entry) {
        return fhir.newJsonParser().encodeResourceToString(toResource(order, entry));
    }

    DiagnosticReport toResource(LabOrder order, ResultEntry entry) {
        DiagnosticReport report = new DiagnosticReport();
        report.setStatus(entry.getStatus().fhir());
        String testName = order.getItems().stream().filter(i -> i.testCode().equals(entry.getTestCode()))
                .map(LabItem::testName).findFirst().orElse(entry.getTestCode());
        report.getCode().addCoding().setSystem(FhirSystems.LAB_TEST).setCode(entry.getTestCode())
                .setDisplay(testName);
        if (order.getPatientRef() != null) {
            report.setSubject(new Reference(order.getPatientRef()));
        }
        report.addBasedOn(new Reference("ServiceRequest/" + order.getHisOrderId()));
        report.setEffective(new DateTimeType(Date.from(entry.getCollectedAt()), TemporalPrecisionEnum.SECOND,
                TimeZone.getTimeZone("UTC")));
        report.setIssuedElement(new InstantType(Date.from(entry.getResultedAt()), TemporalPrecisionEnum.SECOND,
                TimeZone.getTimeZone("UTC")));
        report.addPerformer(new Reference().setDisplay(entry.getPerformer()));
        if (entry.getComment() != null) {
            report.setConclusion(entry.getComment());
        }
        int index = 0;
        for (Observation o : entry.getObservations()) {
            org.hl7.fhir.r4.model.Observation obs = new org.hl7.fhir.r4.model.Observation();
            obs.setId("obs" + ++index);
            obs.setStatus(org.hl7.fhir.r4.model.Observation.ObservationStatus.FINAL);
            obs.getCode().addCoding().setSystem(FhirSystems.LAB_ANALYTE).setCode(o.analyteCode())
                    .setDisplay(o.analyteName());
            if (o.numeric() != null) {
                obs.setValue(new Quantity().setValue(o.numeric()).setUnit(o.unit()));
            } else {
                obs.setValue(new StringType(o.text()));
            }
            if (o.flag() != null) {
                obs.addInterpretation().addCoding().setSystem(FhirSystems.OBSERVATION_INTERPRETATION)
                        .setCode(o.flag());
            }
            if (o.low() != null || o.high() != null) {
                var range = obs.addReferenceRange();
                if (o.low() != null) {
                    range.setLow(new Quantity().setValue(o.low()).setUnit(o.unit()));
                }
                if (o.high() != null) {
                    range.setHigh(new Quantity().setValue(o.high()).setUnit(o.unit()));
                }
            }
            report.addContained(obs);
            report.addResult(new Reference("#" + obs.getIdElement().getIdPart()));
        }
        return report;
    }
}
