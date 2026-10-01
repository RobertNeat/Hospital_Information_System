# Terminologia SNOMED CT (Snowstorm Lite)

Endpointy `/terminology/snomed/*` i integracja z serwerem terminologii FHIR (Snowstorm Lite). Powrót: [README.md](README.md).

Kod: `terminology/snomed/*` (`TerminologyController`, `SnowstormClient`, `SnowstormProperties`, `TerminologyException`, `TerminologyExceptionHandler`).

## Endpointy

Dostęp: wystarczy uwierzytelnienie (`/api/**` jest `authenticated`; brak `@PreAuthorize`), czyli każda rola.

| Metoda | Ścieżka | Parametry | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/terminology/snomed/concepts` | `ecl` (opc.), `term` (opc.), `limit` (domyślnie 20), `offset` (domyślnie 0); wymagane `ecl` lub `term` | `SnomedConceptPage` (`total`, `offset`, `concepts[]` `{ code, display }`) | 200; 400; 503 |
| GET | `/api/v1/terminology/snomed/concepts/{sctid}` | `sctid`: 6-18 cyfr | `SnomedConcept` (`code`, `display`) | 200; 400; 404; 503 |

Zachowanie `GET /concepts`:

- Podane tylko `term` -> zakres domyślny ECL `<< 138875005` (SNOMED CT Concept i potomkowie) z filtrem tekstowym `term`.
- Podane `ecl` (z `term` lub bez) -> `ecl` jako zakres, `term` jako filtr.
- Walidacja lokalna (400): brak `ecl` i `term` ("Wymagany jest parametr ecl lub term"); `ecl` > 2000 znaków; `term` > 200 znaków; `limit` poza `1..HIS_SNOWSTORM_MAX_PAGE_SIZE` (domyślnie 100); `offset` < 0. Zły typ `limit`/`offset` (np. tekst) -> 422 z globalnego handlera.
- `sctid` niepasujący do `\d{6,18}` -> 400. Nieznany kod (Snowstorm: 400/404/422 na `$lookup`) -> 404.

## Integracja ze Snowstorm Lite

- Protokół: FHIR R4 JSON (`Accept: application/fhir+json`), klient HTTP Springa (`RestClient`, JDK `HttpClient`).
- Wyszukiwanie: `GET {baseUrl}/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=ecl/<ECL>&count=&offset=&displayLanguage=[&filter=]`. `total` z `expansion.total`, pojęcia z `expansion.contains[]` (`code`, `display`).
- Szczegóły pojęcia: `GET {baseUrl}/CodeSystem/$lookup?system=http://snomed.info/sct&code=&displayLanguage=`; `display` z parametru `display`. Brak `display` w odpowiedzi -> 404.
- Kontrola dostępności (`isAvailable`): `GET /metadata?_summary=true`; nie jest eksponowana jako endpoint.
- Klient jest leniwy: **połączenie nawiązywane przy pierwszym wywołaniu**, backend startuje także bez Snowstorma.

### Zmienne konfiguracyjne

| Zmienna | Właściwość | Domyślnie (aplikacja) | Uwagi |
| --- | --- | --- | --- |
| `HIS_SNOWSTORM_ENABLED` | `his.terminology.snowstorm.enabled` | `false` | ta sama wartość domyślna w `deploy/compose.yml` |
| `HIS_SNOWSTORM_URL` | `...base-url` | `http://localhost:8080/fhir` | w compose na sztywno `http://snowstorm-lite:8080/fhir` |
| `HIS_SNOWSTORM_CONNECT_TIMEOUT` | `...connect-timeout` | `2s` | przekazywana w compose |
| `HIS_SNOWSTORM_READ_TIMEOUT` | `...read-timeout` | `5s` | |
| `HIS_SNOWSTORM_DISPLAY_LANGUAGE` | `...display-language` | `pl,en` | parametr `displayLanguage` |
| `HIS_SNOWSTORM_MAX_PAGE_SIZE` | `...max-page-size` | `100` | górna granica `limit` |

## Błędy (`TerminologyExceptionHandler`)

Handler ma najwyższy priorytet i dotyczy tylko `TerminologyController`. Odpowiedzi to `application/problem+json` z polskim `title`, **bez `code`** (poza 404), `type` domyślny.

| Status | Kiedy | `title` | `code` |
| --- | --- | --- | --- |
| 400 | błędne dane wejściowe (patrz wyżej) lub odrzucenie zapytania przez serwer terminologii (HTTP 400/404/422 na `$expand`, HTTP 501 = nieobsługiwana funkcja ECL) | "Niepoprawne zapytanie terminologiczne" | brak |
| 404 | nieznane pojęcie (`$lookup`) | "Nie znaleziono pojecia" | `NOT_FOUND` |
| 503 | integracja wyłączona (`enabled=false`), timeout, brak połączenia, inny HTTP 4xx/5xx serwera terminologii, niepoprawna odpowiedź | "Serwer terminologii niedostepny" | brak |

Kolejność sprawdzeń w `expandEcl`: walidacja wejścia (400) przed sprawdzeniem `enabled` (503), więc błędne parametry dają 400 także przy wyłączonej integracji.

## SNOMED poza SQL

W bazie nie ma tabel ani danych SNOMED CT (49 tabel schematu: [database-and-data.md](database-and-data.md)). Słownik ICD-10 (`icd10_code`, ok. 40 kodów) jest osobny i obsługiwany przez `GET /dictionaries/icd-10`. Dane SNOMED trafiają do Snowstorm Lite przez ręczny import RF2 (`/fhir-admin/load-package`; procedura: `.github/pipeline_docs/production_deployment.md`); `Coding.system` `ICD-10`/`LOINC`/`ATC`/`ICD-9-PL`/`local` nie zawiera SNOMED, a żaden endpoint zapisu nie używa terminologii.

Wdrożenie Snowstorm Lite: usługa `snowstorm-lite` w compose pod profilem `terminology` ([deployment-and-config.md](deployment-and-config.md)).
