# Secure Config & Audit Hub

Three Java microservices secured with Zero Trust (authentication, authorization and inter-service trust): OAuth2/OIDC + JWT issued by Keycloak, and RBAC. Each service validates the JWT itself.

The project contract is in `docs/CONTRACT.md`.

## Architecture

- Keycloak (:8180), realm `hub-realm`, issues the JWTs (users and service account).
- Hub Service (:8080), the only client-facing service. It validates the JWT, checks the role, calls Config with the user JWT relayed, and writes audit events with its own service token.
- Config Service (:8082), internal. CRUD on configuration entries (H2 in memory).
- Audit Service (:8083), internal. Append-only audit events (H2 in memory); only the Hub service account may write.

Modules: `common-security` (shared JWT/role/error/correlation code), `hub-service`, `config-service`, `audit-service`. Infrastructure: `docker-compose.yml` and `infra/keycloak/hub-realm.json`.

## Decisions

- Spring Boot 3.5.16, Java 17, Maven multi-module
- Keycloak image `quay.io/keycloak/keycloak:26.8.0`
- Package `ma.ensias.sechub.<module>`; MDC key for the correlation id: `correlationId`
- Ports: Hub 8080, Config 8082, Audit 8083, Keycloak 8180

## Prerequisites

- Java 17, Maven 3.9+ (or the IntelliJ bundled Maven), Docker with Docker Compose

## Install and run (local)

1. Start Keycloak (imports the realm on startup, wait about 30 seconds):

       docker compose up -d

2. Build and test everything (the tests do not need Keycloak):

       mvn clean install

3. Start the three services, each in its own terminal (or from IntelliJ):

       mvn -pl config-service spring-boot:run
       mvn -pl audit-service spring-boot:run
       mvn -pl hub-service spring-boot:run

4. Check that they are up:

       http://localhost:8080/actuator/health
       http://localhost:8082/actuator/health
       http://localhost:8083/actuator/health

All settings have local defaults (see `KEYCLOAK_ISSUER`, `KEYCLOAK_TOKEN_URI`, `HUB_CLIENT_SECRET`, `CONFIG_SERVICE_URL`, `AUDIT_SERVICE_URL` in the contract), so nothing needs to be set. To stop Keycloak: `docker compose down`.

## Test users (development only)

| User | Password | Role |
|---|---|---|
| alice | alice123 | admin |
| bob | bob123 | developer |
| carol | carol123 | auditor |

Clients: `postman-client` (public, password grant for tests only) and `hub-service` (confidential, secret `dev-secret-change-me`, service account with role `svc-audit-writer`).

Get a user token (valid about 5 minutes):

    POST http://localhost:8180/realms/hub-realm/protocol/openid-connect/token
    grant_type=password, client_id=postman-client, username, password

## Demo scenarios

Import `postman/sechub.postman_collection.json` and `postman/sechub.postman_environment.json` in Postman, select the environment, then run the collection (or call the requests one by one). Screenshots are in `docs/screenshots/`.

| # | Scenario | Expected |
|---|---|---|
| 1 | alice creates a config | 201, audit ALLOWED |
| 2 | bob updates it | 200, version 2 |
| 3 | bob deletes it | 403, audit DENIED |
| 4 | carol lists configs, then creates one | 200, then 403 |
| 5 | carol reads `/api/audit`, bob reads `/api/audit` | 200 (sees all events), then 403 |
| 6 | no token | 401 |
| 7 | direct POST to Audit with alice's token, then with the Hub service token | 403, then 201 (Zero Trust) |
| 8 | direct call to Config with no token | 401 (bypassing the Hub is impossible) |
| 9 | tampered token, expired token | 401 |

The expired-token request is in the manual folder of the Postman collection: wait 5 minutes after fetching a token.

## Tests

`mvn clean install` runs all tests without Keycloak (spring-security-test with a mocked `JwtDecoder`).

## Limits and improvements

- mTLS between services (stretch goal, not implemented)
- Config and Audit would not be publicly exposed in production
- Password grant is for tests only
- Development secrets are in files; production would use a secret manager
- Audit write is fail-open (a failed audit write does not fail the request; a warning is logged) and synchronous without retry
- Malformed request bodies are rejected before the role check and are not audited