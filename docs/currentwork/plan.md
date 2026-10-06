# Planowane prace

- Napraw błąd `ERR_PNPM_META_FETCH_FAIL` / "Lockfile failed supply-chain policy check"
  w budowach Docker (`angular.Dockerfile`, `node.Dockerfile`) — pnpm 11 domyślnie
  włącza `minimumReleaseAge`, co wymaga pobrania metadanych publikacji dla każdego
  wpisu lockfile i zawodzi w środowisku kontenerowym CI. Właściwa kontrola polityki
  działa już w `check_node.sh` (`_job_check.yaml`), który jest zależnością (`needs`)
  joba budującego obrazy — wyłączenie tej kontroli tylko na etapie Docker install
  nie osłabia faktycznej bramki CI.
