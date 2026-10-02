package robert_neat.ereceipt.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import robert_neat.ereceipt.prescription.Receipt;
import robert_neat.ereceipt.prescription.ReceiptException;
import robert_neat.ereceipt.prescription.ReceiptService;
import robert_neat.ereceipt.prescription.ReceiptStatus;
import robert_neat.ereceipt.prescription.SyncState;

/** UI Thymeleaf (bez uwierzytelniania): lista e-recept i zmiana stanu. */
@Controller
class ReceiptUiController {

    private final ReceiptService receipts;

    ReceiptUiController(ReceiptService receipts) {
        this.receipts = receipts;
    }

    @GetMapping("/")
    String root() {
        return "redirect:/ui/prescriptions";
    }

    @GetMapping("/ui/prescriptions")
    String list(@RequestParam(defaultValue = "active") String view, Model model) {
        boolean history = "history".equals(view);
        // Historia = recepty zamkniete (zrealizowane, anulowane, wygasle); aktywne = nadal "zywe".
        model.addAttribute("receipts",
                receipts.list().stream().filter(r -> r.getStatus().isOpen() != history).toList());
        model.addAttribute("view", history ? "history" : "active");
        model.addAttribute("brand", "e-receipt");
        model.addAttribute("subtitle", "system zarządzania receptami");
        model.addAttribute("base", "/ui/prescriptions");
        model.addAttribute("targets", ReceiptStatus.values());
        return "prescriptions";
    }

    @PostMapping("/ui/prescriptions/{erxKey}/status")
    String changeStatus(@PathVariable String erxKey, @RequestParam String status, RedirectAttributes redirect) {
        try {
            ReceiptStatus target = ReceiptStatus.fromWire(status).orElseThrow(
                    () -> new ReceiptException(ReceiptException.Kind.INVALID, "Nieznany stan: " + status));
            Receipt updated = receipts.changeFromUi(erxKey, target);
            redirect.addFlashAttribute("message", "Zmieniono stan na '" + updated.getStatus().wire() + "'"
                    + (updated.getSyncState() == SyncState.PENDING
                            ? " (HIS nie potwierdzil zmiany - mozna ponowic)" : ""));
        } catch (ReceiptException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/prescriptions";
    }

    @PostMapping("/ui/prescriptions/{erxKey}/sync")
    String resync(@PathVariable String erxKey, RedirectAttributes redirect) {
        try {
            Receipt updated = receipts.resync(erxKey);
            redirect.addFlashAttribute("message", "Synchronizacja z HIS: " + updated.getSyncState());
        } catch (ReceiptException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/prescriptions";
    }
}
