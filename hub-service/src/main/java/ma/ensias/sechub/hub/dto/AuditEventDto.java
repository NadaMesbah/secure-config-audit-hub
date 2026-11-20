package ma.ensias.sechub.hub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Audit event. For POST /audit/events, id and timestamp are null (set by the server)
 * and are omitted from the JSON; GET responses include them.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEventDto(Long id, Instant timestamp, String correlationId, String actor,
                            ActorType actorType, Action action, String resource,
                            Outcome outcome, String sourceService, String details) {

    public enum ActorType { USER, SERVICE }

    public enum Action {
        LIST_CONFIGS, READ_CONFIG, CREATE_CONFIG, UPDATE_CONFIG, DELETE_CONFIG, READ_AUDIT
    }

    public enum Outcome { ALLOWED, DENIED, FAILED }

    /** Builds an event to send to Audit Service (id and timestamp are set by the server). */
    public static AuditEventDto newEvent(String correlationId, String actor, ActorType actorType,
                                         Action action, String resource, Outcome outcome,
                                         String sourceService, String details) {
        return new AuditEventDto(null, null, correlationId, actor, actorType, action,
                resource, outcome, sourceService, details);
    }
}