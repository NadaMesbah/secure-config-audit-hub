package ma.ensias.sechub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reads X-Correlation-Id (or generates one), puts it in the logs (MDC), the response
 * header and a request attribute so that error handlers can include it in the error JSON.
 * Runs before the Spring Security chain so that 401 responses carry the id too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".ID";

    // Only letters, digits and '-' (max 64): anything else could forge log lines.
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || !VALID_ID.matcher(id).matches()) {
            id = UUID.randomUUID().toString();
        }
        request.setAttribute(REQUEST_ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        MDC.put(MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Correlation id of the current request, or null outside the filter. */
    public static String fromRequest(HttpServletRequest request) {
        Object id = request.getAttribute(REQUEST_ATTRIBUTE);
        return id instanceof String s ? s : null;
    }
}