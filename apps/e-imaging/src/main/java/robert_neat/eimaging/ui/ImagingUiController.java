package robert_neat.eimaging.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import robert_neat.eimaging.order.ImagingOrder;
import robert_neat.eimaging.order.ImagingOrderException;
import robert_neat.eimaging.order.ImagingOrderException.Kind;
import robert_neat.eimaging.order.ImagingOrderService;
import robert_neat.eimaging.order.ImagingOrderStatus;
import robert_neat.eimaging.order.ResultStatus;
import robert_neat.eimaging.order.SyncState;

/** UI Thymeleaf (bez uwierzytelniania): lista zlecen, zmiana stanu i formularz wyniku (opis, wnioski, krytyczny). */
@Controller
class ImagingUiController {

    private final ImagingOrderService orders;

    ImagingUiController(ImagingOrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/")
    String root() {
        return "redirect:/ui/orders";
    }

    @GetMapping("/ui/orders")
    String list(Model model) {
        model.addAttribute("orders", orders.list());
        model.addAttribute("targets", ImagingOrderStatus.values());
        model.addAttribute("resultStatuses", ResultStatus.values());
        return "orders";
    }

    @PostMapping("/ui/orders/{id}/status")
    String changeStatus(@PathVariable String id, @RequestParam String status, RedirectAttributes redirect) {
        try {
            ImagingOrderStatus target = ImagingOrderStatus.fromWire(status).orElseThrow(
                    () -> new ImagingOrderException(Kind.INVALID, "Nieznany stan: " + status));
            ImagingOrder updated = orders.changeFromUi(id, target);
            redirect.addFlashAttribute("message", "Zmieniono stan na '" + updated.getStatus().wire() + "'"
                    + (updated.getSyncState() == SyncState.PENDING
                            ? " (HIS nie potwierdzil zmiany - mozna ponowic)" : ""));
        } catch (ImagingOrderException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/orders";
    }

    @PostMapping("/ui/orders/{id}/sync")
    String resync(@PathVariable String id, RedirectAttributes redirect) {
        try {
            ImagingOrder updated = orders.resync(id);
            redirect.addFlashAttribute("message", "Synchronizacja stanu z HIS: " + updated.getSyncState());
        } catch (ImagingOrderException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/orders";
    }

    @PostMapping("/ui/orders/{id}/result")
    String recordResult(@PathVariable String id, @RequestParam String status,
            @RequestParam(required = false) String radiologist, @RequestParam(required = false) String findings,
            @RequestParam(required = false) String conclusion, @RequestParam(defaultValue = "false") boolean critical,
            RedirectAttributes redirect) {
        try {
            ResultStatus resultStatus = ResultStatus.fromWire(status).orElseThrow(
                    () -> new ImagingOrderException(Kind.INVALID, "Nieznany status wyniku: " + status));
            ImagingOrder updated = orders.recordResult(id,
                    new ImagingOrderService.ResultInput(resultStatus, radiologist, findings, conclusion, critical));
            boolean pending = updated.getResults().stream().anyMatch(r -> r.getSyncState() == SyncState.PENDING);
            redirect.addFlashAttribute("message", "Zapisano wynik (" + resultStatus.wire() + ")"
                    + (pending ? " (HIS nie potwierdzil wyniku - mozna ponowic)" : ""));
        } catch (ImagingOrderException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/orders";
    }

    @PostMapping("/ui/orders/{id}/results/{resultId}/sync")
    String resyncResult(@PathVariable String id, @PathVariable String resultId, RedirectAttributes redirect) {
        try {
            orders.resyncResult(id, resultId);
            redirect.addFlashAttribute("message", "Ponowiono przekazanie wyniku do HIS");
        } catch (ImagingOrderException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/orders";
    }
}
