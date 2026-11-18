package ma.ensias.sechub.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts a Keycloak JWT into a Spring Security authentication:
 * realm_access.roles -> ROLE_<UPPERCASE> (with '-' replaced by '_'),
 * principal name = preferred_username.
 */
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_KEY = "roles";
    private static final String USERNAME_CLAIM = "preferred_username";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        String principalName = jwt.getClaimAsString(USERNAME_CLAIM);
        if (principalName == null || principalName.isBlank()) {
            principalName = jwt.getSubject();
        }
        return new JwtAuthenticationToken(jwt, authorities, principalName);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Object realmAccess = jwt.getClaim(REALM_ACCESS_CLAIM);
        if (!(realmAccess instanceof Map<?, ?> realmMap)) {
            return List.of();
        }
        Object roles = realmMap.get(ROLES_KEY);
        if (!(roles instanceof Collection<?> roleCollection)) {
            return List.of();
        }
        return roleCollection.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> ROLE_PREFIX + role.toUpperCase(Locale.ROOT).replace('-', '_'))
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }
}