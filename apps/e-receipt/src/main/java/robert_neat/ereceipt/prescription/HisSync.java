package robert_neat.ereceipt.prescription;

/** Port: przekazanie zmiany statusu recepty do HIS (implementacja FHIR w pakiecie `his`). */
public interface HisSync {

    enum Outcome {
        /** HIS zaakceptowal zmiane. */
        SYNCED,
        /** HIS odrzucil zmiane (4xx, np. recepta w HIS jest juz w stanie koncowym). */
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

    Result pushStatus(Receipt receipt, ReceiptStatus target);
}
