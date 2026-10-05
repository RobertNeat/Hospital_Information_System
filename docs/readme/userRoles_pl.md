# Role użytkowników

Role dostępne w systemie wraz z opisem, do czego uprawniają i za co odpowiada osoba pracująca w danej roli.

## Administrator

Zarządza kontami użytkowników i organizacją szpitala. Zatwierdza rejestrację nowych pracowników, blokuje
konta w razie potrzeby i może je odblokować. Zarządza listą pracowników i oddziałów. Ma wgląd w dokumentację
medyczną pacjentów, zlecenia, wyniki i recepty, może przyjmować i wypisywać pacjentów oraz ustawiać progi
alarmowe parametrów życiowych. Administrator nie wystawia samodzielnie notatek medycznych, diagnoz, zleceń
ani recept - to leży w zakresie ról klinicznych.

**Rejestracja i aktywacja konta:** nowy pracownik rejestruje się samodzielnie, podając swoje dane i
docelową rolę. Konto zostaje utworzone w stanie oczekującym i nie pozwala na zalogowanie, dopóki
administrator go nie zatwierdzi. Administrator przegląda zgłoszenia i aktywuje konta, które uzna za
uprawnione. Administrator może też zablokować aktywne konto (np. po odejściu pracownika lub w razie
podejrzenia nieuprawnionego dostępu) oraz odblokować konto zablokowane wcześniej - tym samym mechanizmem co
aktywacja nowego konta. Konto blokuje się też automatycznie po kilku nieudanych próbach logowania; taka
blokada ustępuje samoczynnie po pewnym czasie albo może zostać zdjęta ręcznie przez administratora.

## Lekarz

Prowadzi leczenie pacjenta: przyjmuje i wypisuje pacjentów, prowadzi dokumentację medyczną (notatki,
diagnozy, alergie), wystawia i anuluje zlecenia badań laboratoryjnych oraz obrazowych, zatwierdza ich
wyniki, wystawia i anuluje recepty. Odczytuje i uzupełnia parametry życiowe pacjenta, odbiera i potwierdza
alerty kliniczne, uczestniczy w zadaniach zespołowych.

## Pielęgniarka

Opiekuje się pacjentem na oddziale: przegląda przyjęcia, prowadzi notatki pielęgniarskie i zapisuje
alergie, odczytuje zlecenia laboratoryjne i potwierdza pobranie materiału do badania. Przegląda wyniki
obrazowe i recepty, uzupełnia parametry życiowe, uczestniczy w zadaniach zespołowych, odbiera i potwierdza
alerty kliniczne.

## Laborant

Obsługuje zlecenia badań laboratoryjnych: przegląda zlecenia, zmienia ich status w toku realizacji (np. po
pobraniu materiału, w trakcie analizy, po zakończeniu badania) i wprowadza wyniki. Ma ograniczony wgląd w
dokumentację medyczną pacjenta (tylko informacje potrzebne do realizacji badania), odczytuje katalog leków i
odbiera alerty kliniczne.

## Radiolog

Obsługuje zlecenia badań obrazowych: przegląda zlecenia, zmienia ich status w toku realizacji i wprowadza
wyniki wraz z opisem. Ma ograniczony wgląd w dokumentację medyczną pacjenta i notatki konsultacyjne,
odbiera alerty kliniczne.

## Farmaceuta

Weryfikuje recepty: przegląda wystawione recepty, sprawdza katalog leków oraz interakcje i
bezpieczeństwo leku dla danego pacjenta. Ma ograniczony wgląd w dokumentację medyczną pacjenta.

## Rejestrator

Rejestruje nowych pacjentów w systemie i zajmuje się ich przyjęciem na oddział lub do poradni.

## Dla każdej roli

Niezależnie od roli, każdy użytkownik może: przeglądać dane pacjentów, czytać i wysyłać wiadomości,
przeglądać listę pracowników i oddziałów oraz korzystać z dashboardu.

## Logowanie

Logowanie wymaga aktywnego konta i poprawnego hasła. Po kilku nieudanych próbach logowania konto zostaje
tymczasowo zablokowane (zob. sekcja Administrator). Dodatkowo serwer ogranicza liczbę prób logowania z
jednego adresu IP w jednostce czasu, niezależnie od blokady konta.

## Konta demo

Konta ładowane zawsze, również na produkcji - przeznaczone wyłącznie do dema/testów (login = hasło).
Wszystkie konta są przypisane do oddziału demonstracyjnego.

| Login            | Rola          |
| ---------------- | ------------- |
| `admin`          | administrator |
| `user`, `doctor` | lekarz        |
| `nurse`          | pielęgniarka  |
| `lab-tech`       | laborant      |
| `radiologist`    | radiolog      |
| `pharmacist`     | farmaceuta    |
| `registrar`      | rejestrator   |

Hasła tych kont są trywialne i publicznie znane (znajdują się w repozytorium). Przed użyciem systemu z
prawdziwymi danymi pacjentów należy zablokować te konta lub zmienić ich hasła.

## Konta demonstracyjne (rozszerzone dane testowe)

Dziesięcioro pracowników z przypisaniem do konkretnych, realistycznych oddziałów - używane tylko w
środowisku deweloperskim/demo z rozszerzonymi danymi testowymi. Login w formacie `EMP-0001`...`EMP-0010`,
wspólne hasło `HisDemo2026!`.

| Login    | Pracownik           | Rola                    | Oddział                  |
| -------- | ------------------- | ----------------------- | ------------------------ |
| EMP-0001 | Anna Nowak          | lekarz                  | Choroby wewnętrzne       |
| EMP-0002 | Piotr Wiśniewski    | lekarz                  | Kardiologia              |
| EMP-0003 | Tomasz Kamiński     | lekarz                  | Chirurgia ogólna         |
| EMP-0004 | Marta Lewandowska   | lekarz                  | Neurologia               |
| EMP-0005 | Paweł Zieliński     | lekarz                  | Medycyna ratunkowa (SOR) |
| EMP-0006 | Katarzyna Zielińska | pielęgniarka            | Choroby wewnętrzne       |
| EMP-0007 | Magdalena Wójcik    | pielęgniarka            | Kardiologia              |
| EMP-0008 | Ewa Lewandowska     | pielęgniarka oddziałowa | Chirurgia                |
| EMP-0009 | Agnieszka Kaczmarek | pielęgniarka            | Neurologia               |
| EMP-0010 | Michał Szymański    | pielęgniarka            | SOR                      |
