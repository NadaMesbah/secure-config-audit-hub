package ma.ensias.sechub.hub.service;

import ma.ensias.sechub.hub.dto.AuditEventDto;
import ma.ensias.sechub.hub.dto.AuditEventDto.Action;
import ma.ensias.sechub.hub.dto.AuditEventDto.ActorType;
import ma.ensias.sechub.hub.dto.AuditEventDto.Outcome;
import ma.ensias.sechub.hub.dto.ConfigRequest;
import ma.ensias.sechub.hub.dto.ConfigResponse;
import ma.ensias.sechub.hub.dto.PageResponse;
import ma.ensias.sechub.security.CorrelationIdFilter;
import ma.ensias.sechub.security.ErrorResponse;
import ma.ensias.sechub.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Hub flow (contract section 10): check the caller's roles, call Config or Audit with the
 * relayed JWT, audit ALLOWED / DENIED / FAILED, and map downstream errors
 * (400, 403, 404, 409 passed through, anything else -> 502).
 * The audit write is fail-open (see AuditClient).
 */
@Service
public class ConfigOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ConfigOrchestrator.class);

    private static final String SOURCE_SERVICE = "hub-service";
    private static final String BAD_GATEWAY_MESSAGE =
            "Downstream service unreachable or returned an unexpected response";

    private static final Set<String> READ_ROLES =
            authorities(Roles.ADMIN, Roles.DEVELOPER, Roles.AUDITOR);
    private static final Set<String> WRITE_ROLES = authorities(Roles.ADMIN, Roles.DEVELOPER);
    private static final Set<String> DELETE_ROLES = authorities(Roles.ADMIN);
    private static final Set<String> AUDIT_READ_ROLES = authorities(Roles.ADMIN, Roles.AUDITOR);

    private final ConfigClient configClient;
    private final AuditClient auditClient;

    public ConfigOrchestrator(ConfigClient configClient, AuditClient auditClient) {
        this.configClient = configClient;
        this.auditClient = auditClient;
    }

    /** Error raised by the orchestrator; GlobalExceptionHandler turns it into the JSON error format. */
    public static class HubException extends RuntimeException {

        private final HttpStatus status;
        private final Map<String, String> fieldErrors;

        public HubException(HttpStatus status, String message, Map<String, String> fieldErrors) {
            super(message);
            this.status = status;
            this.fieldErrors = fieldErrors;
        }

        public HttpStatus getStatus() {
            return status;
        }

        public Map<String, String> getFieldErrors() {
            return fieldErrors;
        }
    }

    public List<ConfigResponse> list(Authentication auth, String application, String environment) {
        return execute(auth, Action.LIST_CONFIGS, "config", READ_ROLES,
                () -> configClient.list(application, environment));
    }

    public ConfigResponse get(Authentication auth, Long id) {
        return execute(auth, Action.READ_CONFIG, "config/" + id, READ_ROLES,
                () -> configClient.get(id));
    }

    public ConfigResponse create(Authentication auth, ConfigRequest request) {
        return execute(auth, Action.CREATE_CONFIG, "config", WRITE_ROLES,
                () -> configClient.create(request));
    }

    public ConfigResponse update(Authentication auth, Long id, ConfigRequest request) {
        return execute(auth, Action.UPDATE_CONFIG, "config/" + id, WRITE_ROLES,
                () -> configClient.update(id, request));
    }

    public void delete(Authentication auth, Long id) {
        execute(auth, Action.DELETE_CONFIG, "config/" + id, DELETE_ROLES, () -> {
            configClient.delete(id);
            return null;
        });
    }

    public PageResponse<AuditEventDto> readAudit(Authentication auth, String actor, String action,
                                                 String outcome, String from, String to,
                                                 Integer page, Integer size) {
        return execute(auth, Action.READ_AUDIT, "audit", AUDIT_READ_ROLES,
                () -> auditClient.read(actor, action, outcome, from, to, page, size));
    }

    private <T> T execute(Authentication auth, Action action, String resource,
                          Set<String> allowedAuthorities, Supplier<T> call) {
        if (!isAllowed(auth, allowedAuthorities)) {
            audit(auth, action, resource, Outcome.DENIED, "role not allowed");
            throw new HubException(HttpStatus.FORBIDDEN, "Access denied for this role", null);
        }

        T result;
        try {
            result = call.get();
        } catch (HttpStatusCodeException e) {
            throw mapDownstreamError(auth, action, resource, e);
        } catch (RestClientException e) {
            log.warn("Downstream unreachable: action={} resource={} cause={}",
                    action, resource, e.getClass().getSimpleName());
            audit(auth, action, resource, Outcome.FAILED, "downstream unreachable");
            throw new HubException(HttpStatus.BAD_GATEWAY, BAD_GATEWAY_MESSAGE, null);
        }

        audit(auth, action, resource, Outcome.ALLOWED, null);
        return result;
    }

    private HubException mapDownstreamError(Authentication auth, Action action, String resource,
                                            HttpStatusCodeException e) {
        int status = e.getStatusCode().value();
        ErrorResponse body = parseDownstreamError(e);

        if (status == 403) {
            audit(auth, action, resource, Outcome.DENIED, "denied by downstream service");
            return new HubException(HttpStatus.FORBIDDEN,
                    messageOr(body, "Access denied for this role"), null);
        }

        if (status == 400 || status == 404 || status == 409) {
            audit(auth, action, resource, Outcome.FAILED, "downstream status " + status);
            Map<String, String> fieldErrors = (status == 400 && body != null) ? body.fieldErrors() : null;
            return new HubException(HttpStatus.valueOf(status),
                    messageOr(body, defaultMessage(status)), fieldErrors);
        }

        log.warn("Unexpected downstream status: action={} resource={} status={}", action, resource, status);
        audit(auth, action, resource, Outcome.FAILED, "downstream status " + status);
        return new HubException(HttpStatus.BAD_GATEWAY, BAD_GATEWAY_MESSAGE, null);
    }

    private static ErrorResponse parseDownstreamError(HttpStatusCodeException e) {
        try {
            return e.getResponseBodyAs(ErrorResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String messageOr(ErrorResponse body, String fallback) {
        return (body != null && body.message() != null && !body.message().isBlank())
                ? body.message() : fallback;
    }

    private static String defaultMessage(int status) {
        return switch (status) {
            case 400 -> "Invalid input";
            case 404 -> "Not found";
            default -> "Duplicate config";
        };
    }

    private static boolean isAllowed(Authentication auth, Set<String> allowedAuthorities) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> allowedAuthorities.contains(a.getAuthority()));
    }

    private void audit(Authentication auth, Action action, String resource,
                       Outcome outcome, String details) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        auditClient.write(AuditEventDto.newEvent(correlationId, auth.getName(), ActorType.USER,
                action, resource, outcome, SOURCE_SERVICE, details));
    }

    private static Set<String> authorities(String... roles) {
        return Arrays.stream(roles).map(r -> "ROLE_" + r).collect(Collectors.toUnmodifiableSet());
    }
}