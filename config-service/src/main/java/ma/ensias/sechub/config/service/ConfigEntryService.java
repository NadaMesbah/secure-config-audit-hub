package ma.ensias.sechub.config.service;

import ma.ensias.sechub.config.dto.ConfigEntryRequest;
import ma.ensias.sechub.config.dto.ConfigEntryResponse;
import ma.ensias.sechub.config.model.ConfigEntry;
import ma.ensias.sechub.config.repository.ConfigEntryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class ConfigEntryService {

    private final ConfigEntryRepository repository;

    public ConfigEntryService(ConfigEntryRepository repository) {
        this.repository = repository;
    }

    public List<ConfigEntryResponse> list(String application, String environment) {
        boolean hasApp = StringUtils.hasText(application);
        boolean hasEnv = StringUtils.hasText(environment);
        List<ConfigEntry> entries;
        if (hasApp && hasEnv) {
            entries = repository.findByApplicationAndEnvironmentOrderById(application, environment);
        } else if (hasApp) {
            entries = repository.findByApplicationOrderById(application);
        } else if (hasEnv) {
            entries = repository.findByEnvironmentOrderById(environment);
        } else {
            entries = repository.findAll(Sort.by("id"));
        }
        return entries.stream().map(ConfigEntryResponse::from).toList();
    }

    public ConfigEntryResponse get(Long id) {
        return ConfigEntryResponse.from(find(id));
    }

    @Transactional
    public ConfigEntryResponse create(ConfigEntryRequest request, String actor) {
        if (repository.existsByApplicationAndEnvironmentAndConfigKey(
                request.application(), request.environment(), request.key())) {
            throw new DuplicateConfigException("A config with this application, environment and key already exists");
        }
        ConfigEntry entry = new ConfigEntry();
        apply(entry, request, actor);
        entry.setVersion(1);
        return ConfigEntryResponse.from(repository.save(entry));
    }

    @Transactional
    public ConfigEntryResponse update(Long id, ConfigEntryRequest request, String actor) {
        ConfigEntry entry = find(id);
        if (repository.existsByApplicationAndEnvironmentAndConfigKeyAndIdNot(
                request.application(), request.environment(), request.key(), id)) {
            throw new DuplicateConfigException("A config with this application, environment and key already exists");
        }
        apply(entry, request, actor);
        entry.setVersion(entry.getVersion() + 1);
        return ConfigEntryResponse.from(repository.save(entry));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private ConfigEntry find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Config " + id + " not found"));
    }

    private void apply(ConfigEntry entry, ConfigEntryRequest request, String actor) {
        entry.setApplication(request.application());
        entry.setEnvironment(request.environment());
        entry.setConfigKey(request.key());
        entry.setConfigValue(request.value());
        entry.setUpdatedBy(actor);
        entry.setUpdatedAt(Instant.now());
    }
}