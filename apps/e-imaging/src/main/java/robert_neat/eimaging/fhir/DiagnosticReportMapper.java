package robert_neat.eimaging.fhir;

import java.util.Date;
import java.util.TimeZone;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import robert_neat.eimaging.order.ImagingOrder;
import robert_neat.eimaging.order.ResultEntry;

/** Wynik e-imaging -> `DiagnosticReport` R4 (opis i flaga krytyczna jako rozszerzenia; wysylany do HIS). */
@Component
public class DiagnosticReportMapper {

    private final FhirContext fhir;

    public DiagnosticReportMapper(FhirContext fhir) {
        this.fhir = fhir;
    }

    public String encode(ImagingOrder order, ResultEntry entry) {
        return fhir.newJsonParser().encodeResourceToString(toResource(order, entry));
    }

    DiagnosticReport toResource(ImagingOrder order, ResultEntry entry) {
        DiagnosticReport report = new DiagnosticReport();
        report.setStatus(entry.getStatus().fhir());
        report.getCode().addCoding().setSystem(FhirSystems.IMAGING_EXAM).setCode(order.getExamCode())
                .setDisplay(order.getExamName());
        if (order.getPatientRef() != null) {
            report.setSubject(new Reference(order.getPatientRef()));
        }
        report.addBasedOn(new Reference("ServiceRequest/" + order.getHisOrderId()));
        report.setEffective(new DateTimeType(Date.from(entry.getPerformedAt()), TemporalPrecisionEnum.SECOND,
                TimeZone.getTimeZone("UTC")));
        report.setIssuedElement(new InstantType(Date.from(entry.getReportedAt()), TemporalPrecisionEnum.SECOND,
                TimeZone.getTimeZone("UTC")));
        report.addPerformer(new Reference().setDisplay(entry.getRadiologist()));
        report.setConclusion(entry.getConclusion());
        report.addExtension(new Extension(FhirSystems.IMAGING_FINDINGS_EXTENSION,
                new StringType(entry.getFindings())));
        report.addExtension(new Extension(FhirSystems.IMAGING_CRITICAL_EXTENSION,
                new BooleanType(entry.isCritical())));
        return report;
    }
}
