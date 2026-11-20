package ma.ensias.sechub.hub.web;

import ma.ensias.sechub.hub.dto.AuditEventDto;
import ma.ensias.sechub.hub.dto.PageResponse;
import ma.ensias.sechub.hub.service.ConfigOrchestrator;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client-facing audit read route. Filters are optional and passed as is to Audit Service,
 * which validates them (ISO-8601 dates, size capped at 100).
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final ConfigOrchestrator orchestrator;

    public AuditController(ConfigOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping
    public PageResponse<AuditEventDto> read(Authentication authentication,
                                            @RequestParam(required = false) String actor,
                                            @RequestParam(required = false) String action,
                                            @RequestParam(required = false) String outcome,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        return orchestrator.readAudit(authentication, actor, action, outcome, from, to, page, size);
    }
}