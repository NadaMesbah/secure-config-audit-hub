package ma.ensias.sechub.hub.dto;

import java.util.List;

/** Paged response of GET /audit/events (sorted newest first by Audit Service). */
public record PageResponse<T>(List<T> content, int page, int size,
                              long totalElements, int totalPages) {}