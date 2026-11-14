package ma.ensias.sechub.security;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(Instant timestamp, int status, String error, String message,
                            String path, String correlationId, Map<String, String> fieldErrors) {}
