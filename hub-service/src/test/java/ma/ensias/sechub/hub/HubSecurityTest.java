package ma.ensias.sechub.hub;

import ma.ensias.sechub.hub.dto.AuditEventDto;
import ma.ensias.sechub.hub.dto.AuditEventDto.Action;
import ma.ensias.sechub.hub.dto.AuditEventDto.Outcome;
import ma.ensias.sechub.hub.dto.ConfigResponse;
import ma.ensias.sechub.hub.dto.PageResponse;
import ma.ensias.sechub.hub.service.AuditClient;
import ma.ensias.sechub.hub.service.ConfigClient;
import ma.ensias.sechub.security.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Hub security chain without Keycloak: fake JwtDecoder, fake downstream clients.
 * Covers 401 (no token, invalid token), role rules, the DENIED audit, 502 and the correlation id.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HubSecurityTest {

    private static final String CONFIG_JSON =
            "{\"application\":\"payment-api\",\"environment\":\"prod\",\"key\":\"db.pool.size\",\"value\":\"20\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ConfigClient configClient;

    @MockitoBean
    private AuditClient auditClient;

    // ---------- 401 ----------

    @Test
    void withoutToken_returns401Json_andNothingIsAudited() throws Exception {
        mockMvc.perform(get("/api/configs"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/configs"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        verifyNoInteractions(configClient, auditClient);
    }

    @Test
    void invalidToken_returns401Json() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("invalid token"));

        mockMvc.perform(get("/api/configs").header("Authorization", "Bearer tampered.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        verifyNoInteractions(configClient, auditClient);
    }

    // ---------- public and closed routes ----------

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void anyOtherRoute_isDeniedEvenWithAValidToken() throws Exception {
        mockMvc.perform(get("/other").with(as("alice", "ROLE_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // ---------- 403 written to the audit as DENIED ----------

    @Test
    void auditorCreatingConfig_gets403_andDeniedIsAudited() throws Exception {
        mockMvc.perform(post("/api/configs").with(as("carol", "ROLE_AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIG_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied for this role"))
                .andExpect(jsonPath("$.path").value("/api/configs"));

        verifyNoInteractions(configClient);
        AuditEventDto event = capturedAudit();
        assertThat(event.actor()).isEqualTo("carol");
        assertThat(event.action()).isEqualTo(Action.CREATE_CONFIG);
        assertThat(event.resource()).isEqualTo("config");
        assertThat(event.outcome()).isEqualTo(Outcome.DENIED);
    }

    @Test
    void developerDeletingConfig_gets403_andDeniedIsAudited() throws Exception {
        mockMvc.perform(delete("/api/configs/42").with(as("bob", "ROLE_DEVELOPER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(configClient);
        AuditEventDto event = capturedAudit();
        assertThat(event.actor()).isEqualTo("bob");
        assertThat(event.action()).isEqualTo(Action.DELETE_CONFIG);
        assertThat(event.resource()).isEqualTo("config/42");
        assertThat(event.outcome()).isEqualTo(Outcome.DENIED);
    }

    @Test
    void developerReadingAudit_gets403_andDeniedIsAudited() throws Exception {
        mockMvc.perform(get("/api/audit").with(as("bob", "ROLE_DEVELOPER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verify(auditClient, never()).read(any(), any(), any(), any(), any(), any(), any());
        AuditEventDto event = capturedAudit();
        assertThat(event.action()).isEqualTo(Action.READ_AUDIT);
        assertThat(event.resource()).isEqualTo("audit");
        assertThat(event.outcome()).isEqualTo(Outcome.DENIED);
    }

    // ---------- allowed ----------

    @Test
    void adminCreatingConfig_gets201_andAllowedIsAudited() throws Exception {
        when(configClient.create(any())).thenReturn(response());

        mockMvc.perform(post("/api/configs").with(as("alice", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIG_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.key").value("db.pool.size"));

        AuditEventDto event = capturedAudit();
        assertThat(event.actor()).isEqualTo("alice");
        assertThat(event.action()).isEqualTo(Action.CREATE_CONFIG);
        assertThat(event.outcome()).isEqualTo(Outcome.ALLOWED);
    }

    @Test
    void developerUpdatingConfig_gets200() throws Exception {
        when(configClient.update(any(), any())).thenReturn(response());

        mockMvc.perform(put("/api/configs/42").with(as("bob", "ROLE_DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIG_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void adminDeletingConfig_gets204() throws Exception {
        mockMvc.perform(delete("/api/configs/42").with(as("alice", "ROLE_ADMIN")))
                .andExpect(status().isNoContent());

        verify(configClient).delete(42L);
    }

    @Test
    void auditorListingConfigs_gets200() throws Exception {
        when(configClient.list(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/configs").with(as("carol", "ROLE_AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void auditorReadingAudit_gets200() throws Exception {
        when(auditClient.read(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<AuditEventDto>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/audit").with(as("carol", "ROLE_AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    // ---------- downstream down ----------

    @Test
    void unreachableConfigService_gets502Json_andFailedIsAudited() throws Exception {
        when(configClient.list(any(), any())).thenThrow(new ResourceAccessException("connection refused"));

        mockMvc.perform(get("/api/configs").with(as("alice", "ROLE_ADMIN")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"));

        assertThat(capturedAudit().outcome()).isEqualTo(Outcome.FAILED);
    }

    // ---------- correlation ----------

    @Test
    void correlationIdIsKeptAndWrittenToTheAudit() throws Exception {
        when(configClient.list(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/configs").header(CorrelationIdFilter.HEADER, "corr-abc")
                        .with(as("alice", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "corr-abc"));

        assertThat(capturedAudit().correlationId()).isEqualTo("corr-abc");
    }

    // ---------- helpers ----------

    private static RequestPostProcessor as(String username, String... roles) {
        SimpleGrantedAuthority[] authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(token -> token.subject(username)).authorities(authorities);
    }

    private static ConfigResponse response() {
        return new ConfigResponse(42L, "payment-api", "prod", "db.pool.size", "20", 1,
                "alice", Instant.parse("2026-11-15T10:00:00Z"));
    }

    private AuditEventDto capturedAudit() {
        ArgumentCaptor<AuditEventDto> captor = ArgumentCaptor.forClass(AuditEventDto.class);
        verify(auditClient).write(captor.capture());
        return captor.getValue();
    }
}