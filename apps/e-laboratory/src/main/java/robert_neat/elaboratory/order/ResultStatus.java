package robert_neat.elaboratory.order;

import java.util.Arrays;
import java.util.Optional;

import org.hl7.fhir.r4.model.DiagnosticReport.DiagnosticReportStatus;

/** Status wyniku (kody zgodne z `LabResultStatus` w HIS) i odpowiednik R4 `DiagnosticReport.status`. */
public enum ResultStatus {
    PRELIMINARY("preliminary", DiagnosticReportStatus.PRELIMINARY),
    FINAL("final", DiagnosticReportStatus.FINAL),
    CORRECTED("corrected", DiagnosticReportStatus.CORRECTED);

    private final String wire;
    private final DiagnosticReportStatus fhir;

    ResultStatus(String wire, DiagnosticReportStatus fhir) {
        this.wire = wire;
        this.fhir = fhir;
    }

    public String wire() {
        return wire;
    }

    public DiagnosticReportStatus fhir() {
        return fhir;
    }

    /** Wynik zatwierdzony (`final` lub korekta). */
    public boolean isFinalised() {
        return this != PRELIMINARY;
    }

    public static Optional<ResultStatus> fromWire(String code) {
        return Arrays.stream(values()).filter(s -> s.wire.equals(code)).findFirst();
    }
}
