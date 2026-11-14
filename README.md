# Secure Config & Audit Hub

Project contract: see `docs/CONTRACT.md` (copy the shared contract there).

## Decisions
- Spring Boot 3.5.16, Java 17
- Keycloak image `quay.io/keycloak/keycloak:26.8.0`
- MDC key for the correlation id: `correlationId`
- Ports: Hub 8080, Config 8082, Audit 8083, Keycloak 8180

## Modules
| Module | Owner |
|---|---|
| common-security, hub-service, infra/keycloak | A |
| config-service, audit-service, docker-compose.yml, postman/ | B |

## Run (local)
1. `docker compose up -d` (needs `infra/keycloak/hub-realm.json`, added by A)
2. Open the root `pom.xml` in IntelliJ, then `mvn clean install`
3. Start the three services from IntelliJ
