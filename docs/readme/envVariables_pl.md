# Zmienne środowiskowe

Nazwy, znaczenie i przyjmowane wartości zmiennych środowiskowych używanych w projekcie.

## Baza danych (his_backend)

| Zmienna                  | Znaczenie                                               | Domyślnie                                               | Wymagana       |
| ------------------------ | ------------------------------------------------------- | ------------------------------------------------------- | -------------- |
| `HIS_DB_URL`             | adres połączenia do bazy danych                         | brak (dev: lokalna baza na porcie 5432)                 | tak            |
| `HIS_DB_USER`            | użytkownik bazy danych                                  | brak (dev: `his`)                                       | tak            |
| `HIS_DB_PASSWORD`        | hasło do bazy danych                                    | brak (dev: `his`)                                       | tak            |
| `HIS_DB_NAME`            | nazwa bazy danych kontenera (tylko Docker Compose)      | `his`                                                   | tak w Dockerze |
| `HIS_DB_HOST_PORT`       | port hosta bazy danych (tylko środowisko deweloperskie) | `5432`                                                  | nie            |
| `HIS_LIQUIBASE_CONTEXTS` | zestaw danych początkowych do załadowania               | podstawowy (dev: podstawowy + rozszerzone dane testowe) | nie            |

## Uwierzytelnianie (his_backend)

| Zmienna                    | Znaczenie                                          | Domyślnie                                         | Wymagana |
| -------------------------- | -------------------------------------------------- | ------------------------------------------------- | -------- |
| `HIS_JWT_SECRET`           | klucz do podpisywania tokenów dostępu              | **pusty = błąd startu** (dev: klucz deweloperski) | tak      |
| `HIS_JWT_TTL`              | czas życia tokenu dostępu                          | 15 minut                                          | nie      |
| `HIS_JWT_ISSUER`           | nazwa wydawcy tokenu                               | nazwa systemu głównego                            | nie      |
| `HIS_LOCKOUT_MAX_ATTEMPTS` | liczba nieudanych prób logowania do blokady konta  | 5                                                 | nie      |
| `HIS_LOCKOUT_DURATION`     | czas blokady konta po przekroczeniu liczby prób    | 15 minut                                          | nie      |
| `HIS_CORS_ALLOWED_ORIGINS` | dozwolone źródła dla zapytań z innej domeny (CORS) | brak (CORS wyłączony)                             | nie      |
| `HIS_WS_ALLOWED_ORIGINS`   | dozwolone źródła dla połączenia WebSocket          | brak (tylko to samo źródło)                       | nie      |

## Terminologia SNOMED CT / Snowstorm Lite (his_backend)

| Zmienna                          | Znaczenie                              | Domyślnie                         | Wymagana                   |
| -------------------------------- | -------------------------------------- | --------------------------------- | -------------------------- |
| `HIS_SNOWSTORM_ENABLED`          | włącza integrację terminologiczną      | wyłączona                         | nie                        |
| `HIS_SNOWSTORM_URL`              | adres usługi Snowstorm Lite            | lokalny/wewnątrz sieci kontenerów | nie                        |
| `HIS_SNOWSTORM_CONNECT_TIMEOUT`  | limit czasu połączenia                 | 2 sekundy                         | nie                        |
| `HIS_SNOWSTORM_READ_TIMEOUT`     | limit czasu odczytu odpowiedzi         | 5 sekund                          | nie                        |
| `HIS_SNOWSTORM_DISPLAY_LANGUAGE` | język wyświetlanych nazw terminów      | polski, angielski                 | nie                        |
| `HIS_SNOWSTORM_MAX_PAGE_SIZE`    | maksymalna liczba wyników wyszukiwania | 100                               | nie                        |
| `HIS_SNOWSTORM_ADMIN_USER`       | login administratora Snowstorm Lite    | `admin`                           | nie                        |
| `HIS_SNOWSTORM_ADMIN_PASSWORD`   | hasło administratora Snowstorm Lite    | `admin`                           | nie (zmienić na produkcji) |
| `HIS_SNOWSTORM_HOST_PORT`        | port hosta Snowstorm Lite (tylko dev)  | 8080                              | nie                        |

- Bez zaimportowanych danych terminologicznych włączona integracja zwraca błąd dla każdego zapytania.

## mTLS i integracja FHIR (his_backend, e-receipt, e-laboratory, e-imaging)

| Zmienna                                       | Znaczenie                                                               | Domyślnie                                | Wymagana   |
| --------------------------------------------- | ----------------------------------------------------------------------- | ---------------------------------------- | ---------- |
| `HIS_MTLS_ENABLED`                            | włącza mTLS; wyłączenie oznacza tryb bez certyfikatów (testy, IDE)      | włączona                                 | nie        |
| `HIS_FHIR_PORT`                               | port FHIR (mTLS) systemu głównego                                       | 10424                                    | nie        |
| `HIS_MANAGEMENT_PORT`                         | port stanu zdrowia systemu głównego                                     | 10440                                    | nie        |
| `HIS_FHIR_ALLOWED_CLIENT_CNS`                 | dozwolone nazwy certyfikatów klienta na porcie FHIR                     | serwisy e-receipt/e-laboratory/e-imaging | nie        |
| `HIS_CERTS_DIR`                               | katalog z certyfikatami montowany do kontenerów (podkatalog per usługa) | katalog `.certs` w repozytorium          | nie        |
| `MTLS_KEYSTORE`, `MTLS_KEYSTORE_PASSWORD`     | magazyn klucza/certyfikatu usługi i jego hasło                          | ścieżka lokalna; hasło wymagane          | hasło: tak |
| `MTLS_TRUSTSTORE`, `MTLS_TRUSTSTORE_PASSWORD` | magazyn urzędu certyfikacji i jego hasło                                | ścieżka lokalna; hasło wymagane          | hasło: tak |
| `MTLS_KEY_ALIAS`                              | alias klucza w magazynie                                                | zależny od usługi                        | nie        |

Porty interfejsu webowego i stanu zdrowia serwisów e-* są konfigurowalne osobno dla każdego serwisu (zob.
[localDeploy](localDeploy_pl.md) dla wartości domyślnych).

### Przełączniki integracji systemu głównego z serwisami e-*

| Zmienna                                                      | Znaczenie                             | Domyślnie                         |
| ------------------------------------------------------------ | ------------------------------------- | --------------------------------- |
| `HIS_ERECEIPT_ENABLED`                                       | włącza wysyłkę recept do e-receipt    | wyłączona                         |
| `HIS_ERECEIPT_URL`                                           | adres FHIR e-receipt                  | lokalny/wewnątrz sieci kontenerów |
| `HIS_ERECEIPT_CONNECT_TIMEOUT` / `HIS_ERECEIPT_READ_TIMEOUT` | limity czasu połączenia/odczytu       | 1 / 3 sekundy                     |
| `HIS_ERECEIPT_SSL_BUNDLE`                                    | nazwa zestawu certyfikatów mTLS       | zestaw mTLS                       |
| `HIS_ELAB_ENABLED`                                           | włącza wysyłkę zleceń do e-laboratory | wyłączona                         |
| `HIS_ELAB_URL`                                               | adres FHIR e-laboratory               | lokalny/wewnątrz sieci kontenerów |
| `HIS_ELAB_CONNECT_TIMEOUT` / `HIS_ELAB_READ_TIMEOUT`         | limity czasu połączenia/odczytu       | 1 / 3 sekundy                     |
| `HIS_ELAB_SSL_BUNDLE`                                        | nazwa zestawu certyfikatów mTLS       | zestaw mTLS                       |
| `HIS_EIMG_ENABLED`                                           | włącza wysyłkę zleceń do e-imaging    | wyłączona                         |
| `HIS_EIMG_URL`                                               | adres FHIR e-imaging                  | lokalny/wewnątrz sieci kontenerów |
| `HIS_EIMG_CONNECT_TIMEOUT` / `HIS_EIMG_READ_TIMEOUT`         | limity czasu połączenia/odczytu       | 1 / 3 sekundy                     |
| `HIS_EIMG_SSL_BUNDLE`                                        | nazwa zestawu certyfikatów mTLS       | zestaw mTLS                       |

### Przełączniki integracji serwisów e-* z systemem głównym (wywołanie zwrotne)

| Zmienna                                                              | Znaczenie                                                 | Domyślnie                         |
| -------------------------------------------------------------------- | --------------------------------------------------------- | --------------------------------- |
| `ERECEIPT_HIS_ENABLED`                                               | włącza wywołania zwrotne e-receipt do systemu głównego    | wyłączona                         |
| `ERECEIPT_HIS_URL`                                                   | adres FHIR systemu głównego                               | lokalny/wewnątrz sieci kontenerów |
| `ERECEIPT_HIS_CONNECT_TIMEOUT` / `ERECEIPT_HIS_READ_TIMEOUT`         | limity czasu połączenia/odczytu                           | 1 / 3 sekundy                     |
| `ERECEIPT_HIS_SSL_BUNDLE`                                            | nazwa zestawu certyfikatów mTLS                           | zestaw mTLS                       |
| `ERECEIPT_UI_PORT`                                                   | port interfejsu webowego e-receipt                        | 10431                             |
| `ERECEIPT_MANAGEMENT_PORT`                                           | port stanu zdrowia e-receipt                              | 10441                             |
| `E_LABORATORY_HIS_ENABLED`                                           | włącza wywołania zwrotne e-laboratory do systemu głównego | wyłączona                         |
| `E_LABORATORY_HIS_URL`                                               | adres FHIR systemu głównego                               | lokalny/wewnątrz sieci kontenerów |
| `E_LABORATORY_HIS_CONNECT_TIMEOUT` / `E_LABORATORY_HIS_READ_TIMEOUT` | limity czasu połączenia/odczytu                           | 1 / 3 sekundy                     |
| `E_LABORATORY_HIS_SSL_BUNDLE`                                        | nazwa zestawu certyfikatów mTLS                           | zestaw mTLS                       |
| `E_LABORATORY_UI_PORT`                                               | port interfejsu webowego e-laboratory                     | 10432                             |
| `E_LABORATORY_MANAGEMENT_PORT`                                       | port stanu zdrowia e-laboratory                           | 10442                             |
| `E_IMAGING_HIS_ENABLED`                                              | włącza wywołania zwrotne e-imaging do systemu głównego    | wyłączona                         |
| `E_IMAGING_HIS_URL`                                                  | adres FHIR systemu głównego                               | lokalny/wewnątrz sieci kontenerów |
| `E_IMAGING_HIS_CONNECT_TIMEOUT` / `E_IMAGING_HIS_READ_TIMEOUT`       | limity czasu połączenia/odczytu                           | 1 / 3 sekundy                     |
| `E_IMAGING_HIS_SSL_BUNDLE`                                           | nazwa zestawu certyfikatów mTLS                           | zestaw mTLS                       |
| `E_IMAGING_UI_PORT`                                                  | port interfejsu webowego e-imaging                        | 10433                             |
| `E_IMAGING_MANAGEMENT_PORT`                                          | port stanu zdrowia e-imaging                              | 10443                             |

- Nazwy zmiennych portów interfejsu i stanu zdrowia dla e-receipt różnią się wewnątrz kontenera od nazw
  używanych w plikach konfiguracyjnych - to zamierzona niekonsekwencja historyczna, nie błąd.

## Frontend (his_frontend)

Frontend nie czyta żadnych zmiennych środowiskowych w czasie działania - jest statyczną aplikacją, a adres
systemu głównego jest ustalony na etapie budowania. Jedyna zmienna dotyczy samego procesu budowania:

| Zmienna               | Znaczenie                                                                                                       | Domyślnie |
| --------------------- | --------------------------------------------------------------------------------------------------------------- | --------- |
| `HIS_PERSIST_SESSION` | ustawiana przy budowaniu aplikacji; wyłączenie oznacza, że sesja logowania kończy się przy przeładowaniu strony | włączona  |

## Zmienne tylko dla Docker Compose (nieczytane przez aplikacje)

Zmienne sterujące nazwami obrazów, portami kontenerów i metadanymi Dockera - używane wyłącznie przez
proces budowania i wdrożenia, nie przez kod aplikacji.

## Uwagi

- Pliki zmiennych środowiskowych (lokalny plik deweloperski, pliki konfiguracyjne na produkcji) służą do
  interpolacji wartości w plikach Docker Compose - do kontenerów trafia tylko lista zmiennych jawnie
  zdefiniowana w pliku Compose.
- Przykładowe wartości znajdują się w szablonach konfiguracji (produkcyjnym i deweloperskim) - wartości
  wymagające zmiany na serwerze są w nich oznaczone.
- Na serwerze produkcyjnym należy zmienić: hasło bazy danych, klucz do podpisywania tokenów, hasło
  administratora Snowstorm Lite oraz wszystkie hasła certyfikatów mTLS. Istniejący plik konfiguracyjny na
  serwerze nie jest nadpisywany przy wdrożeniu - nowe zmienne trzeba dopisać ręcznie.
