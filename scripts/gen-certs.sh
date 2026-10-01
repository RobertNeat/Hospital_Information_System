#!/usr/bin/env bash
# Generuje certyfikaty mTLS dla stosu HIS (OpenSSL + keytool). Uzycie: scripts/gen-certs.sh [katalog_wyjsciowy]
# (domyslnie .certs w katalogu glownym repo; poza repo - jest w .gitignore).
#
# Struktura wyniku:
#   <out>/ca.crt, ca.key, ca.srl        CA `HIS-CA` (tworzone tylko gdy brak; istniejacej CA nie nadpisuje)
#   <out>/<katalog>/<host>.crt|.key     certyfikat i klucz uslugi (SAN: nazwa uslugi compose, localhost, 127.0.0.1;
#                                       EKU serverAuth+clientAuth - ten sam certyfikat jako serwer i klient)
#   <out>/<katalog>/keystore.p12        klucz + certyfikat + CA (alias: `his` dla his_backend, `server` dla e-*)
#   <out>/<katalog>/truststore.p12      CA (alias `hisca`)
#   <out>/passwords.env                 losowe hasla: <KATALOG_UPPER>_KEYSTORE_PASSWORD / _TRUSTSTORE_PASSWORD
# Certyfikaty uslug sa za kazdym razem generowane od nowa (odnowienie), wraz z nowymi haslami.
set -euo pipefail

CA_DAYS="${CA_DAYS:-1825}"     # 5 lat
CERT_DAYS="${CERT_DAYS:-365}"  # 1 rok

# katalog:host:alias w keystore
APPS=(
  "his_backend:his-backend:his"
  "e_receipt:e-receipt:server"
  "e_laboratory:e-laboratory:server"
  "e_imaging:e-imaging:server"
)

for tool in openssl keytool; do
  command -v "$tool" > /dev/null || { echo "Brak narzedzia: $tool" >&2; exit 1; }
done

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
out="${1:-$script_dir/../.certs}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Narzedzia pisza postep na stderr; pokazujemy go tylko przy bledzie. Nazwy podmiotow ida przez pliki
# konfiguracyjne (a nie `-subj "/CN=..."`), bo Git Bash przepisuje argumenty zaczynajace sie od "/" na sciezki Windows.
quiet() { "$@" 2> "$work/err.log" || { cat "$work/err.log" >&2; return 1; }; }

rand_password() { openssl rand -hex 16; }

# --- CA (tylko jesli brak) ---
if [ -f "$out/ca.crt" ] && [ -f "$out/ca.key" ]; then
  echo "CA istnieje - zostaje bez zmian: $out/ca.crt"
else
  cat > "$work/ca.cnf" <<EOF
[req]
distinguished_name=dn
prompt=no
x509_extensions=v3
[dn]
CN=HIS-CA
[v3]
basicConstraints=critical,CA:TRUE
keyUsage=critical,keyCertSign,cRLSign
EOF
  quiet openssl req -x509 -newkey rsa:4096 -nodes -config "$work/ca.cnf" -keyout "$out/ca.key" -out "$out/ca.crt" \
    -days "$CA_DAYS" -sha256
  chmod 600 "$out/ca.key"
  echo "Utworzono CA: $out/ca.crt"
fi

passwords="$work/passwords.env"
: > "$passwords"

for entry in "${APPS[@]}"; do
  IFS=: read -r dir host alias <<< "$entry"
  upper="$(echo "$dir" | tr '[:lower:]' '[:upper:]')"
  target="$out/$dir"
  mkdir -p "$target"

  keystore_pw="$(rand_password)"
  truststore_pw="$(rand_password)"

  cat > "$work/csr.cnf" <<EOF
[req]
distinguished_name=dn
prompt=no
[dn]
CN=$host
EOF
  cat > "$work/ext.cnf" <<EOF
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth,clientAuth
subjectAltName=DNS:$host,DNS:localhost,IP:127.0.0.1
EOF

  quiet openssl genrsa -out "$target/$host.key" 2048
  quiet openssl req -new -config "$work/csr.cnf" -key "$target/$host.key" -out "$work/$host.csr"
  # -CAcreateserial zapisuje ca.srl obok ca.crt
  quiet openssl x509 -req -in "$work/$host.csr" -CA "$out/ca.crt" -CAkey "$out/ca.key" -CAcreateserial \
    -days "$CERT_DAYS" -sha256 -extfile "$work/ext.cnf" -out "$target/$host.crt"
  chmod 600 "$target/$host.key"

  # Hasla przekazywane przez zmienne srodowiskowe (nie trafiaja na liste procesow).
  rm -f "$target/keystore.p12" "$target/truststore.p12"
  KS_PW="$keystore_pw" quiet openssl pkcs12 -export -name "$alias" -inkey "$target/$host.key" \
    -in "$target/$host.crt" -certfile "$out/ca.crt" -out "$target/keystore.p12" -passout env:KS_PW
  TS_PW="$truststore_pw" quiet keytool -importcert -noprompt -alias hisca -file "$out/ca.crt" \
    -keystore "$target/truststore.p12" -storetype PKCS12 -storepass:env TS_PW
  # Magazyny sa chronione haslem; kontener (uid 10001) musi je odczytac po zamontowaniu read-only.
  chmod 644 "$target/keystore.p12" "$target/truststore.p12" "$target/$host.crt"

  {
    echo "${upper}_KEYSTORE_PASSWORD=$keystore_pw"
    echo "${upper}_TRUSTSTORE_PASSWORD=$truststore_pw"
  } >> "$passwords"
  echo "Wygenerowano: $target ($host, alias $alias, wazny $CERT_DAYS dni)"
done

install -m 600 "$passwords" "$out/passwords.env"
echo "Hasla zapisano w $out/passwords.env (poza repo; wpisz je do deploy/local.env i config.env na serwerze)."
