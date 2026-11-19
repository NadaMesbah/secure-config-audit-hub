package ma.ensias.sechub.config.security;

import ma.ensias.sechub.security.JsonAccessDeniedHandler;
import ma.ensias.sechub.security.JsonAuthenticationEntryPoint;
import ma.ensias.sechub.security.KeycloakRoleConverter;
import ma.ensias.sechub.security.Roles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ConfigSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JsonAuthenticationEntryPoint entryPoint,
                                    JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/configs", "/configs/**")
                        .hasAnyRole(Roles.ADMIN, Roles.DEVELOPER, Roles.AUDITOR)
                        .requestMatchers(HttpMethod.POST, "/configs")
                        .hasAnyRole(Roles.ADMIN, Roles.DEVELOPER)
                        .requestMatchers(HttpMethod.PUT, "/configs/*")
                        .hasAnyRole(Roles.ADMIN, Roles.DEVELOPER)
                        .requestMatchers(HttpMethod.DELETE, "/configs/*")
                        .hasRole(Roles.ADMIN)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakRoleConverter())))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}