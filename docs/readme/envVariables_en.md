# Environment variables

Names, meaning and accepted values of the environment variables used in the project.

## Database (his_backend)

| Variable                 | Meaning                                               | Default                                       | Required       |
| ------------------------ | ----------------------------------------------------- | --------------------------------------------- | -------------- |
| `HIS_DB_URL`             | database connection URL                               | none (dev: local database on port 5432)       | yes            |
| `HIS_DB_USER`            | database user                                         | none (dev: `his`)                             | yes            |
| `HIS_DB_PASSWORD`        | database password                                     | none (dev: `his`)                             | yes            |
| `HIS_DB_NAME`            | database name for the container (Docker Compose only) | `his`                                         | yes, in Docker |
| `HIS_DB_HOST_PORT`       | host port for the database (development setup only)   | `5432`                                        | no             |
| `HIS_LIQUIBASE_CONTEXTS` | which set of initial data to load                     | baseline (dev: baseline + extended test data) | no             |

## Authentication (his_backend)

| Variable                   | Meaning                                            | Default                                  | Required |
| -------------------------- | -------------------------------------------------- | ---------------------------------------- | -------- |
| `HIS_JWT_SECRET`           | signing key for access tokens                      | **empty = startup error** (dev: dev key) | yes      |
| `HIS_JWT_TTL`              | access token lifetime                              | 15 minutes                               | no       |
| `HIS_JWT_ISSUER`           | token issuer name                                  | main system's name                       | no       |
| `HIS_LOCKOUT_MAX_ATTEMPTS` | failed login attempts before an account is locked  | 5                                        | no       |
| `HIS_LOCKOUT_DURATION`     | lockout duration once the attempt limit is reached | 15 minutes                               | no       |
| `HIS_CORS_ALLOWED_ORIGINS` | allowed origins for cross-origin requests (CORS)   | none (CORS disabled)                     | no       |
| `HIS_WS_ALLOWED_ORIGINS`   | allowed origins for the WebSocket handshake        | none (same origin only)                  | no       |

## SNOMED CT terminology / Snowstorm Lite (his_backend)

| Variable                         | Meaning                                 | Default                            | Required                     |
| -------------------------------- | --------------------------------------- | ---------------------------------- | ---------------------------- |
| `HIS_SNOWSTORM_ENABLED`          | enables the terminology integration     | disabled                           | no                           |
| `HIS_SNOWSTORM_URL`              | Snowstorm Lite address                  | local / internal container network | no                           |
| `HIS_SNOWSTORM_CONNECT_TIMEOUT`  | connection timeout                      | 2 seconds                          | no                           |
| `HIS_SNOWSTORM_READ_TIMEOUT`     | response read timeout                   | 5 seconds                          | no                           |
| `HIS_SNOWSTORM_DISPLAY_LANGUAGE` | language of displayed term names        | Polish, English                    | no                           |
| `HIS_SNOWSTORM_MAX_PAGE_SIZE`    | maximum number of search results        | 100                                | no                           |
| `HIS_SNOWSTORM_ADMIN_USER`       | Snowstorm Lite administrator login      | `admin`                            | no                           |
| `HIS_SNOWSTORM_ADMIN_PASSWORD`   | Snowstorm Lite administrator password   | `admin`                            | no (change it in production) |
| `HIS_SNOWSTORM_HOST_PORT`        | host port for Snowstorm Lite (dev only) | 8080                               | no                           |

- Without imported terminology data, an enabled integration returns an error for every query.

## mTLS and FHIR integration (his_backend, e-receipt, e-laboratory, e-imaging)

| Variable                                      | Meaning                                                                          | Default                                       | Required      |
| --------------------------------------------- | -------------------------------------------------------------------------------- | --------------------------------------------- | ------------- |
| `HIS_MTLS_ENABLED`                            | enables mTLS; disabling it means no-certificate mode (tests, IDE)                | enabled                                       | no            |
| `HIS_FHIR_PORT`                               | the main system's FHIR (mTLS) port                                               | 10424                                         | no            |
| `HIS_MANAGEMENT_PORT`                         | the main system's health-check port                                              | 10440                                         | no            |
| `HIS_FHIR_ALLOWED_CLIENT_CNS`                 | allowed client certificate names on the FHIR port                                | the e-receipt/e-laboratory/e-imaging services | no            |
| `HIS_CERTS_DIR`                               | certificate directory mounted into the containers (one subdirectory per service) | the repository's `.certs` directory           | no            |
| `MTLS_KEYSTORE`, `MTLS_KEYSTORE_PASSWORD`     | the service's key/certificate store and its password                             | local path; password required                 | password: yes |
| `MTLS_TRUSTSTORE`, `MTLS_TRUSTSTORE_PASSWORD` | the certificate authority store and its password                                 | local path; password required                 | password: yes |
| `MTLS_KEY_ALIAS`                              | key alias inside the store                                                       | depends on the service                        | no            |

The web interface and health-check ports of the e-* services are configurable per service (see
[localDeploy](localDeploy_en.md) for default values).

### Switches for the main system's integrations with the e-* services

| Variable                                                     | Meaning                                    | Default                            |
| ------------------------------------------------------------ | ------------------------------------------ | ---------------------------------- |
| `HIS_ERECEIPT_ENABLED`                                       | enables sending prescriptions to e-receipt | disabled                           |
| `HIS_ERECEIPT_URL`                                           | e-receipt's FHIR address                   | local / internal container network |
| `HIS_ERECEIPT_CONNECT_TIMEOUT` / `HIS_ERECEIPT_READ_TIMEOUT` | connection / read timeouts                 | 1 / 3 seconds                      |
| `HIS_ERECEIPT_SSL_BUNDLE`                                    | name of the mTLS certificate bundle        | the mTLS bundle                    |
| `HIS_ELAB_ENABLED`                                           | enables sending orders to e-laboratory     | disabled                           |
| `HIS_ELAB_URL`                                               | e-laboratory's FHIR address                | local / internal container network |
| `HIS_ELAB_CONNECT_TIMEOUT` / `HIS_ELAB_READ_TIMEOUT`         | connection / read timeouts                 | 1 / 3 seconds                      |
| `HIS_ELAB_SSL_BUNDLE`                                        | name of the mTLS certificate bundle        | the mTLS bundle                    |
| `HIS_EIMG_ENABLED`                                           | enables sending orders to e-imaging        | disabled                           |
| `HIS_EIMG_URL`                                               | e-imaging's FHIR address                   | local / internal container network |
| `HIS_EIMG_CONNECT_TIMEOUT` / `HIS_EIMG_READ_TIMEOUT`         | connection / read timeouts                 | 1 / 3 seconds                      |
| `HIS_EIMG_SSL_BUNDLE`                                        | name of the mTLS certificate bundle        | the mTLS bundle                    |

### Switches for the e-* services' callback integrations with the main system

| Variable                                                             | Meaning                                            | Default                            |
| -------------------------------------------------------------------- | -------------------------------------------------- | ---------------------------------- |
| `ERECEIPT_HIS_ENABLED`                                               | enables e-receipt's callback to the main system    | disabled                           |
| `ERECEIPT_HIS_URL`                                                   | the main system's FHIR address                     | local / internal container network |
| `ERECEIPT_HIS_CONNECT_TIMEOUT` / `ERECEIPT_HIS_READ_TIMEOUT`         | connection / read timeouts                         | 1 / 3 seconds                      |
| `ERECEIPT_HIS_SSL_BUNDLE`                                            | name of the mTLS certificate bundle                | the mTLS bundle                    |
| `ERECEIPT_UI_PORT`                                                   | e-receipt's web interface port                     | 10431                              |
| `ERECEIPT_MANAGEMENT_PORT`                                           | e-receipt's health-check port                      | 10441                              |
| `E_LABORATORY_HIS_ENABLED`                                           | enables e-laboratory's callback to the main system | disabled                           |
| `E_LABORATORY_HIS_URL`                                               | the main system's FHIR address                     | local / internal container network |
| `E_LABORATORY_HIS_CONNECT_TIMEOUT` / `E_LABORATORY_HIS_READ_TIMEOUT` | connection / read timeouts                         | 1 / 3 seconds                      |
| `E_LABORATORY_HIS_SSL_BUNDLE`                                        | name of the mTLS certificate bundle                | the mTLS bundle                    |
| `E_LABORATORY_UI_PORT`                                               | e-laboratory's web interface port                  | 10432                              |
| `E_LABORATORY_MANAGEMENT_PORT`                                       | e-laboratory's health-check port                   | 10442                              |
| `E_IMAGING_HIS_ENABLED`                                              | enables e-imaging's callback to the main system    | disabled                           |
| `E_IMAGING_HIS_URL`                                                  | the main system's FHIR address                     | local / internal container network |
| `E_IMAGING_HIS_CONNECT_TIMEOUT` / `E_IMAGING_HIS_READ_TIMEOUT`       | connection / read timeouts                         | 1 / 3 seconds                      |
| `E_IMAGING_HIS_SSL_BUNDLE`                                           | name of the mTLS certificate bundle                | the mTLS bundle                    |
| `E_IMAGING_UI_PORT`                                                  | e-imaging's web interface port                     | 10433                              |
| `E_IMAGING_MANAGEMENT_PORT`                                          | e-imaging's health-check port                      | 10443                              |

- The e-receipt UI and health-check port variable names differ, inside the container, from the names used
  in configuration files - this is an intentional historical inconsistency, not a bug.

## Frontend (his_frontend)

The frontend does not read any environment variables at runtime - it is a static application, and the
address of the main system is fixed at build time. The only variable concerns the build process itself:

| Variable              | Meaning                                                                                | Default |
| --------------------- | -------------------------------------------------------------------------------------- | ------- |
| `HIS_PERSIST_SESSION` | set at build time; disabling it means the login session ends when the page is reloaded | enabled |

## Variables for Docker Compose only (not read by the applications)

Variables controlling image names, container ports and Docker metadata - used only by the build and
deployment process, not by application code.

## Notes

- Environment files (the local development file, the production configuration files) are used to
  interpolate values into the Docker Compose files - only the variable list explicitly defined in the
  Compose file is passed into the containers.
- Example values are provided in configuration templates (production and development) - values that need
  to be changed on the server are marked in them.
- On a production server, the following must be changed: the database password, the token signing key,
  the Snowstorm Lite administrator password, and all mTLS certificate passwords. An existing configuration
  file on the server is not overwritten during deployment - new variables must be added manually.
