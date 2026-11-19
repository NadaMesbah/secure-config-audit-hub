package ma.ensias.sechub.audit;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;

final class TestJwt {

    private TestJwt() {}

    static RequestPostProcessor as(String username, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(username))
                .authorities(authorities);
    }
}