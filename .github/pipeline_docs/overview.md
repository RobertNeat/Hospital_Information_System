# Założenia ogólne

Pipeline GitHub Actions jest niezależną warstwą automatyzacji. Aplikacje
pozostają właścicielem kodu, zależności, testów i komend jakości; pipeline
wywołuje tylko kontrakt opisany w `projects.json`.

Pipeline sprawdza, buduje i wdraża aplikacje znajdujące się w tym repozytorium,
nie zewnętrzne projekty. Działa dla wielu technologii (Node.js/Angular/NestJS,
Python i Spring Boot) i traktuje je jako jeden zestaw współpracujących
kontenerów. Ten sam commit SHA identyfikuje cały zestaw obrazów.

## Ścieżki

- `check` — walidacja konfiguracji, kontrola jakości, testy, budowanie aplikacji,
  SAST i skanowanie zależności/obrazu;
- `build` — budowa obrazu, skan Trivy i zapis obrazu w lokalnym rejestrze pod
  pełnym SHA;
- `deploy` — uruchomienie dokładnie tego zestawu obrazów na produkcji;
- `release` — publikacja obrazów wybranego SHA do GHCR;
- `github_release` — utworzenie wydania o nazwie taga.

Workflowy korzystają z self-hosted runnera. Obrazy są niezmienne, `latest` nie
jest używany, a operacje build/deploy/release są serializowane.

## Lokalne rejestry zależności

Runner i obrazy Docker korzystają z lokalnych rejestrów zamiast publicznych,
żeby budowy nie zależały od dostępności/limitów zewnętrznych usług:

- obrazy bazowe (`FROM`) oraz obrazy uruchamiane bezpośrednio w `deploy/compose.yml`
  (`postgres`, `snowstorm-lite`) — ZOT (`192.168.1.162:5000`), ten sam rejestr co
  cel push dla zbudowanych obrazów aplikacji. ZOT ma włączony `sync` z
  `on-demand pull-through` do `registry-1.docker.io` (z kontem Docker Hub po
  stronie serwera, dla wyższych limitów) — obraz jest pobierany i cache'owany
  przy pierwszym żądaniu, więc ścieżka w `FROM`/`image:` musi odzwierciedlać
  dokładną ścieżkę z Docker Hub: oficjalne obrazy (`node`, `nginx`, `python`,
  `maven`, `eclipse-temurin`, `postgres`) są pod `library/`, obrazy
  użytkowników/organizacji (`snomedinternational/snowstorm-lite`) bez tego
  prefiksu;
- zależności npm/pnpm — Verdaccio (`192.168.1.163`, przez nginx na porcie 80;
  aplikacja nasłuchuje tylko na `127.0.0.1:4873`), skonfigurowany w `.npmrc` w
  katalogu głównym repozytorium; corepack również kieruje się tam przez
  `COREPACK_NPM_REGISTRY`;
- zależności Maven — Reposilite (`192.168.1.164/releases`, przez nginx na
  porcie 80; aplikacja nasłuchuje tylko na `127.0.0.1:8080`), skonfigurowany
  jako mirror w `.mvn/settings.xml`.
