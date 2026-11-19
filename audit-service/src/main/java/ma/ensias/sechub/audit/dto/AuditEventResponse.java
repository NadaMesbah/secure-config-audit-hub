package ma.ensias.sechub.audit.dto;

import ma.ensias.sechub.audit.model.ActorType;
import ma.ensias.sechub.audit.model.AuditAction;
import ma.ensias.sechub.audit.model.AuditEvent;
import ma.ensias.sechub.audit.model.Outcome;

import java.time.Instant;

public record AuditEventResponse(Long id, Instant timestamp, String correlationId, String actor,
                                 ActorType actorType, AuditAction action, String resource,
                                 Outcome outcome, String sourceService, String details) {

    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getEventTime(), e.getCorrelationId(), e.getActor(),
                e.getActorType(), e.getAction(), e.getResource(), e.getOutcome(),
                e.getSourceService(), e.getDetails());
    }
}