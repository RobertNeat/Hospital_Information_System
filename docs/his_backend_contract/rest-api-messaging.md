# REST: komunikacja, zadania, przekazania zmiany, alerty

`MessageThreadController`, `TeamTaskController`, `HandoffNoteController`, `AlertController` (prefiks `/api/v1`). Powrót: [README.md](README.md) | indeks: [rest-api.md](rest-api.md). Typy: [data-types.md](data-types.md#komunikacja-i-alerty). Push STOMP: [realtime-stomp.md](realtime-stomp.md).

## Wątki i wiadomości

| Metoda | Ścieżka | Uprawnienie | Parametry / body | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/message-threads` | `message:read` | query: `patientId`, `page`, `size`, `sort` | Pag. `MessageThreadResponse` (tylko wątki zalogowanego uczestnika) | 200; 422 |
| GET | `/message-threads/{threadId}` | `message:read` | - | `MessageThreadResponse` | 200; 403 nie-uczestnik; 404 |
| GET | `/message-threads/{threadId}/messages` | `message:read` | - | `MessageResponse[]` (rosnąco wg `sentAt`, potem `id`; bez paginacji) | 200; 403; 404 |
| POST | `/message-threads` | `message:write` | `ThreadCreateRequest` | `MessageThreadResponse` + `Location: /api/v1/message-threads/{id}` | 201; 404 uczestnik/pacjent; 422 |
| POST | `/message-threads/{threadId}/messages` | `message:write` | `MessageSendRequest` (`body`, `priority`) | `MessageResponse` | 201 (bez `Location`); 403; 404; 422 |
| POST | `/message-threads/{threadId}/read` | `message:write` | puste (ciało ignorowane) | `MessageThreadResponse` (`unreadCount=0`) | 200; 403; 404 |

Reguły:

- Dostęp do wątku mają wyłącznie jego uczestnicy (także `admin`); nie-uczestnik -> 403 "Brak dostepu do watku", nieistniejący wątek -> 404.
- Utworzenie: twórca (z tokenu) jest dołączany jako uczestnik niezależnie od `participantIds`, powtórzenia scalane; wszyscy uczestnicy muszą istnieć (404); `patientId` musi istnieć (404). Pierwsza wiadomość powstaje w tej samej transakcji (nadawca = twórca). Wątek nie ma endpointu edycji/usunięcia.
- `unreadCount` (`@viewerScoped`) = liczba wiadomości innych nadawców, nowszych niż kursor odczytu zalogowanego (`lastReadAt`; `null` = wszystkie nowe). Twórca ma kursor ustawiony na moment utworzenia (własna wiadomość jest "przeczytana").
- `readByIds` wiadomości: nadawca (zawsze, pierwszy) + uczestnicy, których kursor jest >= `sentAt`, w kolejności dołączenia.
- `participantIds` w kolejności dołączenia (`joinedAt`, potem id).
- `read`: idempotentne, kursor idzie tylko do przodu (nigdy wstecz); odpowiedź zawsze z `unreadCount=0`.
- `sort` listy: `lastMessageAt`, `subject`, `createdAt`; domyślnie `lastMessageAt` malejąco.
- Zdarzenia: `MessageSent` (każda wiadomość, także pierwsza), `ThreadMarkedRead` -> push STOMP.

## Zadania zespołu

| Metoda | Ścieżka | Uprawnienie | Parametry / body | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/tasks` | `task:read` | query: `assignedToId`, `createdById`, `status`, `patientId` | `TeamTaskResponse[]` (`createdAt` malejąco, bez paginacji) | 200; 422 |
| POST | `/tasks` | `task:write` | `TaskCreateRequest` | `TeamTaskResponse` | 201 (bez `Location`); 404 `assignedToId`/`patientId`; 422 |
| POST | `/tasks/{taskId}/status` | `task:write` | `TaskStatusUpdateRequest` (`status`, `version?`) | `TeamTaskResponse` | 200; 403; 404; 409; 422 |

- Nowe zadanie zawsze zaczyna jako `open`; `status` w żądaniu inny niż `open` -> 422. `createdById` w żądaniu ignorowany (twórca z tokenu).
- Zdarzenia: `TaskAssigned` (utworzenie), `TaskStatusChanged` (zmiana statusu).
- Status może zmienić **tylko osoba przypisana lub twórca** (inaczej 403). Rola `admin` ma tylko `task:read`, więc nie zmienia statusu.
- Wersja: niezgodne `version` -> 409.

Maszyna stanów zadania (`done`, `cancelled` końcowe; niedozwolone przejście -> 409):

| Z \ Na | `open` | `in_progress` | `done` | `cancelled` |
| --- | --- | --- | --- | --- |
| `open` | - | tak | **tak** | tak |
| `in_progress` | nie | - | tak | tak |
| `done` | nie | nie | - | nie |
| `cancelled` | nie | nie | nie | - |

## Przekazania zmiany

| Metoda | Ścieżka | Uprawnienie | Parametry / body | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/handoff-notes` | `task:read` | query: `wardId`, `shiftDate` (`YYYY-MM-DD`) | `HandoffNoteResponse[]` (`shiftDate` malejąco, `createdAt` malejąco; bez paginacji) | 200; 422 |
| POST | `/handoff-notes` | `task:write` | `HandoffNoteCreateRequest` | `HandoffNoteResponse` | 201 (bez `Location`); 422 |

- `fromId` z tokenu (ciało ignorowane). 422 zbiorczo z `errors[]`: nieistniejący `wardId` / `toId`, nieistniejący pacjent (`patientNotes[i].patientId`, `notFound`), powtórzony pacjent (`duplicate`).
- `patientNotes` (SBAR: `situation`, `background`, `assessment`, `recommendation`, wszystkie niepuste) zwracane rosnąco wg `patientId`. `patientNotes` może być pustą listą.
- Przekazania są niezmienne (brak edycji/usuwania).

## Alerty kliniczne

| Metoda | Ścieżka | Uprawnienie | Parametry | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/alerts` | `alert:read` | query: `patientId`, `acknowledged` (bool, względem zalogowanego) | `AlertResponse[]` (`createdAt` malejąco, bez paginacji) | 200; 422 |
| POST | `/alerts/{alertId}/acknowledge` | `alert:acknowledge` | puste | `AlertResponse` (`acknowledged=true`, `acknowledgedById`, `acknowledgedAt`) | 200; 404 |

- Alerty tworzy wyłącznie backend (reakcja na zdarzenia, [events.md](events.md)); brak `POST /alerts`.
- Alert nie ma adresata w modelu: **każdy** z `alert:read` widzi wszystkie alerty. Adresaci i oddział dotyczą tylko push STOMP.
- Potwierdzenie jest **per użytkownik** (`alert_acknowledgement`): `acknowledged`/`acknowledgedById`/`acknowledgedAt` to projekcja dla zalogowanego (`@viewerScoped`). Idempotentne: ponowne potwierdzenie nic nie zmienia. Publikuje `AlertAcknowledged` (push do sesji tego użytkownika).
- Filtr `acknowledged=true|false` dotyczy tylko potwierdzeń zalogowanego.
- Pole `link` nie jest zwracane; wskazanie encji jest w `target` (`kind`, `id`, `patientId?`).
- Alert `critical` niepotwierdzony przez użytkownika liczy się do `criticalAlerts` w `/dashboard/stats`.
