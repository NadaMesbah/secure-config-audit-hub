package ma.ensias.sechub.hub.config;

import ma.ensias.sechub.security.JsonAccessDeniedHandler;
import ma.ensias.sechub.security.JsonAuthenticationEntryPoint;
import ma.ensias.sechub.security.KeycloakRoleConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Hub security chain: stateless, JWT only. /actuator/health is public and /api/**
 * only requires a valid token. The role check happens in ConfigOrchestrator so
 * that every denial can be audited.
 */
@Configuration
public class HubSecurityConfig {

    @Bean
    SecurityFilterChain hubSecurityFilterChain(HttpSecurity http,
                                               JsonAuthenticationEntryPoint authenticationEntryPoint,
                                               JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakRoleConverter())))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}