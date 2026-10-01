package robert_neat.his_backend.lab.elab;

import java.util.Optional;

import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.stereotype.Component;

import robert_neat.his_backend.common.fhir.FhirOrderHandler;
import robert_neat.his_backend.common.fhir.FhirSystems;

/** Zlecenia i wyniki laboratoryjne pod wspolnymi sciezkami FHIR (kod badania `urn:his:lab-test`). */
@Component
class LabFhirHandler implements FhirOrderHandler {

    private final LabOrderExternalService orders;
    private final LabResultExternalService results;

    LabFhirHandler(LabOrderExternalService orders, LabResultExternalService results) {
        this.orders = orders;
        this.results = results;
    }

    @Override
    public boolean ownsOrder(String orderId) {
        return orders.exists(orderId);
    }

    @Override
    public ServiceRequest readOrder(String orderId) {
        return orders.read(orderId);
    }

    @Override
    public ServiceRequest applyOrderStatus(String orderId, ServiceRequest request) {
        return orders.applyStatus(orderId, request);
    }

    @Override
    public boolean handlesReport(DiagnosticReport report) {
        return report.getCode().getCoding().stream().anyMatch(c -> FhirSystems.LAB_TEST.equals(c.getSystem()));
    }

    @Override
    public Recorded recordReport(DiagnosticReport report) {
        LabResultExternalService.Recorded recorded = results.record(report);
        return new Recorded(recorded.report(), recorded.created());
    }

    @Override
    public Optional<DiagnosticReport> findReport(String resultId) {
        return results.find(resultId);
    }
}
