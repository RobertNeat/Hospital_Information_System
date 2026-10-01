package robert_neat.his_backend.lab;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import robert_neat.his_backend.common.api.PageResponse;
import robert_neat.his_backend.common.order.OrderCancelRequest;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.order.OrderStatusUpdateRequest;
import robert_neat.his_backend.common.order.Urgency;

/** Zlecenia laboratoryjne (API.md, par. 4). Uprawnienia wg par. 12: `lab-order:*`. */
@RestController
@RequestMapping("/api/v1")
public class LabOrderController {

    private final LabOrderService service;

    LabOrderController(LabOrderService service) {
        this.service = service;
    }

    /** Worklista; `orderedFrom`/`orderedTo` (ISO-8601, wlacznie) - dodatkowy filtr po `orderedAt`. */
    @GetMapping("/lab-orders")
    @PreAuthorize("hasAuthority('lab-order:read')")
    public PageResponse<LabOrderResponse> list(@RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) OrderStatus status, @RequestParam(required = false) Urgency urgency,
            @RequestParam(required = false) Instant orderedFrom, @RequestParam(required = false) Instant orderedTo,
            Pageable pageable) {
        return service.list(patientId, status, urgency, orderedFrom, orderedTo, pageable);
    }

    @GetMapping("/lab-orders/{orderId}")
    @PreAuthorize("hasAuthority('lab-order:read')")
    public LabOrderResponse get(@PathVariable String orderId) {
        return service.get(orderId);
    }

    @PostMapping("/patients/{patientId}/lab-orders")
    @PreAuthorize("hasAuthority('lab-order:create')")
    public ResponseEntity<LabOrderResponse> create(@PathVariable String patientId,
            @Valid @RequestBody LabOrderCreateRequest request) {
        LabOrderResponse created = service.create(patientId, request);
        return ResponseEntity.created(URI.create("/api/v1/lab-orders/" + created.id())).body(created);
    }

    /** Laborant/admin (`update-status`) - dowolny status; pielegniarka (`collect-specimen`) - tylko `specimen_collected`. */
    @PostMapping("/lab-orders/{orderId}/status")
    @PreAuthorize("hasAnyAuthority('lab-order:update-status', 'lab-order:collect-specimen')")
    public LabOrderResponse updateStatus(@PathVariable String orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request, Authentication authentication) {
        return service.updateStatus(orderId, request, authentication.getAuthorities());
    }

    @PostMapping("/lab-orders/{orderId}/cancel")
    @PreAuthorize("hasAuthority('lab-order:cancel')")
    public LabOrderResponse cancel(@PathVariable String orderId, @Valid @RequestBody OrderCancelRequest request) {
        return service.cancel(orderId, request);
    }
}
