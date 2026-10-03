package robert_neat.his_backend.common.outbox;

import java.util.UUID;

/**
 * Wdrazane przez kazda integracje FHIR (e-receipt/e-laboratory/e-imaging), zeby {@link IntegrationOutboxScheduler}
 * mogl ponowic probe bez znajomosci szczegolow danej integracji. `retry` ma byc identyczne z proba synchroniczna
 * (ten sam kod, patrz np. `EReceiptIntegration#trySubmit`) - jedno miejsce prawdy o tym, jak wyslac dana operacje.
 */
public interface OutboxRetryable {

    /** Integracja, ktorej wpisy outboxa ten komponent obsluguje. */
    OutboxIntegration integration();

    /**
     * Ponawia dana operacje dla danej encji. Zwraca `true`, gdy wpis mozna uznac za zakonczony (sukces lub stan
     * encji uczynil wysylke bezprzedmiotowa - metoda sama aktualizuje outbox w obu przypadkach), `false` gdy
     * wysylka nadal sie nie powiodla (outbox zostaje zaktualizowany - licznik prob/`last_error` - przez implementacje).
     */
    boolean retry(OutboxOperation operation, UUID entityId);
}
