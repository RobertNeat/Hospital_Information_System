# Kontrakt wystawiany przez his_frontend

Ten katalog opisuje, **czego his_frontend oczekuje od backendu**: modele danych i operacje, które UI zna dziś z typów TypeScript i mockowych serwisów. To kontrakt po stronie *konsumenta* (frontend). Kontrakt po stronie *dostawcy* (co backend faktycznie wystawia) jest w [`../his_backend_contract`](../his_backend_contract/README.md).

| Dokument | Zawartość |
| --- | --- |
| [CONVENTIONS.md](CONVENTIONS.md) | konwencje (ID, daty, enumy, optional vs null, audyt, wersjonowanie, błędy, paginacja) i ustalenia |
| [ERD.md](ERD.md) | encje, pola (TS -> Java/SQL), klucze i unikalności, osadzenia, snapshoty/projekcje, typy UI i projekcje (nie-encje) |
| [API.md](API.md) | endpointy REST dla każdej publicznej metody serwisów, STOMP, role, kolejność implementacji, zdarzenia przekrojowe, przyjęte rozstrzygnięcia |

## Workflow

1. **Źródłem kontraktu są typy TypeScript** w `apps/his_frontend/src/app/models/` (`*.model.ts`) i `models/api/` (żądania, zapytania, `Page`, `ProblemDetail`) oraz publiczne metody serwisów (`services/*.service.ts`, dziś w większości mockowe).
2. **Backend pisze encje, DTO i kontrolery z tych dokumentów** (ERD -> encje JPA i migracje, API -> kontrolery i DTO, CONVENTIONS -> serializacja Jacksona, enumy, błędy). Kolejność: [API.md, sekcja 13](API.md#13-rekomendowana-kolejność-implementacji-backendu).
3. **Zmiana kontraktu = zmiana TS + dokumentów** w tym samym PR: najpierw typ w `models/` (ewolucyjnie: bez zmiany nazw pól i wartości unii, nowe pola tylko opcjonalne), potem odpowiednie wpisy w ERD.md / API.md / CONVENTIONS.md.
4. Kod mocków w serwisach (np. obliczanie anomalii, `EhrSummary`) nie jest specyfikacją; backend jest autorytatywny (oznaczenia `mock-only: backend authoritative`).
5. Gdy backend świadomie odstępuje od tego kontraktu, odstępstwo opisujemy w [`../his_backend_contract`](../his_backend_contract/README.md) (sekcja [Odstępstwa od his_frontend_contract](../his_backend_contract/README.md#odstępstwa-od-his_frontend_contract)); tu pozostaje opis oczekiwań frontendu.

Stan: dokumenty opisują modele i operacje oczekiwane przez frontend. Przyjęte rozstrzygnięcia: [API.md, sekcja 15](API.md#15-przyjęte-rozstrzygnięcia).
