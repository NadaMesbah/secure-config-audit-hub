package ma.ensias.sechub.config.web;

import jakarta.validation.Valid;
import ma.ensias.sechub.config.dto.ConfigEntryRequest;
import ma.ensias.sechub.config.dto.ConfigEntryResponse;
import ma.ensias.sechub.config.service.ConfigEntryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/configs")
public class ConfigEntryController {

    private final ConfigEntryService service;

    public ConfigEntryController(ConfigEntryService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConfigEntryResponse> list(@RequestParam(required = false) String application,
                                          @RequestParam(required = false) String environment) {
        return service.list(application, environment);
    }

    @GetMapping("/{id}")
    public ConfigEntryResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigEntryResponse create(@Valid @RequestBody ConfigEntryRequest request,
                                      Authentication authentication) {
        return service.create(request, authentication.getName());
    }

    @PutMapping("/{id}")
    public ConfigEntryResponse update(@PathVariable Long id,
                                      @Valid @RequestBody ConfigEntryRequest request,
                                      Authentication authentication) {
        return service.update(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}