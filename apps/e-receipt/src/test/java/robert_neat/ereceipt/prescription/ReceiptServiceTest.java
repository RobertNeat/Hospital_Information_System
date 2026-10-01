package robert_neat.ereceipt.prescription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import robert_neat.ereceipt.prescription.HisSync.Outcome;

class ReceiptServiceTest {

    /** HIS podstawiony w tescie: kolejny wynik wywolania ustawia test, wywolania sa zapisywane. */
    static class FakeHis implements HisSync {
        Result next = Result.of(Outcome.SYNCED);
        final List<ReceiptStatus> pushed = new ArrayList<>();

        @Override
        public Result pushStatus(Receipt receipt, ReceiptStatus target) {
            pushed.add(target);
            return next;
        }
    }

    private final FakeHis his = new FakeHis();
    private final ReceiptService service = new ReceiptService(new ReceiptStore(), his);

    private String register(String hisId) {
        return service.register(new ReceiptDraft(hisId, "1234", "Patient/p", "Practitioner/s", Instant.now(),
                LocalDate.now(), LocalDate.now().plusDays(30), "Lek", List.of("1 tabl."), null))
                .receipt().getErxKey();
    }

    @Test
    void generatesKeyInHisFormatAndIsIdempotent() {
        String key = register("his-1");
        assertThat(key).matches("[A-Z0-9]{44}");
        ReceiptService.Registration again = service.register(new ReceiptDraft("his-1", null, null, null,
                Instant.now(), LocalDate.now(), LocalDate.now(), "Lek", List.of(), null));
        assertThat(again.created()).isFalse();
        assertThat(again.receipt().getErxKey()).isEqualTo(key);
        assertThat(service.list()).hasSize(1);
    }

    @Test
    void uiChangeIsPushedToHisAndMarksSynced() {
        String key = register("his-2");
        Receipt r = service.changeFromUi(key, ReceiptStatus.DISPENSED);
        assertThat(r.getStatus()).isEqualTo(ReceiptStatus.DISPENSED);
        assertThat(r.getSyncState()).isEqualTo(SyncState.SYNCED);
        assertThat(his.pushed).containsExactly(ReceiptStatus.DISPENSED);
    }

    @Test
    void hisRejectionKeepsLocalState() {
        String key = register("his-3");
        his.next = new HisSync.Result(Outcome.REJECTED, "HTTP 409");
        assertThatThrownBy(() -> service.changeFromUi(key, ReceiptStatus.CANCELLED))
                .isInstanceOf(ReceiptException.class).hasMessageContaining("HTTP 409");
        assertThat(service.get(key).getStatus()).isEqualTo(ReceiptStatus.ISSUED);
    }

    @Test
    void unavailableHisLeavesPendingAndResyncRecovers() {
        String key = register("his-4");
        his.next = new HisSync.Result(Outcome.UNAVAILABLE, "HIS niedostepny");
        Receipt r = service.changeFromUi(key, ReceiptStatus.EXPIRED);
        assertThat(r.getStatus()).isEqualTo(ReceiptStatus.EXPIRED);
        assertThat(r.getSyncState()).isEqualTo(SyncState.PENDING);

        his.next = HisSync.Result.of(Outcome.SYNCED);
        assertThat(service.resync(key).getSyncState()).isEqualTo(SyncState.SYNCED);
        assertThat(his.pushed).containsExactly(ReceiptStatus.EXPIRED, ReceiptStatus.EXPIRED);
    }

    @Test
    void disabledHisIsReportedAsDisabled() {
        String key = register("his-5");
        his.next = HisSync.Result.of(Outcome.DISABLED);
        assertThat(service.changeFromUi(key, ReceiptStatus.PARTIALLY_DISPENSED).getSyncState())
                .isEqualTo(SyncState.DISABLED);
    }

    @Test
    void transitionsFromTerminalOrToIssuedAreConflicts() {
        String key = register("his-6");
        assertThatThrownBy(() -> service.changeFromUi(key, ReceiptStatus.ISSUED))
                .isInstanceOf(ReceiptException.class);
        service.changeFromUi(key, ReceiptStatus.PARTIALLY_DISPENSED);
        service.changeFromUi(key, ReceiptStatus.DISPENSED);
        assertThatThrownBy(() -> service.changeFromUi(key, ReceiptStatus.CANCELLED))
                .isInstanceOf(ReceiptException.class);
    }

    @Test
    void changeFromHisDoesNotCallBack() {
        String key = register("his-7");
        service.changeFromHis(key, ReceiptStatus.CANCELLED);
        assertThat(his.pushed).isEmpty();
        assertThat(service.get(key).getStatus()).isEqualTo(ReceiptStatus.CANCELLED);
    }
}
