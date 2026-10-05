<p align="center">
  <img src="../../apps/his_frontend/public/logo.png" width="150" alt="Logo HIS">
</p>

<h1 align="center">Hospital Information System (HIS)</h1>

_English version: [../../README.md](../../README.md)_

Hospital Information System (HIS) jest platformą kliniczną klasy enterprise, zbudowaną od podstaw wokół
standardów informacji medycznej. Łączy opiekę nad pacjentem, diagnostykę i przepisywanie leków w jeden
spójny przepływ pracy, mówiąc tym samym językiem terminologii i wymiany danych, co rzeczywiste systemy
ochrony zdrowia - SNOMED CT, HL7 FHIR R4 i klasyfikacja leków ATC. Efektem jest system zaprojektowany jako
szkielet IT nowoczesnego szpitala, nie prototyp, który tylko na niego wygląda.

## Użyte standardy enterprise

- **SNOMED CT** - najbardziej obszerna terminologia kliniczna na świecie, używana w diagnozach i
  wskazaniach klinicznych, z kodami ICD-10 wyliczanymi na żądanie.
- **ATC (Anatomical Therapeutic Chemical)** - standard klasyfikacji leków WHO, na którym działa katalog
  leków i automatyczne sprawdzanie interakcji.
- **HL7 FHIR R4** - wiodący standard interoperacyjności w ochronie zdrowia, używany w każdej integracji z
  zewnętrznymi systemami recept, badań laboratoryjnych i obrazowych.
- **mTLS (wzajemny TLS, X.509)** - wzajemne uwierzytelnianie certyfikatami zabezpieczające każdą integrację
  między serwisami.
- **OpenAPI** - żywa, zawsze aktualna specyfikacja REST API.

## Co umożliwia HIS

- **Rejestracja i przyjęcia pacjentów** - kreator rejestracji z bieżącym wykrywaniem duplikatów, a następnie
  przyjęcie na oddział lub do opieki ambulatoryjnej.
- **Dokumentacja medyczna (EHR)** - notatki, diagnozy i alergie, zorganizowane per pacjent i per wizyta.
- **Zlecenia laboratoryjne** - od wystawienia zlecenia, przez śledzenie jego realizacji, do odczytu wyniku.
- **Zlecenia obrazowe** - planowanie badania, listy bezpieczeństwa i opisy radiologa.
- **E-recepty** - wystawianie, wydawanie i anulowanie recept, ze sprawdzaniem interakcji leków i refundacji.
- **Monitorowanie parametrów życiowych** - ciągły zapis z konfigurowalnymi progami alarmowymi.
- **Alerty kliniczne i wiadomości** - wyniki krytyczne, anomalie parametrów życiowych i komunikacja
  zespołowa w jednym miejscu.
- **Zadania zespołowe i przekazanie zmiany** - koordynacja pracy i przekazywanie pacjentów między zmianami
  i oddziałami.
- **Dashboardy zależne od roli** - każda rola widzi informacje i akcje odpowiednie do swojej pracy.

Pełny zakres uprawnień poszczególnych ról: [userRoles_pl.md](userRoles_pl.md).

## Dokumentacja

| Dokument                                     | Zawartość                                                             |
| -------------------------------------------- | --------------------------------------------------------------------- |
| [userRoles_pl.md](userRoles_pl.md)           | Role, co umożliwiają, konta demo                                      |
| [localDeploy_pl.md](localDeploy_pl.md)       | Porty, pierwsze uruchomienie, uruchomienie lokalne (z Dockerem i bez) |
| [envVariables_pl.md](envVariables_pl.md)     | Zmienne środowiskowe całego projektu                                  |
| [databaseSchema_pl.md](databaseSchema_pl.md) | Schemat encji, relacje, dane początkowe                               |
| [services_pl.md](services_pl.md)             | Serwisy, komunikacja między nimi, dokumentacja API                    |
| [basicWorkflows_pl.md](basicWorkflows_pl.md) | Główne przebiegi procesowe systemu                                    |

Dokumentacja wdrożenia produkcyjnego (CI/CD, serwer): `.github/pipeline_docs/production_deployment.md`.

---

This project references SNOMED Clinical Terms® (SNOMED CT®), a clinical terminology maintained by SNOMED
International (www.snomed.org). No SNOMED CT content is included in this repository. Users wishing to work
with real SNOMED CT data must obtain their own license via MLDS (mlds.ihtsdotools.org).
