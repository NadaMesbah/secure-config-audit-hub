package ma.ensias.sechub.hub.dto;

/** Body of POST and PUT /api/configs. Validated by Config Service, not by the Hub. */
public record ConfigRequest(String application, String environment, String key, String value) {}