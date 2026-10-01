# Czas rzeczywisty (WebSocket / STOMP)

Endpoint `/ws`, uwierzytelnianie w ramce CONNECT, reguły SUBSCRIBE/SEND, tematy i payloady push. Powrót: [README.md](README.md). Źródło zdarzeń: [events.md](events.md).

Kod: `realtime/WebSocketConfig`, `StompSecurityInterceptor`, `StompUserAuthentication`, `StompDestinations`, `RealtimePublisher`, `PresenceEventListener`, `WebSocketProperties`, `staff/PresenceRegistry`.

## Połączenie

| Element | Wartość |
| --- | --- |
| Endpoint | `/ws` (czysty WebSocket, **bez SockJS**) |
| Handshake HTTP | publiczny (`permitAll` dla `/ws/**`, nagłówek `Authorization` ignorowany); uwierzytelnienie dopiero w STOMP CONNECT |
| Dozwolone originy | `HIS_WS_ALLOWED_ORIGINS` (lista po przecinku); **puste = tylko same-origin** (frontend i backend za jednym nginx) |
| Prefiks aplikacji | `/app` (bez handlerów - nic nie można tam wysłać) |
| Broker | prosty broker w pamięci: `/topic`, `/queue`; adresowanie użytkownika `/user` |
| Proxy | nginx `location /ws` (Upgrade, `proxy_read_timeout 1h`); dev: `proxy.conf.json` (`ws: true`) |
| Heartbeat | domyślne ustawienia brokera (nie konfigurowane w kodzie) |

## Uwierzytelnianie (CONNECT)

- Wymagany natywny nagłówek STOMP `Authorization: Bearer <JWT>` w ramce `CONNECT` (lub `STOMP`). Token jest weryfikowany tym samym dekoderem (HS256, issuer) i konwerterem claimów co REST.
- Brak nagłówka, zły format, błędny lub wygasły token -> ramka `ERROR`, połączenie odrzucone. Nie ma uwierzytelnienia cookie ani sesji.
- Ważność tokenu jest sprawdzana **tylko przy CONNECT** - otwarte połączenie działa po wygaśnięciu tokenu, aż do rozłączenia; blokada konta/zmiana roli nie rozłącza.
- Nazwa użytkownika sesji (`Principal.getName()`) = `staffId` (nie `employeeId`); po niej broker kieruje `/user/queue/**`.

## Reguły poleceń

| Polecenie | Reguła |
| --- | --- |
| CONNECT / STOMP | wymaga poprawnego JWT |
| SUBSCRIBE | patrz tabela tematów; wymaga uwierzytelnionej sesji; inny temat -> odmowa |
| UNSUBSCRIBE | wymaga uwierzytelnionej sesji |
| DISCONNECT | zawsze dozwolone |
| SEND (także `/app/**`) i inne polecenia | **zawsze zabronione** (kontrakt nie przewiduje komunikatów klient -> serwer; chroni `/topic/**` przed publikacją przez klienta) |
| ramki bez polecenia (heartbeat) | przechodzą |

Wysyłanie wiadomości/akcji odbywa się wyłącznie przez REST; STOMP służy do powiadomień serwer -> klient.

## Tematy

Payloady to te same DTO co w REST ([data-types.md](data-types.md)), bez koperty; typ wynika z tematu. Push jest wysyłany **po commit** transakcji źródłowej (`AFTER_COMMIT`); rollback nic nie wysyła. Błąd pushu jest logowany i nie wpływa na zatwierdzony zapis.

| Temat | Subskrypcja | Zdarzenie źródłowe | Payload | Adresaci |
| --- | --- | --- | --- | --- |
| `/user/queue/messages` | każdy uwierzytelniony | `MessageSent` | `MessageResponse` | uczestnicy wątku **poza nadawcą** |
| `/user/queue/threads` | każdy uwierzytelniony | `MessageSent` | `MessageThreadResponse` z `unreadCount` liczonym dla adresata | **wszyscy** uczestnicy wątku (z nadawcą) |
| `/user/queue/threads` | j.w. | `ThreadMarkedRead` | `MessageThreadResponse` z `unreadCount=0` | tylko sesje użytkownika, który oznaczył wątek (synchronizacja) |
| `/user/queue/alerts` | każdy uwierzytelniony | `AlertCreated` | `AlertResponse` z `acknowledged=false`, bez `acknowledgedBy*` | `recipientIds` zdarzenia (zlecający, lekarz prowadzący z aktywnego przyjęcia, osoba przypisana do zadania) |
| `/user/queue/alerts` | j.w. | `AlertAcknowledged` | `AlertResponse` z `acknowledged=true` (stan potwierdzającego) | tylko sesje potwierdzającego |
| `/topic/alerts/{wardId}` | uprawnienie `alert:read` **i** własny oddział (`wardId` z tokenu) lub rola `admin` (każdy oddział) | `AlertCreated` (gdy znany oddział) | `AlertResponse` z `acknowledged=false` | subskrybenci oddziału |
| `/user/queue/tasks` | każdy uwierzytelniony | `TaskAssigned`, `TaskStatusChanged` | `TeamTaskResponse` | tylko `assignedToId` (twórca zadania nie dostaje pushu przy zmianie statusu) |

Uwagi:

- `/user/queue/*`: klient subskrybuje pełne ścieżki `/user/queue/messages|threads|alerts|tasks` (inne `/user/...` są odrzucane).
- `{wardId}` musi być poprawnym UUID; niepoprawny lub cudzy oddział -> odmowa subskrypcji. Oddział alertu to oddział aktywnego przyjęcia pacjenta w chwili zdarzenia (alert bez pacjenta/bez aktywnego przyjęcia nie trafia na topic oddziału).
- Alert dla pacjenta bez aktywnego przyjęcia nie ma lekarza prowadzącego jako adresata.
- Pola `@viewerScoped` w broadcastach (`/topic/alerts/...`) nie zawierają stanu potwierdzeń innych użytkowników; klient nakłada własny stan z `GET /alerts` lub z `/user/queue/alerts`.
- Nie ma tematu presence ani powiadomień o zmianie statusu zleceń/wyników innych niż przez alerty.

## Rejestr obecności

`PresenceRegistry` (pamięć jednej instancji, bez persystencji): pracownik jest **online**, gdy ma co najmniej jedną otwartą sesję STOMP po udanym CONNECT, do DISCONNECT/zamknięcia (`SessionConnectedEvent`/`SessionDisconnectEvent`). Wartość jest projekcją w `StaffMemberResponse.online` (`GET /staff`, `/staff/{id}`, `/auth/me`, `LoginResponse.user`). Zmiana obecności nie jest rozgłaszana.

## Ograniczenia

- Broker w pamięci: przy wielu instancjach backendu push i rejestr obecności nie są współdzielone (wymagałoby brokera zewnętrznego / wspólnego magazynu).
- Po restarcie backendu klienci muszą połączyć się ponownie (broker nie persystuje); utracone w tym czasie powiadomienia trzeba odtworzyć przez REST (`GET /alerts`, `/message-threads`, `/tasks`).
- Brak potwierdzeń dostarczenia i brak kolejek trwałych.
