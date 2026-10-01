package robert_neat.elaboratory.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import robert_neat.elaboratory.order.HisSync.Outcome;

class LabOrderServiceTest {

    /** HIS podstawiony w tescie: kolejny wynik wywolania ustawia test, wywolania sa zapisywane. */
    static class FakeHis implements HisSync {
        Result next = Result.of(Outcome.SYNCED);
        final List<LabOrderStatus> pushedStatuses = new ArrayList<>();
        final List<ResultEntry> pushedResults = new ArrayList<>();

        @Override
        public Result pushStatus(LabOrder order, LabOrderStatus target) {
            pushedStatuses.add(target);
            return next;
        }

        @Override
        public Result pushResult(LabOrder order, ResultEntry result) {
            pushedResults.add(result);
            return next;
        }
    }

    private final FakeHis his = new FakeHis();
    private final LabOrderService service = new LabOrderService(new LabOrderStore(), his);

    private static LabOrderDraft draft(String id, String... tests) {
        List<LabItem> items = new ArrayList<>();
        for (String t : tests) {
            items.add(new LabItem(t, t + " nazwa", "blood", List.of(new LabItem.Analyte(t, t, "u",
                    new BigDecimal("1"), new BigDecimal("5")))));
        }
        return new LabOrderDraft(id, "Patient/p", "Practitioner/s", "routine", Instant.now(), null, false,
                List.of(), items);
    }

    private static LabOrderService.ResultInput result(ResultStatus status, String analyte) {
        return new LabOrderService.ResultInput(status, "Laborant", null,
                List.of(new Observation(analyte, analyte, "u", null, null, new BigDecimal("2"), null, null)));
    }

    private String collected(String id, String... tests) {
        service.register(draft(id, tests));
        service.changeFromUi(id, LabOrderStatus.SPECIMEN_COLLECTED);
        return id;
    }

    @Test
    void registerIsIdempotent() {
        assertThat(service.register(draft("o1", "MORF")).created()).isTrue();
        assertThat(service.register(draft("o1", "MORF")).created()).isFalse();
        assertThat(service.list()).hasSize(1);
    }

    @Test
    void uiChangeIsPushedToHisAndMarksSynced() {
        service.register(draft("o2", "MORF"));
        LabOrder o = service.changeFromUi("o2", LabOrderStatus.SPECIMEN_COLLECTED);

        assertThat(o.getStatus()).isEqualTo(LabOrderStatus.SPECIMEN_COLLECTED);
        assertThat(o.getSyncState()).isEqualTo(SyncState.SYNCED);
        assertThat(o.getCollectedAt()).isNotNull();
        assertThat(his.pushedStatuses).containsExactly(LabOrderStatus.SPECIMEN_COLLECTED);
    }

    @Test
    void hisRejectionKeepsLocalState() {
        service.register(draft("o3", "MORF"));
        his.next = new HisSync.Result(Outcome.REJECTED, "HTTP 409");

        assertThatThrownBy(() -> service.changeFromUi("o3", LabOrderStatus.SPECIMEN_COLLECTED))
                .isInstanceOf(LabOrderException.class).hasMessageContaining("HTTP 409");
        assertThat(service.get("o3").getStatus()).isEqualTo(LabOrderStatus.ORDERED);
    }

    @Test
    void unavailableHisKeepsChangeAsPendingAndResyncConfirms() {
        service.register(draft("o4", "MORF"));
        his.next = new HisSync.Result(Outcome.UNAVAILABLE, "HIS niedostepny");
        LabOrder o = service.changeFromUi("o4", LabOrderStatus.CANCELLED);
        assertThat(o.getStatus()).isEqualTo(LabOrderStatus.CANCELLED);
        assertThat(o.getSyncState()).isEqualTo(SyncState.PENDING);

        his.next = HisSync.Result.of(Outcome.SYNCED);
        assertThat(service.resync("o4").getSyncState()).isEqualTo(SyncState.SYNCED);
    }

    @Test
    void disabledHisIsLocalOnly() {
        service.register(draft("o5", "MORF"));
        his.next = HisSync.Result.of(Outcome.DISABLED);
        assertThat(service.changeFromUi("o5", LabOrderStatus.SPECIMEN_COLLECTED).getSyncState())
                .isEqualTo(SyncState.DISABLED);
    }

    @Test
    void disallowedTransitionsAreConflicts() {
        service.register(draft("o6", "MORF"));
        assertThatThrownBy(() -> service.changeFromUi("o6", LabOrderStatus.IN_PROGRESS))
                .isInstanceOf(LabOrderException.class);
        service.changeFromUi("o6", LabOrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.changeFromUi("o6", LabOrderStatus.SPECIMEN_COLLECTED))
                .isInstanceOf(LabOrderException.class);
        assertThatThrownBy(() -> service.changeFromUi("brak", LabOrderStatus.CANCELLED))
                .extracting(e -> ((LabOrderException) e).kind()).isEqualTo(LabOrderException.Kind.NOT_FOUND);
    }

    @Test
    void changeFromHisIsIdempotentAndHasNoCallback() {
        service.register(draft("o7", "MORF"));
        service.changeFromHis("o7", LabOrderStatus.CANCELLED);
        assertThat(service.changeFromHis("o7", LabOrderStatus.CANCELLED).getStatus())
                .isEqualTo(LabOrderStatus.CANCELLED);
        assertThat(his.pushedStatuses).isEmpty();
    }

    @Test
    void resultIsPushedAndLastFinalResultCompletesOrder() {
        collected("o8", "MORF", "CRP");

        LabOrder first = service.recordResult("o8", "MORF", result(ResultStatus.FINAL, "MORF"));
        assertThat(first.getStatus()).isEqualTo(LabOrderStatus.SPECIMEN_COLLECTED);
        LabOrder second = service.recordResult("o8", "CRP", result(ResultStatus.FINAL, "CRP"));

        assertThat(second.getStatus()).isEqualTo(LabOrderStatus.COMPLETED);
        assertThat(his.pushedResults).hasSize(2);
        assertThat(his.pushedResults.get(0).getResultedAt()).isAfterOrEqualTo(his.pushedResults.get(0).getCollectedAt());
    }

    @Test
    void preliminaryResultDoesNotCompleteOrder() {
        collected("o9", "MORF");
        assertThat(service.recordResult("o9", "MORF", result(ResultStatus.PRELIMINARY, "MORF")).getStatus())
                .isEqualTo(LabOrderStatus.SPECIMEN_COLLECTED);
    }

    @Test
    void resultRulesAreEnforcedLocally() {
        service.register(draft("o10", "MORF"));
        // zlecenie bez pobranego materialu
        assertThatThrownBy(() -> service.recordResult("o10", "MORF", result(ResultStatus.FINAL, "MORF")))
                .hasMessageContaining("nie przyjmuje wyniku");
        service.changeFromUi("o10", LabOrderStatus.SPECIMEN_COLLECTED);
        // nieznane badanie, brak obserwacji
        assertThatThrownBy(() -> service.recordResult("o10", "XYZ", result(ResultStatus.FINAL, "XYZ")))
                .hasMessageContaining("nie zawiera badania");
        assertThatThrownBy(() -> service.recordResult("o10", "MORF",
                new LabOrderService.ResultInput(ResultStatus.FINAL, null, null, List.of())))
                .hasMessageContaining("obserwacje");
        // wynik ostateczny tylko raz; potem tylko korekta (takze po completed)
        service.recordResult("o10", "MORF", result(ResultStatus.FINAL, "MORF"));
        assertThat(service.get("o10").getStatus()).isEqualTo(LabOrderStatus.COMPLETED);
        assertThatThrownBy(() -> service.recordResult("o10", "MORF", result(ResultStatus.FINAL, "MORF")))
                .isInstanceOf(LabOrderException.class);
        assertThat(service.recordResult("o10", "MORF", result(ResultStatus.CORRECTED, "MORF")).getResults())
                .hasSize(2);
    }

    @Test
    void hisRejectionOfResultIsNotStored() {
        collected("o11", "MORF");
        his.next = new HisSync.Result(Outcome.REJECTED, "HTTP 422");

        assertThatThrownBy(() -> service.recordResult("o11", "MORF", result(ResultStatus.FINAL, "MORF")))
                .hasMessageContaining("HTTP 422");
        assertThat(service.get("o11").getResults()).isEmpty();
    }

    @Test
    void pendingResultCompletesOrderOnlyAfterResync() {
        collected("o12", "MORF");
        his.next = new HisSync.Result(Outcome.UNAVAILABLE, "HIS niedostepny");
        LabOrder o = service.recordResult("o12", "MORF", result(ResultStatus.FINAL, "MORF"));
        assertThat(o.getResults()).singleElement().satisfies(r -> assertThat(r.getSyncState())
                .isEqualTo(SyncState.PENDING));
        assertThat(o.getStatus()).isEqualTo(LabOrderStatus.SPECIMEN_COLLECTED);

        his.next = HisSync.Result.of(Outcome.SYNCED);
        LabOrder after = service.resyncResult("o12", o.getResults().get(0).getId());
        assertThat(after.getResults().get(0).getSyncState()).isEqualTo(SyncState.SYNCED);
        assertThat(after.getStatus()).isEqualTo(LabOrderStatus.COMPLETED);
        assertThatThrownBy(() -> service.resyncResult("o12", "brak")).isInstanceOf(LabOrderException.class);
    }
}
