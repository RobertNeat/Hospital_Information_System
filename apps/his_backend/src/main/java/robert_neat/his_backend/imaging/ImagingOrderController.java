package robert_neat.his_backend.imaging;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import robert_neat.his_backend.catalog.ImagingModality;
import robert_neat.his_backend.common.api.PageResponse;
import robert_neat.his_backend.common.order.OrderCancelRequest;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.order.OrderStatusUpdateRequest;
import robert_neat.his_backend.common.order.Urgency;

/** Zlecenia obrazowe (API.md, par. 5). Uprawnienia wg par. 12: `imaging-order:*`. */
@RestController
@RequestMapping("/api/v1")
public class ImagingOrderController {

    private final ImagingOrderService service;

    ImagingOrderController(ImagingOrderService service) {
        this.service = service;
    }

    /** Worklista (`ImagingOrderQuery`): filtry `patientId`, `status`, `urgency`, `modality` + paginacja. */
    @GetMapping("/imaging-orders")
    @PreAuthorize("hasAuthority('imaging-order:read')")
    public PageResponse<ImagingOrderResponse> list(@RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) OrderStatus status, @RequestParam(required = false) Urgency urgency,
            @RequestParam(required = false) ImagingModality modality, Pageable pageable) {
        return service.list(patientId, status, urgency, modality, pageable);
    }

    @GetMapping("/imaging-orders/{orderId}")
    @PreAuthorize("hasAuthority('imaging-order:read')")
    public ImagingOrderResponse get(@PathVariable String orderId) {
        return service.get(orderId);
    }

    @PostMapping("/patients/{patientId}/imaging-orders")
    @PreAuthorize("hasAuthority('imaging-order:create')")
    public ResponseEntity<ImagingOrderResponse> create(@PathVariable String patientId,
            @Valid @RequestBody ImagingOrderCreateRequest request) {
        ImagingOrderResponse created = service.create(patientId, request);
        return ResponseEntity.created(URI.create("/api/v1/imaging-orders/" + created.id())).body(created);
    }

    @PostMapping("/imaging-orders/{orderId}/status")
    @PreAuthorize("hasAuthority('imaging-order:update-status')")
    public ImagingOrderResponse updateStatus(@PathVariable String orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return service.updateStatus(orderId, request);
    }

    @PostMapping("/imaging-orders/{orderId}/cancel")
    @PreAuthorize("hasAuthority('imaging-order:cancel')")
    public ImagingOrderResponse cancel(@PathVariable String orderId, @Valid @RequestBody OrderCancelRequest request) {
        return service.cancel(orderId, request);
    }
}
