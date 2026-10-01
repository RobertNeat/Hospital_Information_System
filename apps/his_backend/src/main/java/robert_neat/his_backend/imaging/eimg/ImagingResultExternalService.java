package robert_neat.his_backend.imaging.eimg;

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
import robert_neat.his_backend.common.fhir.FhirOrderHandler.Recorded;
import robert_neat.his_backend.imaging.ImagingOrderRepository;
import robert_neat.his_backend.imaging.ImagingResult;
import robert_neat.his_backend.imaging.ImagingResultRecordingService;
import robert_neat.his_backend.imaging.ImagingResultRepository;
import robert_neat.his_backend.imaging.RecordImagingResultCommand;

/**
 * Wynik z e-imaging (`DiagnosticReport`) -> {@link ImagingResultRecordingService#recordResult} (te same reguly:
 * walidacja 422, stan zlecenia / wynik ostateczny 409, zdarzenie `ImagingResultRecorded`, auto-`completed`). Aktor jest
 * systemowy (`null`), radiolog z `performer.display` (domyslnie "e-imaging"). Idempotencja: powtorzony raport zlecenia o
 * tym samym statusie i czasie opisu (`issued`) zwraca zapisany wynik (200) zamiast konfliktu; sprawdzana przed zapisem,
 * bo po wyniku ostatecznym zlecenie jest juz `completed`.
 */
@Service
@Transactional
public class ImagingResultExternalService {

    static final String DEFAULT_RADIOLOGIST = "e-imaging";

    private final ImagingResultRecordingService recording;
    private final ImagingResultRepository results;
    private final ImagingOrderRepository orders;
    private final ImagingFhirMapper mapper;

    ImagingResultExternalService(ImagingResultRecordingService recording, ImagingResultRepository results,
            ImagingOrderRepository orders, ImagingFhirMapper mapper) {
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
        String examCode = mapper.examCode(report);
        RecordImagingResultCommand parsed = mapper.toCommand(report);
        RecordImagingResultCommand cmd = new RecordImagingResultCommand(parsed.patientId(), parsed.orderId(),
                parsed.modality(), parsed.examName(), parsed.bodyRegion(), parsed.performedAt(), parsed.reportedAt(),
                parsed.radiologistName() == null || parsed.radiologistName().isBlank() ? DEFAULT_RADIOLOGIST
                        : parsed.radiologistName(),
                parsed.radiologistId(), parsed.technique(), parsed.findings(), parsed.conclusion(), parsed.status(),
                parsed.imageCount(), parsed.critical());
        orders.findById(cmd.orderId()).ifPresent(order -> {
            if (!order.getExamCode().equals(examCode)) {
                throw FhirException.unprocessable("Kod badania '" + examCode + "' nie zgadza sie ze zleceniem ('"
                        + order.getExamCode() + "')");
            }
        });
        Optional<ImagingResult> replay = results.findFirstByOrderIdAndStatusAndReportedAt(cmd.orderId(),
                cmd.status(), cmd.reportedAt());
        if (replay.isPresent()) {
            return new Recorded(mapper.toReport(replay.get()), false);
        }
        try {
            return new Recorded(mapper.toReport(results.findById(recording.recordResult(cmd).id()).orElseThrow()),
                    true);
        } catch (ValidationFailedException e) {
            throw FhirException.unprocessable(e.getErrors().stream().map(this::describe)
                    .collect(Collectors.joining("; ")));
        } catch (ConflictException e) {
            throw FhirException.conflict(e.getMessage());
        }
    }

    private String describe(FieldError e) {
        return e.field() + ": " + e.message();
    }
}
