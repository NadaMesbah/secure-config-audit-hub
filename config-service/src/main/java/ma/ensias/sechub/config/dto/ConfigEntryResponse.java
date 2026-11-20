package ma.ensias.sechub.config.dto;

import ma.ensias.sechub.config.model.ConfigEntry;
import java.time.Instant;

public record ConfigEntryResponse(Long id, String application, String environment, String key,
                                  String value, int version, String updatedBy, Instant updatedAt) {

    public static ConfigEntryResponse from(ConfigEntry e) {
        return new ConfigEntryResponse(e.getId(), e.getApplication(), e.getEnvironment(),
                e.getConfigKey(), e.getConfigValue(), e.getVersion(), e.getUpdatedBy(), e.getUpdatedAt());
    }
}