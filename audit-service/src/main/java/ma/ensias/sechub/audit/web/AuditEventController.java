package ma.ensias.sechub.audit.web;

import jakarta.validation.Valid;
import ma.ensias.sechub.audit.dto.AuditEventRequest;
import ma.ensias.sechub.audit.dto.AuditEventResponse;
import ma.ensias.sechub.audit.dto.PageResponse;
import ma.ensias.sechub.audit.model.AuditAction;
import ma.ensias.sechub.audit.model.Outcome;
import ma.ensias.sechub.audit.service.AuditEventService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/audit/events")
public class AuditEventController {

    private final AuditEventService service;

    public AuditEventController(AuditEventService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditEventResponse record(@Valid @RequestBody AuditEventRequest request) {
        return service.record(request);
    }

    @GetMapping
    public PageResponse<AuditEventResponse> search(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Outcome outcome,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(actor, action, outcome, from, to, page, size);
    }
}