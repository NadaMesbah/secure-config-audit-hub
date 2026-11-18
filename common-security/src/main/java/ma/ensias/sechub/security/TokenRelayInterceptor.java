package ma.ensias.sechub.security;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

/**
 * Relays the caller's JWT and the correlation id on outgoing calls.
 * Never overrides an Authorization header that is already set (e.g. the service token).
 * Not a @Component: attach it only to the clients that target our own services.
 */
public class TokenRelayInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();

        if (!headers.containsKey(HttpHeaders.AUTHORIZATION)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                headers.setBearerAuth(jwtAuthentication.getToken().getTokenValue());
            }
        }

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null && !headers.containsKey(CorrelationIdFilter.HEADER)) {
            headers.set(CorrelationIdFilter.HEADER, correlationId);
        }

        return execution.execute(request, body);
    }
}