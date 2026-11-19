package ma.ensias.sechub.audit.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(nullable = false, length = 200)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(nullable = false, length = 200)
    private String resource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Outcome outcome;

    @Column(name = "source_service", nullable = false, length = 100)
    private String sourceService;

    @Column(length = 500)
    private String details;

    public Long getId() { return id; }
    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public ActorType getActorType() { return actorType; }
    public void setActorType(ActorType actorType) { this.actorType = actorType; }
    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }
    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public String getSourceService() { return sourceService; }
    public void setSourceService(String sourceService) { this.sourceService = sourceService; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}