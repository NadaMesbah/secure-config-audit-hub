package ma.ensias.sechub.hub.config;

import ma.ensias.sechub.security.ClientCredentialsTokenProvider;
import ma.ensias.sechub.security.TokenRelayInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP clients of the Hub. Timeouts make an unreachable downstream fail fast (-> 502).
 * The relay interceptor forwards the caller's JWT and the correlation id, and never
 * overrides an Authorization header that is already set (the service token for audit writes).
 */
@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    ClientCredentialsTokenProvider serviceTokenProvider(
            RestClient.Builder builder,
            @Value("${sechub.keycloak.token-uri}") String tokenUri,
            @Value("${sechub.keycloak.client-id}") String clientId,
            @Value("${sechub.keycloak.client-secret}") String clientSecret) {
        RestClient tokenClient = builder.requestFactory(requestFactory()).build();
        return new ClientCredentialsTokenProvider(tokenClient, tokenUri, clientId, clientSecret);
    }

    @Bean
    RestClient configRestClient(RestClient.Builder builder,
                                @Value("${sechub.downstream.config-url}") String baseUrl) {
        return downstreamClient(builder, baseUrl);
    }

    @Bean
    RestClient auditRestClient(RestClient.Builder builder,
                               @Value("${sechub.downstream.audit-url}") String baseUrl) {
        return downstreamClient(builder, baseUrl);
    }

    private RestClient downstreamClient(RestClient.Builder builder, String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .requestInterceptor(new TokenRelayInterceptor())
                .build();
    }

    private JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}