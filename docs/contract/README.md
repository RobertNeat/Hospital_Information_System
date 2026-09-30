# Kontrakt frontend - backend HIS

Dokumenty opisują, jak backend (Spring Boot 4.1, Java 25, później JPA/PostgreSQL) ma zbudować encje, DTO i kontrolery na podstawie modeli frontendu.

| Dokument | Zawartość |
| --- | --- |
| [CONVENTIONS.md](CONVENTIONS.md) | konwencje (ID, daty, enumy, optional vs null, audyt, wersjonowanie, błędy, paginacja) i decyzje z kroków 2-11 |
| [ERD.md](ERD.md) | encje, pola (TS -> Java/SQL), klucze i unikalności, osadzenia, snapshoty/projekcje, typy UI i projekcje (nie-encje) |
| [API.md](API.md) | endpointy REST dla każdej publicznej metody serwisów, STOMP, role, kolejność implementacji, zdarzenia przekrojowe, otwarte decyzje |

## Workflow

1. **Źródłem kontraktu są typy TypeScript** w `apps/his_frontend/src/app/models/` (`*.model.ts`) i `models/api/` (żądania, zapytania, `Page`, `ProblemDetail`) oraz publiczne metody serwisów (`services/*.service.ts`, dziś mockowe).
2. **Backend pisze encje, DTO i kontrolery z tych dokumentów** (ERD -> encje JPA i migracje, API -> kontrolery i DTO, CONVENTIONS -> serializacja Jacksona, enumy, błędy). Kolejność: [API.md, sekcja 13](API.md#13-rekomendowana-kolejność-implementacji-backendu).
3. **Zmiana kontraktu = zmiana TS + dokumentów** w tym samym PR: najpierw typ w `models/` (ewolucyjnie: bez zmiany nazw pól i wartości unii, nowe pola tylko opcjonalne), potem odpowiednie wpisy w ERD.md / API.md / CONVENTIONS.md.
4. Kod mocków w serwisach (np. obliczanie anomalii, `EhrSummary`) nie jest specyfikacją; backend jest autorytatywny (oznaczenia `mock-only: backend authoritative`).
5. Nie zmieniamy `apps/his_backend` ani kodu frontendu w ramach zmian dokumentacyjnych.

Stan: dokumenty opisują modele po krokach 1-11. Otwarte decyzje: [API.md, sekcja 15](API.md#15-otwarte-decyzje).
