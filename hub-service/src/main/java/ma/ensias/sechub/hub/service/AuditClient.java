package ma.ensias.sechub.hub.service;

import ma.ensias.sechub.hub.dto.AuditEventDto;
import ma.ensias.sechub.hub.dto.PageResponse;
import ma.ensias.sechub.security.ClientCredentialsTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * HTTP client for Audit Service.
 * - write: uses the Hub's own service token, set explicitly (the relay interceptor never
 *   overrides an existing Authorization header). Fail-open: any failure, including
 *   getToken(), is logged as a warning and never reaches the caller.
 * - read: the caller's JWT is relayed; errors propagate to ConfigOrchestrator.
 */
@Component
public class AuditClient {

    private static final Logger log = LoggerFactory.getLogger(AuditClient.class);

    private final RestClient restClient;
    private final ClientCredentialsTokenProvider serviceTokenProvider;

    public AuditClient(@Qualifier("auditRestClient") RestClient restClient,
                       ClientCredentialsTokenProvider serviceTokenProvider) {
        this.restClient = restClient;
        this.serviceTokenProvider = serviceTokenProvider;
    }

    /** Fail-open: never throws. */
    public void write(AuditEventDto event) {
        try {
            String serviceToken = serviceTokenProvider.getToken();
            restClient.post()
                    .uri("/audit/events")
                    .headers(headers -> headers.setBearerAuth(serviceToken))
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Audit write failed (fail-open, request continues): action={} resource={} cause={}: {}",
                    event.action(), event.resource(), e.getClass().getSimpleName(), e.getMessage());
        }
    }

    public PageResponse<AuditEventDto> read(String actor, String action, String outcome,
                                            String from, String to, Integer page, Integer size) {
        return restClient.get()
                .uri(uri -> uri.path("/audit/events")
                        .queryParamIfPresent("actor", Optional.ofNullable(actor))
                        .queryParamIfPresent("action", Optional.ofNullable(action))
                        .queryParamIfPresent("outcome", Optional.ofNullable(outcome))
                        .queryParamIfPresent("from", Optional.ofNullable(from))
                        .queryParamIfPresent("to", Optional.ofNullable(to))
                        .queryParamIfPresent("page", Optional.ofNullable(page))
                        .queryParamIfPresent("size", Optional.ofNullable(size))
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<PageResponse<AuditEventDto>>() {});
    }
}