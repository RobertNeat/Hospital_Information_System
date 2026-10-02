package robert_neat.elaboratory.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import robert_neat.elaboratory.order.LabItem;
import robert_neat.elaboratory.order.LabItem.Analyte;
import robert_neat.elaboratory.order.LabOrder;
import robert_neat.elaboratory.order.LabOrderException;
import robert_neat.elaboratory.order.LabOrderException.Kind;
import robert_neat.elaboratory.order.LabOrderService;
import robert_neat.elaboratory.order.LabOrderStatus;
import robert_neat.elaboratory.order.Observation;
import robert_neat.elaboratory.order.ResultStatus;
import robert_neat.elaboratory.order.SyncState;

/**
 * UI Thymeleaf (bez uwierzytelniania): lista zlecen, zmiana stanu i formularz wyniku. Pola formularza wyniku:
 * `v_{kodAnalitu}` (liczba albo tekst; puste = pominiete) i `f_{kodAnalitu}` (flaga; pusta = wyznaczy HIS).
 */
@Controller
class LabUiController {

    private static final Set<String> FLAGS = Set.of("N", "L", "H", "LL", "HH", "A");

    private final LabOrderService orders;

    LabUiController(LabOrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/")
    String root() {
        return "redirect:/ui/orders";
    }

    @GetMapping("/ui/orders")
    String list(@RequestParam(defaultValue = "active") String view, Model model) {
        boolean history = "history".equals(view);
        // Historia = zlecenia zakonczone lub anulowane; aktywne = pozostale.
        model.addAttribute("orders",
                orders.list().stream().filter(o -> o.getStatus().isTerminal() == history).toList());
        model.addAttribute("view", history ? "history" : "active");
        model.addAttribute("brand", "e-laboratory");
        model.addAttribute("subtitle", "system zarządzania zleceniami laboratoryjnymi");
        model.addAttribute("base", "/ui/orders");
        model.addAttribute("targets", LabOrderStatus.values());
        model.addAttribute("resultStatuses", ResultStatus.values());
        model.addAttribute("flags", List.of("N", "L", "H", "LL", "HH", "A"));
        return "orders";
    }

    @PostMapping("/ui/orders/{id}/status")
    String changeStatus(@PathVariable String id, @RequestParam String status, RedirectAttributes redirect) {
        try {
            LabOrderStatus target = LabOrderStatus.fromWire(status).orElseThrow(
                    () -> new LabOrderException(Kind.INVALID, "Nieznany stan: " + status));
            LabOrder updated = orders.changeFromUi(id, target);
            redirect.addFlashAttribute("message", "Zmieniono stan na '" + updated.getStatus().wire() + "'"
                    + (updated.getSyncState() == SyncState.PENDING
                            ? " (HIS nie potwierdzil zmiany - mozna ponowic)" : ""));
        } catch (LabOrderException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/orders";
    }

    @PostMapping("/ui/orders/{id}/sync")
    String resync(@PathVariable String id, RedirectAttributes redirect) {
        try {
            LabOrder updated = orders.resync(id);
            redirect.addFlashAttribute("message", "Synchronizacja stanu z HIS: " + updated.getSyncState());
        } catch (LabOrderException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/orders";
    }

    @PostMapping("/ui/orders/{id}/results/{testCode}")
    String recordResult(@PathVariable String id, @PathVariable String testCode, @RequestParam String status,
            @RequestParam(required = false) String performer, @RequestParam(required = false) String comment,
            @RequestParam Map<String, String> form, RedirectAttributes redirect) {
        try {
            ResultStatus resultStatus = ResultStatus.fromWire(status).orElseThrow(
                    () -> new LabOrderException(Kind.INVALID, "Nieznany status wyniku: " + status));
            LabOrder order = orders.get(id);
            LabItem item = order.getItems().stream().filter(i -> i.testCode().equals(testCode)).findFirst()
                    .orElseThrow(() -> new LabOrderException(Kind.INVALID, "Zlecenie nie zawiera badania " + testCode));
            LabOrder updated = orders.recordResult(id, testCode,
                    new LabOrderService.ResultInput(resultStatus, performer, comment, observations(item, form)));
            ResultSyncNote note = new ResultSyncNote(updated);
            redirect.addFlashAttribute("message", "Zapisano wynik " + testCode + " (" + resultStatus.wire() + ")"
                    + note.suffix());
        } catch (LabOrderException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/orders";
    }

    @PostMapping("/ui/orders/{id}/results/{resultId}/sync")
    String resyncResult(@PathVariable String id, @PathVariable String resultId, RedirectAttributes redirect) {
        try {
            orders.resyncResult(id, resultId);
            redirect.addFlashAttribute("message", "Ponowiono przekazanie wyniku do HIS");
        } catch (LabOrderException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ui/orders";
    }

    /** Obserwacje z pol formularza dla analitow badania (brak definicji = jeden analit o kodzie badania). */
    private static List<Observation> observations(LabItem item, Map<String, String> form) {
        List<Analyte> analytes = item.analytes().isEmpty()
                ? List.of(new Analyte(item.testCode(), item.testName(), "", null, null)) : item.analytes();
        List<Observation> result = new ArrayList<>();
        for (Analyte a : analytes) {
            String raw = form.get("v_" + a.code());
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String flag = form.get("f_" + a.code());
            if (flag != null && !flag.isBlank() && !FLAGS.contains(flag)) {
                throw new LabOrderException(Kind.INVALID, "Nieznana flaga: " + flag);
            }
            String value = raw.trim();
            BigDecimal number = parse(value);
            result.add(new Observation(a.code(), a.name(), a.unit(), a.low(), a.high(), number,
                    number == null ? value : null, flag == null || flag.isBlank() ? null : flag));
        }
        return result;
    }

    private static BigDecimal parse(String value) {
        try {
            return new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Dopisek do komunikatu, gdy HIS nie potwierdzil ostatniego wyniku. */
    private record ResultSyncNote(LabOrder order) {

        String suffix() {
            boolean pending = order.getResults().stream().anyMatch(r -> r.getSyncState() == SyncState.PENDING);
            return pending ? " (HIS nie potwierdzil wyniku - mozna ponowic)" : "";
        }
    }
}
