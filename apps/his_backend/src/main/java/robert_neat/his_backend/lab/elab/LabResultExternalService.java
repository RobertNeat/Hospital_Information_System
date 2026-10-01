package robert_neat.his_backend.lab.elab;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.DiagnosticReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.fhir.FhirException;
import robert_neat.his_backend.lab.LabOrderRepository;
import robert_neat.his_backend.lab.LabResult;
import robert_neat.his_backend.lab.LabResultRecordingService;
import robert_neat.his_backend.lab.LabResultRepository;
import robert_neat.his_backend.lab.RecordLabResultCommand;

/**
 * Wynik z e-laboratory (`DiagnosticReport`) -> {@link LabResultRecordingService#recordResult} (te same reguly:
 * walidacja 422, stan zlecenia / wynik ostateczny 409, flagi, zdarzenia, auto-`completed`). Aktor jest systemowy
 * (`null`), wykonawca z `performer.display` (domyslnie "e-laboratory"). Idempotencja: powtorzony raport pozycji
 * o tym samym statusie i czasie wyniku (`issued`) zwraca zapisany wynik (200) zamiast konfliktu.
 */
@Service
@Transactional
public class LabResultExternalService {

    static final String DEFAULT_PERFORMER = "e-laboratory";

    public record Recorded(DiagnosticReport report, boolean created) {
    }

    private final LabResultRecordingService recording;
    private final LabResultRepository results;
    private final LabOrderRepository orders;
    private final LabFhirMapper mapper;

    LabResultExternalService(LabResultRecordingService recording, LabResultRepository results,
            LabOrderRepository orders, LabFhirMapper mapper) {
        this.recording = recording;
        this.results = results;
        this.orders = orders;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<DiagnosticReport> find(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return results.findById(uuid).map(mapper::toReport);
    }

    public Recorded record(DiagnosticReport report) {
        RecordLabResultCommand parsed = mapper.toCommand(report);
        RecordLabResultCommand cmd = new RecordLabResultCommand(parsed.patientId(), parsed.orderId(),
                parsed.orderItemId(), parsed.testCode(), parsed.collectedAt(), parsed.resultedAt(), parsed.status(),
                parsed.performerName() == null || parsed.performerName().isBlank() ? DEFAULT_PERFORMER
                        : parsed.performerName(),
                parsed.comment(), parsed.observations());
        Optional<LabResult> replay = existing(cmd);
        if (replay.isPresent()) {
            return new Recorded(mapper.toReport(replay.get()), false);
        }
        try {
            return new Recorded(mapper.toReport(results.findById(recording.recordResult(cmd).id()).orElseThrow()),
                    true);
        } catch (ValidationFailedException e) {
            throw FhirException.unprocessable(e.getErrors().stream()
                    .map(this::describe).collect(Collectors.joining("; ")));
        } catch (ConflictException e) {
            throw FhirException.conflict(e.getMessage());
        }
    }

    /** Wynik tej samej pozycji zlecenia z tym samym statusem i czasem wyniku (ponowienie wysylki). */
    private Optional<LabResult> existing(RecordLabResultCommand cmd) {
        if (cmd.orderId() == null || cmd.testCode() == null || cmd.resultedAt() == null || cmd.status() == null) {
            return Optional.empty();
        }
        return orders.findById(cmd.orderId()).flatMap(order -> order.getItems().stream()
                .filter(i -> i.getTestCode().equals(cmd.testCode().trim())).findFirst())
                .flatMap(item -> results.findFirstByOrderItemIdAndStatusAndResultedAt(item.getId(), cmd.status(),
                        cmd.resultedAt()));
    }

    private String describe(FieldError e) {
        return e.field() + ": " + e.message();
    }
}
