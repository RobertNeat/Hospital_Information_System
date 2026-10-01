package robert_neat.eimaging.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import robert_neat.eimaging.order.HisSync.Outcome;
import robert_neat.eimaging.order.ImagingOrderException.Kind;

class ImagingOrderServiceTest {

    /** HIS podstawiony w tescie: kolejny wynik wywolania ustawia test, wywolania sa zapisywane. */
    static class FakeHis implements HisSync {
        Result next = Result.of(Outcome.SYNCED);
        final List<ImagingOrderStatus> pushedStatuses = new ArrayList<>();
        final List<ResultEntry> pushedResults = new ArrayList<>();

        @Override
        public Result pushStatus(ImagingOrder order, ImagingOrderStatus target) {
            pushedStatuses.add(target);
            return next;
        }

        @Override
        public Result pushResult(ImagingOrder order, ResultEntry result) {
            pushedResults.add(result);
            return next;
        }
    }

    private final FakeHis his = new FakeHis();
    private final ImagingOrderService service = new ImagingOrderService(new ImagingOrderStore(), his);

    private static ImagingOrderService.ResultInput result(ResultStatus status) {
        return new ImagingOrderService.ResultInput(status, "Radiolog", "Opis.", "Wniosek.", false);
    }

    private String scheduled(String id) {
        service.register(OrderFixtures.draft(id, ImagingOrderStatus.SCHEDULED));
        return id;
    }

    @Test
    void registerIsIdempotentAndKeepsInitialStateFromHis() {
        assertThat(service.register(OrderFixtures.draft("o1", ImagingOrderStatus.SCHEDULED)).created()).isTrue();
        assertThat(service.register(OrderFixtures.draft("o1", ImagingOrderStatus.ORDERED)).created()).isFalse();
        assertThat(service.list()).singleElement().satisfies(o -> assertThat(o.getStatus())
                .isEqualTo(ImagingOrderStatus.SCHEDULED));
    }

    @Test
    void uiChangeIsPushedToHisAndMarksSynced() {
        service.register(OrderFixtures.draft("o2", ImagingOrderStatus.ORDERED));
        ImagingOrder o = service.changeFromUi("o2", ImagingOrderStatus.IN_PROGRESS);

        assertThat(o.getStatus()).isEqualTo(ImagingOrderStatus.IN_PROGRESS);
        assertThat(o.getSyncState()).isEqualTo(SyncState.SYNCED);
        assertThat(o.getStartedAt()).isNotNull();
        assertThat(his.pushedStatuses).containsExactly(ImagingOrderStatus.IN_PROGRESS);
    }

    @Test
    void hisRejectionKeepsLocalState() {
        service.register(OrderFixtures.draft("o3", ImagingOrderStatus.ORDERED));
        his.next = new HisSync.Result(Outcome.REJECTED, "HTTP 409");

        assertThatThrownBy(() -> service.changeFromUi("o3", ImagingOrderStatus.CANCELLED))
                .isInstanceOfSatisfying(ImagingOrderException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.REJECTED_BY_HIS));
        assertThat(service.get("o3").getStatus()).isEqualTo(ImagingOrderStatus.ORDERED);
    }

    @Test
    void unavailableHisLeavesPendingChangeThatCanBeResynced() {
        service.register(OrderFixtures.draft("o4", ImagingOrderStatus.ORDERED));
        his.next = new HisSync.Result(Outcome.UNAVAILABLE, "HIS niedostepny");
        ImagingOrder o = service.changeFromUi("o4", ImagingOrderStatus.SCHEDULED);
        assertThat(o.getStatus()).isEqualTo(ImagingOrderStatus.SCHEDULED);
        assertThat(o.getSyncState()).isEqualTo(SyncState.PENDING);

        his.next = HisSync.Result.of(Outcome.SYNCED);
        assertThat(service.resync("o4").getSyncState()).isEqualTo(SyncState.SYNCED);
        assertThat(his.pushedStatuses).containsExactly(ImagingOrderStatus.SCHEDULED, ImagingOrderStatus.SCHEDULED);
    }

    @Test
    void disabledIntegrationAppliesChangeLocally() {
        service.register(OrderFixtures.draft("o5", ImagingOrderStatus.ORDERED));
        his.next = HisSync.Result.of(Outcome.DISABLED);
        assertThat(service.changeFromUi("o5", ImagingOrderStatus.SCHEDULED).getSyncState())
                .isEqualTo(SyncState.DISABLED);
    }

    @Test
    void stateMachineMatchesHis() {
        service.register(OrderFixtures.draft("o6", ImagingOrderStatus.ORDERED));
        // ordered -> completed jest niedozwolone, scheduled -> completed (bez in_progress) dozwolone
        assertThatThrownBy(() -> service.changeFromUi("o6", ImagingOrderStatus.COMPLETED))
                .isInstanceOfSatisfying(ImagingOrderException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.CONFLICT));
        service.changeFromUi("o6", ImagingOrderStatus.SCHEDULED);
        assertThat(service.changeFromUi("o6", ImagingOrderStatus.COMPLETED).getStatus())
                .isEqualTo(ImagingOrderStatus.COMPLETED);
        assertThatThrownBy(() -> service.changeFromUi("o6", ImagingOrderStatus.CANCELLED))
                .isInstanceOf(ImagingOrderException.class);
    }

    @Test
    void changeFromHisAppliesWithoutCallingHisAndIsIdempotent() {
        service.register(OrderFixtures.draft("o7", ImagingOrderStatus.SCHEDULED));
        assertThat(service.changeFromHis("o7", ImagingOrderStatus.CANCELLED).getStatus())
                .isEqualTo(ImagingOrderStatus.CANCELLED);
        assertThat(service.changeFromHis("o7", ImagingOrderStatus.CANCELLED).getStatus())
                .isEqualTo(ImagingOrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.changeFromHis("o7", ImagingOrderStatus.IN_PROGRESS))
                .isInstanceOf(ImagingOrderException.class);
        assertThat(his.pushedStatuses).isEmpty();
    }

    @Test
    void finalResultIsPushedAndCompletesOrder() {
        String id = scheduled("o8");
        ImagingOrder o = service.recordResult(id, new ImagingOrderService.ResultInput(ResultStatus.FINAL, " ",
                " Opis. ", " Wniosek. ", true));

        assertThat(o.getStatus()).isEqualTo(ImagingOrderStatus.COMPLETED);
        assertThat(o.getResults()).singleElement().satisfies(r -> {
            assertThat(r.getSyncState()).isEqualTo(SyncState.SYNCED);
            assertThat(r.getRadiologist()).isEqualTo("e-imaging");
            assertThat(r.getFindings()).isEqualTo("Opis.");
            assertThat(r.isCritical()).isTrue();
            assertThat(r.getReportedAt()).isAfterOrEqualTo(r.getPerformedAt());
        });
        assertThat(his.pushedResults).hasSize(1);
    }

    @Test
    void preliminaryResultDoesNotCompleteAndReportTimesIncrease() {
        String id = scheduled("o9");
        service.recordResult(id, result(ResultStatus.PRELIMINARY));
        ImagingOrder o = service.recordResult(id, result(ResultStatus.PRELIMINARY));

        assertThat(o.getStatus()).isEqualTo(ImagingOrderStatus.SCHEDULED);
        assertThat(o.getResults().get(1).getReportedAt()).isAfter(o.getResults().get(0).getReportedAt());
    }

    @Test
    void pendingFinalResultKeepsOrderOpenUntilResyncSucceeds() {
        String id = scheduled("o10");
        his.next = new HisSync.Result(Outcome.UNAVAILABLE, "HIS niedostepny");
        ImagingOrder o = service.recordResult(id, result(ResultStatus.FINAL));
        assertThat(o.getStatus()).isEqualTo(ImagingOrderStatus.SCHEDULED);
        assertThat(o.getResults().get(0).getSyncState()).isEqualTo(SyncState.PENDING);

        his.next = HisSync.Result.of(Outcome.SYNCED);
        ImagingOrder resynced = service.resyncResult(id, o.getResults().get(0).getId());
        assertThat(resynced.getStatus()).isEqualTo(ImagingOrderStatus.COMPLETED);
        assertThat(his.pushedResults.get(0).getReportedAt()).isEqualTo(his.pushedResults.get(1).getReportedAt());
    }

    @Test
    void resultValidationAndStateRules() {
        service.register(OrderFixtures.draft("o11", ImagingOrderStatus.ORDERED));
        // zlecenie nie jest zaplanowane ani w toku -> 409
        assertThatThrownBy(() -> service.recordResult("o11", result(ResultStatus.FINAL)))
                .isInstanceOfSatisfying(ImagingOrderException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.CONFLICT));
        service.changeFromUi("o11", ImagingOrderStatus.IN_PROGRESS);
        // brak opisu lub wnioskow -> 400
        assertThatThrownBy(() -> service.recordResult("o11",
                new ImagingOrderService.ResultInput(ResultStatus.FINAL, null, " ", "Wniosek", false)))
                .isInstanceOfSatisfying(ImagingOrderException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.INVALID));
        service.recordResult("o11", result(ResultStatus.FINAL));
        // po wyniku ostatecznym zlecenie jest zakonczone
        assertThatThrownBy(() -> service.recordResult("o11", result(ResultStatus.FINAL)))
                .isInstanceOfSatisfying(ImagingOrderException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.CONFLICT));
        assertThatThrownBy(() -> service.get("brak")).isInstanceOfSatisfying(ImagingOrderException.class,
                e -> assertThat(e.kind()).isEqualTo(Kind.NOT_FOUND));
    }

    @Test
    void hisRejectionOfResultIsNotStored() {
        String id = scheduled("o12");
        his.next = new HisSync.Result(Outcome.REJECTED, "HTTP 422");
        assertThatThrownBy(() -> service.recordResult(id, result(ResultStatus.FINAL)))
                .isInstanceOfSatisfying(ImagingOrderException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.REJECTED_BY_HIS));
        assertThat(service.get(id).getResults()).isEmpty();
    }
}
