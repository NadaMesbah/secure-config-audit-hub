package ma.ensias.sechub.hub.dto;

import java.time.Instant;

/** Config entry as returned by Config Service (and relayed as is by the Hub). */
public record ConfigResponse(Long id, String application, String environment, String key,
                             String value, int version, String updatedBy, Instant updatedAt) {}