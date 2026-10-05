# Główne przebiegi procesowe

Opis głównych przebiegów procesowych systemu: rejestracji pacjenta, zleceń badań laboratoryjnych i
obrazowych, e-recept, dokumentacji medycznej, parametrów życiowych i alertów, komunikacji zespołowej oraz
zarządzania kontem użytkownika. Każdy przebieg ma cel, założenia wejściowe, kroki i wynik końcowy.

## Rejestracja i przyjęcie pacjenta

**Cel:** zarejestrowanie nowego pacjenta w systemie i przyjęcie go na oddział lub do opieki ambulatoryjnej.

**Założenia:**

- Użytkownik ma uprawnienie do rejestracji pacjentów (rejestrator albo administrator).
- Pacjent o podanym numerze PESEL nie istnieje jeszcze w systemie.

**Kroki:**

1. Użytkownik wypełnia 4-krokowy kreator rejestracji (Tożsamość, Dane osobowe, Ubezpieczenie, Przyjęcie).
   W trakcie wpisywania numeru PESEL system sprawdza na bieżąco, czy taki pacjent już istnieje.
2. System zapisuje dane pacjenta - powstaje nowy rekord ze statusem "zarejestrowany".
3. Użytkownik uzupełnia dane przyjęcia (oddział, lekarz, powód - niewymagane dla opieki ambulatoryjnej).
4. System zapisuje wizytę i przyjęcie powiązane z tą wizytą.

**Wynik:**

- Pacjent ma status "przyjęty" (hospitalizacja) albo "pacjent ambulatoryjny".
- Jeśli krok przyjęcia się nie powiedzie, pacjent zostaje zarejestrowany, ale bez przyjęcia; użytkownik
  jest o tym informowany i może powtórzyć przyjęcie.

## Zlecenie badania laboratoryjnego

**Cel:** lekarz zleca badanie laboratoryjne, laborant je realizuje i wprowadza wynik, integracja z
e-laboratory synchronizuje status zlecenia między systemami.

**Założenia:**

- Lekarz ma uprawnienie do wystawiania zleceń laboratoryjnych.
- Pielęgniarka ma uprawnienie do potwierdzenia pobrania materiału.
- Laborant ma uprawnienie do zmiany statusu zlecenia i wprowadzania wyniku.
- Integracja z e-laboratory jest włączona w obie strony, uwierzytelnianie mTLS jest aktywne.
- Wskazanie kliniczne z kodem SNOMED CT jest opcjonalne.

**Kroki:**

1. Lekarz wypełnia kreator zlecenia badania laboratoryjnego (rodzaj badania, priorytet, wymóg bycia na
   czczo, wskazanie kliniczne).
2. System zapisuje zlecenie ze statusem początkowym "zlecone" i automatycznie przekazuje je do
   e-laboratory. Nieudana wysyłka jest ponawiana automatycznie w regularnych odstępach czasu.
3. Pielęgniarka albo laborant potwierdza pobranie materiału do badania.
4. Laborant zmienia status zlecenia w e-laboratory w miarę realizacji badania (w trakcie analizy, po
   zakończeniu badania). Każda zmiana jest najpierw potwierdzana przez system główny.
5. Laborant wprowadza wynik badania w e-laboratory.

**Wynik:**

- Wynik jest zapisany w systemie głównym tak samo, jak przy ręcznym wprowadzeniu wyniku.
- Gdy wynik jest ostateczny i wszystkie pozycje zlecenia mają zatwierdzony wynik, zlecenie automatycznie
  przechodzi do statusu "zakończone".
- Statusy zlecenia: zlecone -> zaplanowane / pobrano materiał -> w trakcie realizacji -> zakończone
  (etapy pośrednie można pominąć), albo anulowane z każdego etapu, który nie jest jeszcze zakończony.
- Statusy wyniku: wstępny, końcowy, skorygowany (tylko końcowy i skorygowany liczą się jako zatwierdzone).

## Zlecenie badania obrazowego

**Cel:** lekarz zleca badanie obrazowe, radiolog je realizuje i wprowadza wynik, integracja z e-imaging
synchronizuje status zlecenia między systemami.

**Założenia:**

- Lekarz ma uprawnienie do wystawiania zleceń obrazowych.
- Radiolog ma uprawnienie do zmiany statusu zlecenia i wprowadzania wyniku.
- Integracja z e-imaging jest włączona w obie strony, uwierzytelnianie mTLS jest aktywne.

**Kroki:**

1. Lekarz wypełnia kreator zlecenia (badanie, modalność, okolica anatomiczna, stronność, podanie
   kontrastu, wskazanie kliniczne, lista bezpieczeństwa pacjenta - ciąża, implanty metalowe, uczulenie na
   kontrast, klaustrofobia).
2. System zapisuje zlecenie ze statusem początkowym "zlecone" i automatycznie przekazuje je do e-imaging.
   Nieudana wysyłka jest ponawiana automatycznie w regularnych odstępach czasu.
3. Radiolog zmienia status zlecenia w e-imaging w miarę realizacji badania. Każda zmiana jest najpierw
   potwierdzana przez system główny.
4. Radiolog wprowadza wynik badania (opis, wniosek, flaga krytyczności).

**Wynik:**

- Wynik jest zapisany w systemie głównym; gdy jest ostateczny, zlecenie automatycznie przechodzi do
  statusu "zakończone".
- Wynik oznaczony jako krytyczny dodatkowo generuje alert kliniczny dla personelu prowadzącego pacjenta.
- Statusy zlecenia i wyniku są takie same jak przy badaniach laboratoryjnych.

## Wystawienie e-recepty

**Cel:** lekarz wystawia receptę, sprawdzając bezpieczeństwo leku, a integracja z e-receipt przekazuje
receptę do zewnętrznego systemu wydawania leków.

**Założenia:**

- Lekarz ma uprawnienie do wystawiania i anulowania recept.
- Pacjent ma udokumentowane alergie i przeciwwskazania (jeśli dotyczy) w systemie.

**Kroki:**

1. Lekarz wypełnia kreator recepty, wybierając leki z katalogu i ustawiając dawkowanie.
2. System sprawdza interakcje między przepisywanymi lekami oraz istniejącymi alergiami i
   przeciwwskazaniami pacjenta i ostrzega lekarza o wykrytych zagrożeniach.
3. Lekarz zapisuje receptę.
4. System automatycznie przekazuje receptę do e-receipt. Nieudane przekazanie jest ponawiane automatycznie
   w tle.

**Wynik:**

- Recepta jest w pełni obowiązująca w systemie głównym niezależnie od wyniku przekazania do e-receipt.
- Wydanie leku pacjentowi (pełne albo częściowe) jest odnotowywane w e-receipt i odzwierciedlane w
  systemie głównym.
- Lekarz może anulować receptę, o ile nie została jeszcze w pełni wydana.
- Statusy recepty: wystawiona -> częściowo wydana -> wydana, albo anulowana z etapu, który nie jest
  jeszcze w pełni wydany. Recepta, której termin ważności minął bez wydania, jest traktowana jako wygasła.

## Dokumentacja medyczna w trakcie wizyty

**Cel:** prowadzenie dokumentacji medycznej pacjenta (notatki, diagnozy, alergie) w trakcie wizyty lub
hospitalizacji.

**Założenia:**

- Lekarz i pielęgniarka mają uprawnienia do prowadzenia dokumentacji w zakresie swojej roli (zob.
  [userRoles_pl.md](userRoles_pl.md)).
- Pacjent ma aktywną wizytę lub hospitalizację.

**Kroki:**

1. Lekarz albo pielęgniarka dodaje notatkę kliniczną odpowiedniej kategorii (np. notatka przyjęcia,
   przebiegu, konsultacji, pielęgniarska, wypisu).
2. Lekarz odnotowuje rozpoznaną diagnozę z kodem SNOMED CT.
3. Lekarz albo pielęgniarka zapisuje alergię pacjenta, jeśli zostanie wykryta.

**Wynik:**

- Notatka, diagnoza albo alergia są zapisane w dokumentacji pacjenta.
- Diagnozy i alergie są widoczne natychmiast dla całego personelu mającego dostęp do dokumentacji
  pacjenta, co pozwala np. automatycznie ostrzegać przy wystawianiu recepty.

## Parametry życiowe i alerty

**Cel:** zapis parametrów życiowych pacjenta i automatyczne wykrywanie wartości poza normą.

**Założenia:**

- Lekarz albo pielęgniarka ma uprawnienie do zapisu parametrów życiowych.
- Dla rejestrowanego typu parametru skonfigurowane są progi ostrzegawcze i krytyczne.

**Kroki:**

1. Lekarz albo pielęgniarka zapisuje pomiar (ciśnienie, tętno, saturacja, częstość oddechów, temperatura,
   skala bólu).
2. System porównuje zapisaną wartość z progami ostrzegawczymi i krytycznymi danego typu pomiaru.
3. Jeśli próg jest przekroczony, system generuje alert kliniczny widoczny dla personelu opiekującego się
   pacjentem.
4. Członek personelu potwierdza alert.

**Wynik:**

- Pomiar jest zapisany jako nowy, niemutowalny wpis - korekta błędnego pomiaru nie nadpisuje istniejącego
  wpisu, tylko dodaje nowy.
- Przekroczenie progu ostrzegawczego i krytycznego generuje odrębne alerty.
- Potwierdzenie alertu odnotowuje, kto i kiedy zareagował.

## Komunikacja zespołowa: wiadomości, zadania, przekazanie zmiany

**Cel:** wymiana informacji i koordynacja pracy personelu, w tym przekazanie opieki nad pacjentami między
zmianami.

**Założenia:**

- Każda rola ma uprawnienie do korzystania z wiadomości i zadań zespołowych.

**Kroki:**

1. Użytkownik tworzy wątek wiadomości, opcjonalnie powiązany z konkretnym pacjentem, i ustawia priorytet
   (normalny/wysoki/krytyczny).
2. Użytkownik tworzy zadanie zespołowe i przypisuje je do innego członka personelu.
3. Przy zmianie zmiany na oddziale pielęgniarka albo lekarz tworzy notatkę przekazania zmiany z listą
   pacjentów wymagających uwagi kolejnej zmiany.

**Wynik:**

- Wiadomość jest widoczna dla uczestników wątku; każdy uczestnik ma własny znacznik czasu ostatniego
  odczytu.
- Zadanie zespołowe ma status realizacji śledzony niezależnie od innych zadań.
- Notatka przekazania zmiany jest widoczna dla personelu kolejnej zmiany, wraz z komentarzem per pacjent.

## Zarządzanie kontem użytkownika

**Cel:** rejestracja nowego konta pracownika i jego zatwierdzenie przez administratora, oraz blokowanie i
odblokowywanie kont.

**Założenia:**

- Rejestracja konta jest dostępna dla każdego, kto poda wymagane dane.
- Aktywacja, blokada i odblokowanie konta są dostępne wyłącznie dla administratora.

**Kroki:**

1. Nowy pracownik rejestruje się samodzielnie, podając swoje dane i docelową rolę.
2. System tworzy konto w stanie oczekującym - konto nie pozwala na zalogowanie.
3. Administrator przegląda zgłoszenia i aktywuje konta uznane za uprawnione.
4. W razie potrzeby administrator blokuje aktywne konto (np. po odejściu pracownika) albo odblokowuje
   konto zablokowane wcześniej.

**Wynik:**

- Aktywowane konto pozwala na zalogowanie.
- Zablokowane konto nie pozwala na zalogowanie, niezależnie od poprawności hasła.
- Pełny opis ról i mechanizmu logowania: [userRoles_pl.md](userRoles_pl.md#administrator).
