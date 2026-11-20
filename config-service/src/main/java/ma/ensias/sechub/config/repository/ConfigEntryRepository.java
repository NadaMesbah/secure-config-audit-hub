package ma.ensias.sechub.config.repository;

import ma.ensias.sechub.config.model.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConfigEntryRepository extends JpaRepository<ConfigEntry, Long> {
    List<ConfigEntry> findByApplicationOrderById(String application);
    List<ConfigEntry> findByEnvironmentOrderById(String environment);
    List<ConfigEntry> findByApplicationAndEnvironmentOrderById(String application, String environment);
    boolean existsByApplicationAndEnvironmentAndConfigKey(String application, String environment, String configKey);
    boolean existsByApplicationAndEnvironmentAndConfigKeyAndIdNot(String application, String environment,
                                                                  String configKey, Long id);
}
