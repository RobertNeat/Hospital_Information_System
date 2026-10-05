# Uruchomienie lokalne

Porty, pierwsze uruchomienie (certyfikaty i dane terminologiczne) oraz komendy uruchomienia aplikacji -
jako pojedyncze procesy albo w Dockerze.

## Porty - interfejsy

| Usługa                      | Port  | Zawartość                                                   |
| --------------------------- | ----- | ----------------------------------------------------------- |
| his_frontend                | 10400 | aplikacja dla personelu szpitala                            |
| his_backend                 | 10420 | API, WebSocket, dokumentacja API                            |
| e-receipt                   | 10431 | interfejs do zmiany stanu recept (bez logowania)            |
| e-laboratory                | 10432 | interfejs do zmiany stanu zleceń lab (bez logowania)        |
| e-imaging                   | 10433 | interfejs do zmiany stanu zleceń obrazowych (bez logowania) |
| Snowstorm Lite (opcjonalny) | 8080  | przeglądarka terminologii i import danych                   |
| PostgreSQL                  | 5432  | baza danych systemu                                         |

## Porty - komunikacja między serwisami

| Z            | Do             | Protokół     | Zawartość                               |
| ------------ | -------------- | ------------ | --------------------------------------- |
| e-receipt    | his_backend    | HTTPS + mTLS | zmiana stanu recepty                    |
| e-laboratory | his_backend    | HTTPS + mTLS | zmiana stanu zlecenia, wynik badania    |
| e-imaging    | his_backend    | HTTPS + mTLS | zmiana stanu zlecenia, wynik badania    |
| his_backend  | e-receipt      | HTTPS + mTLS | wysyłka recepty                         |
| his_backend  | e-laboratory   | HTTPS + mTLS | wysyłka zlecenia                        |
| his_backend  | e-imaging      | HTTPS + mTLS | wysyłka zlecenia                        |
| his_frontend | his_backend    | HTTP         | API, WebSocket (przez proxy)            |
| his_backend  | PostgreSQL     | TCP          | połączenie do bazy danych               |
| his_backend  | Snowstorm Lite | HTTP         | wyszukiwanie i tłumaczenie terminologii |

Porty interfejsów (powyższa tabela) są dostępne tylko lokalnie, na uruchomieniu deweloperskim. Na
produkcji na host publikowane są wyłącznie his_frontend i his_backend - pozostałe usługi są dostępne
jedynie w wewnętrznej sieci kontenerów. Porty można zmienić zmiennymi konfiguracyjnymi, zob.
[envVariables](envVariables_pl.md).

## Pierwsze uruchomienie (jednorazowo)

Czynności, które trzeba wykonać raz, przed pierwszym uruchomieniem pełnego stosu - niezależnie od tego, czy
dalej aplikacje są uruchamiane jako pojedyncze procesy czy w Dockerze.

1. **Certyfikaty mTLS.** Integracja FHIR między systemem głównym a serwisami e-receipt/e-laboratory/
   e-imaging wymaga certyfikatów klienta i serwera podpisanych wspólnym urzędem certyfikacji. Skrypt
   generujący certyfikaty tworzy komplet plików dla wszystkich usług i nie nadpisuje istniejącego urzędu
   certyfikacji, jeśli już istnieje.
2. **Plik z hasłami do uruchomienia Dockera.** Uruchomienie w Dockerze wymaga pliku ze zmiennymi
   środowiskowymi zawierającego hasła do magazynów certyfikatów wygenerowanych w poprzednim kroku. Plik
   nie jest częścią repozytorium - trzeba go utworzyć na podstawie szablonu i wpisać do niego
   wygenerowane hasła.
3. **Import terminologii SNOMED CT (opcjonalnie).** Integracja z terminologią medyczną wymaga ważnej
   licencji SNOMED CT i jednorazowego importu danych do usługi Snowstorm Lite. Bez tego kroku integracja
   terminologiczna pozostaje wyłączona, a reszta systemu działa normalnie. Szczegóły importu: zob. sekcję
   "Import danych terminologicznych" niżej.

## Uruchomienie jako pojedyncze procesy (bez Dockera)

Wymaga uruchomionej bazy danych (lokalny PostgreSQL albo tylko kontener bazy danych ze stosu Docker).
W tym trybie certyfikaty mTLS nie są potrzebne, ale integracja z e-receipt/e-laboratory/e-imaging jest
wtedy wyłączona.

Kolejność uruchomienia: instalacja zależności, uruchomienie samej bazy danych, uruchomienie backendu, a
następnie frontendu. Domyślne logowanie demo w tym trybie: `admin` / `admin`.

Usługi e-receipt/e-laboratory/e-imaging jako pojedyncze procesy wymagają wcześniej wygenerowanych
certyfikatów mTLS (zob. "Pierwsze uruchomienie") oraz wskazania ich lokalizacji w konfiguracji. Bez mTLS te
usługi wystawiają swój interfejs FHIR bez uwierzytelniania - wyłącznie do testów.

## Uruchomienie w Dockerze (Docker Compose)

Pełny stos (baza danych, system główny, frontend, trzy serwisy symulujące, opcjonalnie Snowstorm Lite)
uruchamia się jedną komendą Docker Compose, z nałożonym plikiem konfiguracji deweloperskiej i plikiem
zmiennych środowiskowych przygotowanym w kroku "Pierwsze uruchomienie".

Certyfikaty mTLS są montowane do kontenerów tylko do odczytu. Integracja mTLS jest domyślnie włączona i
może być wyłączona w konfiguracji (np. do testów bez certyfikatów).

Po zakończeniu pracy stos należy zatrzymać, aby zwolnić zajęte porty.

### Pełny stos ze Snowstorm Lite

Snowstorm Lite jest usługą opcjonalną, uruchamianą dodatkowym profilem w Docker Compose. Dane terminologii
są trwałe (przechowywane w wolumenie) - import wykonuje się tylko raz, nie trzeba go powtarzać po każdym
uruchomieniu.

## Import danych terminologicznych SNOMED CT

Wymaga ważnej licencji SNOMED CT (afiliacja przez krajowy ośrodek dystrybucji terminologii). Paczka danych
w formacie RF2 nie jest częścią repozytorium.

1. Uruchomić usługę Snowstorm Lite (profil terminologiczny Docker Compose).
2. Zaimportować paczkę RF2 przez interfejs administracyjny Snowstorm Lite - import jednej edycji trwa
   około 5 minut, a kolejny import zastępuje poprzedni.
3. Zweryfikować powodzenie importu w przeglądarce terminologii Snowstorm Lite.
4. Włączyć integrację terminologiczną w konfiguracji systemu głównego i zrestartować stos - bez tego kroku
   zapytania o terminologię zwracają błąd.
5. Po zakończeniu pracy z terminologią profil Snowstorm Lite można zatrzymać niezależnie od reszty stosu.
