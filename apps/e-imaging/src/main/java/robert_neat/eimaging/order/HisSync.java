package robert_neat.eimaging.order;

/** Port: przekazanie zmiany stanu zlecenia i wyniku do HIS (implementacja FHIR w pakiecie `his`). */
public interface HisSync {

    enum Outcome {
        /** HIS zaakceptowal zmiane. */
        SYNCED,
        /** HIS odrzucil zmiane (4xx, np. zlecenie w HIS jest juz w stanie koncowym). */
        REJECTED,
        /** Brak odpowiedzi/blad serwera/brak autoryzacji - zmiana do ponowienia. */
        UNAVAILABLE,
        /** Integracja wylaczona. */
        DISABLED
    }

    record Result(Outcome outcome, String message) {

        public static Result of(Outcome outcome) {
            return new Result(outcome, null);
        }
    }

    Result pushStatus(ImagingOrder order, ImagingOrderStatus target);

    Result pushResult(ImagingOrder order, ResultEntry result);
}
