package ma.ensias.sechub.config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfigEntryRequest(
        @NotBlank @Size(max = 100) String application,
        @NotBlank @Pattern(regexp = "dev|staging|prod", message = "must be dev, staging or prod") String environment,
        @NotBlank @Size(max = 200) String key,
        @NotBlank @Size(max = 2000) String value) {}