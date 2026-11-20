package ma.ensias.sechub.hub;

import ma.ensias.sechub.hub.dto.AuditEventDto;
import ma.ensias.sechub.hub.dto.AuditEventDto.Action;
import ma.ensias.sechub.hub.dto.AuditEventDto.ActorType;
import ma.ensias.sechub.hub.dto.AuditEventDto.Outcome;
import ma.ensias.sechub.hub.dto.ConfigRequest;
import ma.ensias.sechub.hub.dto.ConfigResponse;
import ma.ensias.sechub.hub.dto.PageResponse;
import ma.ensias.sechub.hub.service.AuditClient;
import ma.ensias.sechub.hub.service.ConfigClient;
import ma.ensias.sechub.hub.service.ConfigOrchestrator;
import ma.ensias.sechub.hub.service.ConfigOrchestrator.HubException;
import ma.ensias.sechub.security.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Outcome mapping (ALLOWED / DENIED / FAILED), role checks and 502 on downstream failure. */
@ExtendWith(MockitoExtension.class)
class ConfigOrchestratorTest {

    @Mock
    private ConfigClient configClient;

    @Mock
    private AuditClient auditClient;

    private ConfigOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new ConfigOrchestrator(configClient, auditClient);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // ---------- allowed ----------

    @Test
    void adminListsConfigs_isAllowedAndAudited() {
        when(configClient.list("payment-api", "prod")).thenReturn(List.of());

        List<ConfigResponse> result =
                orchestrator.list(user("alice", "ROLE_ADMIN"), "payment-api", "prod");

        assertThat(result).isEmpty();
        AuditEventDto event = capturedAudit();
        assertThat(event.actor()).isEqualTo("alice");
        assertThat(event.actorType()).isEqualTo(ActorType.USER);
        assertThat(event.action()).isEqualTo(Action.LIST_CONFIGS);
        assertThat(event.resource()).isEqualTo("config");
        assertThat(event.outcome()).isEqualTo(Outcome.ALLOWED);
        assertThat(event.sourceService()).isEqualTo("hub-service");
        assertThat(event.correlationId()).isNotBlank();
    }

    @Test
    void developerUpdatesConfig_isAllowedAndAudited() {
        ConfigRequest request = request();
        when(configClient.update(42L, request)).thenReturn(response());

        ConfigResponse result = orchestrator.update(user("bob", "ROLE_DEVELOPER"), 42L, request);

        assertThat(result.id()).isEqualTo(42L);
        AuditEventDto event = capturedAudit();
        assertThat(event.actor()).isEqualTo("bob");
        assertThat(event.action()).isEqualTo(Action.UPDATE_CONFIG);
        assertThat(event.resource()).isEqualTo("config/42");
        assertThat(event.outcome()).isEqualTo(Outcome.ALLOWED);
    }

    @Test
    void adminDeletesConfig_isAllowedAndAudited() {
        orchestrator.delete(user("alice", "ROLE_ADMIN"), 42L);

        verify(configClient).delete(42L);
        AuditEventDto event = capturedAudit();
        assertThat(event.action()).isEqualTo(Action.DELETE_CONFIG);
        assertThat(event.resource()).isEqualTo("config/42");
        assertThat(event.outcome()).isEqualTo(Outcome.ALLOWED);
    }

    @Test
    void auditorReadsAudit_isAllowedAndAudited() {
        when(auditClient.read(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<AuditEventDto>(List.of(), 0, 20, 0L, 0));

        PageResponse<AuditEventDto> result = orchestrator.readAudit(
                user("carol", "ROLE_AUDITOR"), null, null, null, null, null, null, null);

        assertThat(result.page()).isZero();
        AuditEventDto event = capturedAudit();
        assertThat(event.actor()).isEqualTo("carol");
        assertThat(event.action()).isEqualTo(Action.READ_AUDIT);
        assertThat(event.resource()).isEqualTo("audit");
        assertThat(event.outcome()).isEqualTo(Outcome.ALLOWED);
    }

    // ---------- denied by the Hub (no downstream call) ----------

    @Test
    void auditorCreatingConfig_isDeniedWithoutCallingConfig() {
        HubException e = assertThrows(HubException.class,
                () -> orchestrator.create(user("carol", "ROLE_AUDITOR"), request()));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(configClient);
        AuditEventDto event = capturedAudit();
        assertThat(event.actor()).isEqualTo("carol");
        assertThat(event.action()).isEqualTo(Action.CREATE_CONFIG);
        assertThat(event.resource()).isEqualTo("config");
        assertThat(event.outcome()).isEqualTo(Outcome.DENIED);
    }

    @Test
    void developerDeletingConfig_isDeniedWithoutCallingConfig() {
        HubException e = assertThrows(HubException.class,
                () -> orchestrator.delete(user("bob", "ROLE_DEVELOPER"), 42L));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(configClient);
        AuditEventDto event = capturedAudit();
        assertThat(event.actor()).isEqualTo("bob");
        assertThat(event.action()).isEqualTo(Action.DELETE_CONFIG);
        assertThat(event.resource()).isEqualTo("config/42");
        assertThat(event.outcome()).isEqualTo(Outcome.DENIED);
    }

    @Test
    void developerReadingAudit_isDeniedWithoutCallingAudit() {
        HubException e = assertThrows(HubException.class, () -> orchestrator.readAudit(
                user("bob", "ROLE_DEVELOPER"), null, null, null, null, null, null, null));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(auditClient, never()).read(any(), any(), any(), any(), any(), any(), any());
        AuditEventDto event = capturedAudit();
        assertThat(event.action()).isEqualTo(Action.READ_AUDIT);
        assertThat(event.resource()).isEqualTo("audit");
        assertThat(event.outcome()).isEqualTo(Outcome.DENIED);
    }

    @Test
    void serviceWriterRole_isDeniedOnList() {
        HubException e = assertThrows(HubException.class,
                () -> orchestrator.list(user("service-account-hub-service", "ROLE_SVC_AUDIT_WRITER"),
                        null, null));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(configClient);
        assertThat(capturedAudit().outcome()).isEqualTo(Outcome.DENIED);
    }

    @Test
    void userWithoutRoles_isDenied() {
        HubException e = assertThrows(HubException.class,
                () -> orchestrator.get(user("mallory"), 42L));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(configClient);
        assertThat(capturedAudit().outcome()).isEqualTo(Outcome.DENIED);
    }

    // ---------- downstream errors ----------

    @Test
    void downstreamForbidden_isAuditedAsDenied() {
        when(configClient.get(42L)).thenThrow(downstream(403));

        HubException e = assertThrows(HubException.class,
                () -> orchestrator.get(user("alice", "ROLE_ADMIN"), 42L));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        AuditEventDto event = capturedAudit();
        assertThat(event.action()).isEqualTo(Action.READ_CONFIG);
        assertThat(event.resource()).isEqualTo("config/42");
        assertThat(event.outcome()).isEqualTo(Outcome.DENIED);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 409})
    void downstreamClientErrors_arePassedThroughAndAuditedAsFailed(int status) {
        when(configClient.get(42L)).thenThrow(downstream(status));

        HubException e = assertThrows(HubException.class,
                () -> orchestrator.get(user("alice", "ROLE_ADMIN"), 42L));

        assertThat(e.getStatus().value()).isEqualTo(status);
        assertThat(capturedAudit().outcome()).isEqualTo(Outcome.FAILED);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 500, 503})
    void unexpectedDownstreamStatus_becomesBadGateway(int status) {
        when(configClient.get(42L)).thenThrow(downstream(status));

        HubException e = assertThrows(HubException.class,
                () -> orchestrator.get(user("alice", "ROLE_ADMIN"), 42L));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(capturedAudit().outcome()).isEqualTo(Outcome.FAILED);
    }

    @Test
    void unreachableDownstream_becomesBadGateway() {
        when(configClient.get(42L)).thenThrow(new ResourceAccessException("connection refused"));

        HubException e = assertThrows(HubException.class,
                () -> orchestrator.get(user("alice", "ROLE_ADMIN"), 42L));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(capturedAudit().outcome()).isEqualTo(Outcome.FAILED);
    }

    // ---------- correlation ----------

    @Test
    void auditEventCarriesTheCorrelationId() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");
        when(configClient.get(42L)).thenReturn(response());

        orchestrator.get(user("alice", "ROLE_ADMIN"), 42L);

        assertThat(capturedAudit().correlationId()).isEqualTo("corr-123");
    }

    // ---------- helpers ----------

    private static Authentication user(String name, String... authorities) {
        return new TestingAuthenticationToken(name, "n/a", authorities);
    }

    private static ConfigRequest request() {
        return new ConfigRequest("payment-api", "prod", "db.pool.size", "20");
    }

    private static ConfigResponse response() {
        return new ConfigResponse(42L, "payment-api", "prod", "db.pool.size", "20", 1,
                "alice", Instant.parse("2026-11-15T10:00:00Z"));
    }

    private static HttpStatusCodeException downstream(int status) {
        HttpStatus httpStatus = HttpStatus.valueOf(status);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        if (status >= 500) {
            return HttpServerErrorException.create(httpStatus, httpStatus.getReasonPhrase(),
                    HttpHeaders.EMPTY, body, StandardCharsets.UTF_8);
        }
        return HttpClientErrorException.create(httpStatus, httpStatus.getReasonPhrase(),
                HttpHeaders.EMPTY, body, StandardCharsets.UTF_8);
    }

    private AuditEventDto capturedAudit() {
        ArgumentCaptor<AuditEventDto> captor = ArgumentCaptor.forClass(AuditEventDto.class);
        verify(auditClient).write(captor.capture());
        return captor.getValue();
    }
}