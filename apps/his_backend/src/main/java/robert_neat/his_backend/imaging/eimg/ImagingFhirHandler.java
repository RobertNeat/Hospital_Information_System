package robert_neat.his_backend.imaging.eimg;

import java.util.Optional;

import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.stereotype.Component;

import robert_neat.his_backend.common.fhir.FhirOrderHandler;
import robert_neat.his_backend.common.fhir.FhirSystems;

/** Zlecenia i wyniki obrazowe pod wspolnymi sciezkami FHIR (kod badania `urn:his:imaging-exam`). */
@Component
class ImagingFhirHandler implements FhirOrderHandler {

    private final ImagingOrderExternalService orders;
    private final ImagingResultExternalService results;

    ImagingFhirHandler(ImagingOrderExternalService orders, ImagingResultExternalService results) {
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
        return report.getCode().getCoding().stream().anyMatch(c -> FhirSystems.IMAGING_EXAM.equals(c.getSystem()));
    }

    @Override
    public Recorded recordReport(DiagnosticReport report) {
        return results.record(report);
    }

    @Override
    public Optional<DiagnosticReport> findReport(String resultId) {
        return results.find(resultId);
    }
}
