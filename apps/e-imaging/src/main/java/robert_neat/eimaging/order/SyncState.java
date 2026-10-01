package robert_neat.eimaging.order;

/** Stan synchronizacji zmiany (stanu zlecenia albo wyniku) z HIS. */
public enum SyncState {
    /** Integracja z HIS wylaczona (zmiana tylko lokalna). */
    DISABLED,
    /** HIS potwierdzil zmiane. */
    SYNCED,
    /** HIS nie potwierdzil (niedostepny/blad); mozna ponowic. */
    PENDING
}
