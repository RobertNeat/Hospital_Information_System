# Running the system locally

Ports, first-time setup (certificates and terminology data), and the commands to run the application -
either as plain processes or in Docker.

## Ports - user interfaces

| Service                   | Port  | Content                                   |
| ------------------------- | ----- | ----------------------------------------- |
| his_frontend              | 10400 | the application used by hospital staff    |
| his_backend               | 10420 | API, WebSocket, API documentation         |
| e-receipt                 | 10431 | prescription status interface (no login)  |
| e-laboratory              | 10432 | lab order status interface (no login)     |
| e-imaging                 | 10433 | imaging order status interface (no login) |
| Snowstorm Lite (optional) | 8080  | terminology browser and data import       |
| PostgreSQL                | 5432  | the system's database                     |

## Ports - communication between services

| From         | To             | Protocol     | Content                            |
| ------------ | -------------- | ------------ | ---------------------------------- |
| e-receipt    | his_backend    | HTTPS + mTLS | prescription status update         |
| e-laboratory | his_backend    | HTTPS + mTLS | order status update, test result   |
| e-imaging    | his_backend    | HTTPS + mTLS | order status update, test result   |
| his_backend  | e-receipt      | HTTPS + mTLS | sending a prescription             |
| his_backend  | e-laboratory   | HTTPS + mTLS | sending an order                   |
| his_backend  | e-imaging      | HTTPS + mTLS | sending an order                   |
| his_frontend | his_backend    | HTTP         | API, WebSocket (through the proxy) |
| his_backend  | PostgreSQL     | TCP          | database connection                |
| his_backend  | Snowstorm Lite | HTTP         | terminology search and translation |

The user-interface ports above are only reachable locally, in a development setup. In production, only
his_frontend and his_backend are published to the host - all other services are reachable only on the
internal container network. Ports can be changed via configuration variables, see
[envVariables](envVariables_en.md).

## First-time setup (one-off)

Steps to perform once, before the first run of the full stack - regardless of whether the applications are
later run as plain processes or in Docker.

1. **mTLS certificates.** The FHIR integration between the main system and the e-receipt/e-laboratory/
   e-imaging services requires client and server certificates signed by a shared certificate authority. A
   script generates the full set of files for every service and does not overwrite an existing
   certificate authority if one is already present.
2. **Password file for running in Docker.** Running in Docker requires an environment file containing the
   passwords for the certificate stores generated in the previous step. The file is not part of the
   repository - it must be created from a template and filled in with the generated passwords.
3. **SNOMED CT terminology import (optional).** The terminology integration requires a valid SNOMED CT
   license and a one-time data import into the Snowstorm Lite service. Without this step the terminology
   integration stays disabled while the rest of the system works normally. See the "Importing terminology
   data" section below for details.

## Running as plain processes (without Docker)

Requires a running database (either a local PostgreSQL instance or just the database container from the
Docker stack). In this mode mTLS certificates are not needed, but the integration with
e-receipt/e-laboratory/e-imaging is then disabled.

Startup order: install dependencies, start the database, start the backend, then the frontend. Default
demo login in this mode: `admin` / `admin`.

Running e-receipt/e-laboratory/e-imaging as plain processes requires the mTLS certificates generated
earlier (see "First-time setup") and pointing the configuration at their location. Without mTLS, these
services expose their FHIR interface without authentication - for testing only.

## Running in Docker (Docker Compose)

The full stack (database, main system, frontend, the three simulating services, and optionally Snowstorm
Lite) starts with a single Docker Compose command, combined with a development configuration overlay and
the environment file prepared in "First-time setup".

mTLS certificates are mounted into the containers read-only. The mTLS integration is enabled by default
and can be disabled in configuration (for example, for testing without certificates).

When done, the stack should be stopped to free the ports it uses.

### Full stack with Snowstorm Lite

Snowstorm Lite is an optional service, started with an additional Docker Compose profile. Terminology data
is persistent (stored in a volume) - the import only needs to be done once, not repeated on every startup.

## Importing SNOMED CT terminology data

Requires a valid SNOMED CT license (affiliation through the national terminology distribution center). The
RF2 data package is not part of the repository.

1. Start the Snowstorm Lite service (the terminology Docker Compose profile).
2. Import the RF2 package through the Snowstorm Lite administration interface - importing one edition
   takes about 5 minutes, and a later import replaces the previous one.
3. Verify the import succeeded in the Snowstorm Lite terminology browser.
4. Enable the terminology integration in the main system's configuration and restart the stack - without
   this step, terminology queries return an error.
5. Once done working with terminology, the Snowstorm Lite profile can be stopped independently of the
   rest of the stack.
