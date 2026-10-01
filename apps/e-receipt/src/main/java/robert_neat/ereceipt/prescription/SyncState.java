package robert_neat.ereceipt.prescription;

/** Stan synchronizacji zmiany statusu z HIS. */
public enum SyncState {
    /** Integracja z HIS wylaczona (zmiana tylko lokalna). */
    DISABLED,
    /** HIS potwierdzil biezacy status. */
    SYNCED,
    /** HIS nie potwierdzil (niedostepny/blad); mozna ponowic. */
    PENDING
}
