package ma.ensias.sechub.security;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

/**
 * Obtains (and caches) a service access token from Keycloak with the
 * OAuth2 client credentials grant. Not a @Component: the Hub builds it
 * from its own sechub.keycloak.* settings.
 * Never logs the client secret or the token. Failures propagate to the caller.
 */
public class ClientCredentialsTokenProvider {

    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    private String cachedToken;
    private Instant cachedExpiry = Instant.EPOCH;

    public ClientCredentialsTokenProvider(String tokenUri, String clientId, String clientSecret) {
        this(RestClient.create(), tokenUri, clientId, clientSecret);
    }

    public ClientCredentialsTokenProvider(RestClient restClient, String tokenUri,
                                          String clientId, String clientSecret) {
        this.restClient = restClient;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** Returns a valid service token, requesting a new one only when the cached one is about to expire. */
    public synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedExpiry)) {
            return cachedToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        JsonNode response = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.hasNonNull("access_token")) {
            throw new IllegalStateException("Token endpoint returned no access_token");
        }

        long expiresIn = response.path("expires_in").asLong(60);
        cachedToken = response.get("access_token").asText();
        cachedExpiry = Instant.now().plusSeconds(expiresIn).minus(EXPIRY_MARGIN);
        return cachedToken;
    }
}