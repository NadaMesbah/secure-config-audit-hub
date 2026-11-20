package ma.ensias.sechub.hub.web;

import ma.ensias.sechub.hub.dto.ConfigRequest;
import ma.ensias.sechub.hub.dto.ConfigResponse;
import ma.ensias.sechub.hub.service.ConfigOrchestrator;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Client-facing config routes. No security logic here: the role check, the audit and the
 * error mapping live in ConfigOrchestrator. No @Valid: Config Service validates and the Hub
 * passes its 400 through, so a forbidden caller always gets 403 first.
 */
@RestController
@RequestMapping("/api/configs")
public class ConfigController {

    private final ConfigOrchestrator orchestrator;

    public ConfigController(ConfigOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping
    public List<ConfigResponse> list(Authentication authentication,
                                     @RequestParam(required = false) String application,
                                     @RequestParam(required = false) String environment) {
        return orchestrator.list(authentication, application, environment);
    }

    @GetMapping("/{id}")
    public ConfigResponse get(Authentication authentication, @PathVariable Long id) {
        return orchestrator.get(authentication, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigResponse create(Authentication authentication, @RequestBody ConfigRequest request) {
        return orchestrator.create(authentication, request);
    }

    @PutMapping("/{id}")
    public ConfigResponse update(Authentication authentication, @PathVariable Long id,
                                 @RequestBody ConfigRequest request) {
        return orchestrator.update(authentication, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) {
        orchestrator.delete(authentication, id);
    }
}