package ma.ensias.sechub.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.ensias.sechub.audit.model.ActorType;
import ma.ensias.sechub.audit.model.AuditAction;
import ma.ensias.sechub.audit.model.Outcome;

public record AuditEventRequest(
        @NotBlank @Size(max = 64) String correlationId,
        @NotBlank @Size(max = 200) String actor,
        @NotNull ActorType actorType,
        @NotNull AuditAction action,
        @NotBlank @Size(max = 200) String resource,
        @NotNull Outcome outcome,
        @NotBlank @Size(max = 100) String sourceService,
        @Size(max = 500) String details) {}