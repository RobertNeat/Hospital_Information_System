<p align="center">
  <img src="../../apps/his_frontend/public/logo.png" width="150" alt="Logo HIS">
</p>

<h1 align="center">Hospital Information System (HIS)</h1>


<p align="center">
  <a href="https://robertneat.github.io/Hospital_Information_System/">
    <img src="https://img.shields.io/badge/Dokumentacja-1d5fa8?style=for-the-badge" alt="Dokumentacja">
  </a>
  <br>
  <a href="https://github.com/RobertNeat/Hospital_Information_System/blob/main/docs/readme/../../README.md">
    <img src="https://img.shields.io/badge/Angielska_wersja_dokumentu-1d5fa8?style=for-the-badge" alt="Angielska wersja">
  </a>
</p>

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

## Główne integracje wspierane przez HIS

HIS nie jest pojedynczą, zamkniętą aplikacją - zaprojektowano go tak, by pokazać, jak system szpitalny łączy
się ze światem zewnętrznym. Każdy z poniższych przepływów jest podróżą tam i z powrotem między dwoma
niezależnymi systemami: akcja lekarza w HIS tworzy zlecenie, które jest wysyłane do systemu zewnętrznego
(apteki, zakładu radiologii lub laboratorium) przez bezpieczny, wzajemnie uwierzytelniony kanał (**mTLS**),
w standardowym formacie danych klinicznych (**HL7 FHIR R4**). System zewnętrzny przetwarza zlecenie w swoim
tempie i zgłasza każdą zmianę statusu oraz ostateczny wynik z powrotem do HIS tą samą bezpieczną drogą, dzięki
czemu obie strony zawsze pokazują ten sam, zgodny status tego samego zlecenia.

### Integracja z przepływem e-recepty

Lekarz wystawia e-receptę w HIS. HIS wysyła ją jako rekord recepty w formacie FHIR do zewnętrznego systemu
e-recepty reprezentującego aptekę, który zwraca kod odbioru umożliwiający pacjentowi wykupienie leku. W miarę
jak apteka wydaje lek (w całości, częściowo lub wcale), zgłasza zaktualizowany status z powrotem do HIS, co
jest widoczne na żywo w statusie recepty widzianym przez lekarza.

![Integracja z przepływem e-recepty](../media/wystawianie_recepty_workflow.png)

### Integracja z przepływem badania obrazowego (e-imaging)

Lekarz zleca badanie obrazowe (np. RTG lub CT) w HIS. HIS wysyła zlecenie jako żądanie FHIR do zewnętrznego
systemu e-imaging reprezentującego zakład radiologii. W miarę jak badanie jest planowane, wykonywane i
opisywane, system radiologiczny przekazuje każdą zmianę statusu z powrotem do HIS, kończąc opisem radiologa -
widocznym dla lekarza bezpośrednio w dokumentacji pacjenta.

![Integracja z przepływem badania obrazowego](../media/badanie_obrazowe_workflow.png)

### Integracja z przepływem zlecenia laboratoryjnego (e-lab)

Lekarz zleca badanie laboratoryjne w HIS. HIS wysyła zlecenie jako żądanie FHIR do zewnętrznego systemu e-lab
reprezentującego laboratorium. W miarę jak próbka jest pobierana, analizowana i powstają wyniki, system
laboratoryjny zgłasza każdy etap z powrotem do HIS, który zapisuje ostateczne wyniki - oznaczając wartości
odbiegające od normy do przeglądu przez lekarza.

![Integracja z przepływem zlecenia laboratoryjnego](../media/zlecenie_laboratoryjne_workflow.png)

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
| [Strona GitHub Pages](https://robertneat.github.io/Hospital_Information_System/) | Dokumentacja poszczególnych ekranów: ścieżka nawigacji, przeznaczenie, dane wejściowe, akcje |

Dokumentacja wdrożenia produkcyjnego (CI/CD, serwer): `.github/pipeline_docs/production_deployment.md`.

---

This project references SNOMED Clinical Terms® (SNOMED CT®), a clinical terminology maintained by SNOMED
International (www.snomed.org). No SNOMED CT content is included in this repository. Users wishing to work
with real SNOMED CT data must obtain their own license via MLDS (mlds.ihtsdotools.org).
