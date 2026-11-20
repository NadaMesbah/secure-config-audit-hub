package ma.ensias.sechub.hub.service;

import ma.ensias.sechub.hub.dto.ConfigRequest;
import ma.ensias.sechub.hub.dto.ConfigResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Thin HTTP client for Config Service. The caller's JWT is relayed by TokenRelayInterceptor.
 * Errors (4xx, 5xx, unreachable) propagate as RestClient exceptions: ConfigOrchestrator maps them.
 */
@Component
public class ConfigClient {

    private final RestClient restClient;

    public ConfigClient(@Qualifier("configRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public List<ConfigResponse> list(String application, String environment) {
        return restClient.get()
                .uri(uri -> uri.path("/configs")
                        .queryParamIfPresent("application", java.util.Optional.ofNullable(application))
                        .queryParamIfPresent("environment", java.util.Optional.ofNullable(environment))
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<ConfigResponse>>() {});
    }

    public ConfigResponse get(Long id) {
        return restClient.get()
                .uri("/configs/{id}", id)
                .retrieve()
                .body(ConfigResponse.class);
    }

    public ConfigResponse create(ConfigRequest request) {
        return restClient.post()
                .uri("/configs")
                .body(request)
                .retrieve()
                .body(ConfigResponse.class);
    }

    public ConfigResponse update(Long id, ConfigRequest request) {
        return restClient.put()
                .uri("/configs/{id}", id)
                .body(request)
                .retrieve()
                .body(ConfigResponse.class);
    }

    public void delete(Long id) {
        restClient.delete()
                .uri("/configs/{id}", id)
                .retrieve()
                .toBodilessEntity();
    }
}