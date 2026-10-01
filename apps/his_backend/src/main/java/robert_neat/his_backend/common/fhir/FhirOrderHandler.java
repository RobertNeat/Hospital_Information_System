package robert_neat.his_backend.common.fhir;

import java.util.Optional;

import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.ServiceRequest;

/**
 * Obsluga zlecen i wynikow jednego modulu (laboratorium, obrazowanie) pod wspolnymi sciezkami FHIR
 * `/fhir/ServiceRequest` i `/fhir/DiagnosticReport` (te same typy zasobow, wiec jeden kontroler kieruje zadanie do
 * wlasciwego modulu: zlecenie wg id, raport wg systemu kodu badania).
 */
public interface FhirOrderHandler {

    /** Wynik zapisu raportu: zasob odpowiedzi i informacja, czy powstal nowy wynik (false = powtorzony raport). */
    record Recorded(DiagnosticReport report, boolean created) {
    }

    /** Czy zlecenie o tym id nalezy do modulu (zly format id = nie). */
    boolean ownsOrder(String orderId);

    ServiceRequest readOrder(String orderId);

    ServiceRequest applyOrderStatus(String orderId, ServiceRequest request);

    /** Czy raport dotyczy badania tego modulu (system kodu `DiagnosticReport.code`). */
    boolean handlesReport(DiagnosticReport report);

    Recorded recordReport(DiagnosticReport report);

    Optional<DiagnosticReport> findReport(String resultId);
}
