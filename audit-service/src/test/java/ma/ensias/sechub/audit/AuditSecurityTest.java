package ma.ensias.sechub.audit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static ma.ensias.sechub.audit.TestJwt.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

@SpringBootTest
@AutoConfigureMockMvc
class AuditSecurityTest {

    private static final String BODY = """
            {"correlationId":"c-1","actor":"alice","actorType":"USER","action":"UPDATE_CONFIG",
             "resource":"config/42","outcome":"ALLOWED","sourceService":"hub-service"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @ParameterizedTest(name = "{0} {1} as {2} -> {3}")
    @CsvSource({
            "POST,/audit/events,SVC_AUDIT_WRITER,ALLOWED",
            "POST,/audit/events,ADMIN,FORBIDDEN",
            "POST,/audit/events,DEVELOPER,FORBIDDEN",
            "POST,/audit/events,AUDITOR,FORBIDDEN",
            "POST,/audit/events,NONE,UNAUTHORIZED",
            "GET,/audit/events,ADMIN,ALLOWED",
            "GET,/audit/events,AUDITOR,ALLOWED",
            "GET,/audit/events,DEVELOPER,FORBIDDEN",
            "GET,/audit/events,SVC_AUDIT_WRITER,FORBIDDEN",
            "GET,/audit/events,NONE,UNAUTHORIZED",
            "GET,/audit/events/1,ADMIN,FORBIDDEN",
            "GET,/audit/events/1,NONE,UNAUTHORIZED",
            "PUT,/audit/events/1,ADMIN,FORBIDDEN",
            "PUT,/audit/events/1,AUDITOR,FORBIDDEN",
            "PUT,/audit/events/1,SVC_AUDIT_WRITER,FORBIDDEN",
            "PUT,/audit/events/1,NONE,UNAUTHORIZED",
            "DELETE,/audit/events/1,ADMIN,FORBIDDEN",
            "DELETE,/audit/events/1,AUDITOR,FORBIDDEN",
            "DELETE,/audit/events/1,SVC_AUDIT_WRITER,FORBIDDEN",
            "DELETE,/audit/events/1,NONE,UNAUTHORIZED"
    })
    void rbacMatrix(String method, String path, String role, String expected) throws Exception {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
        if (!role.equals("NONE")) {
            request = request.with(as("tester", role));
        }

        int status = mvc.perform(request).andReturn().getResponse().getStatus();

        switch (expected) {
            case "ALLOWED" -> assertThat(status).isNotIn(401, 403);
            case "FORBIDDEN" -> assertThat(status).isEqualTo(403);
            case "UNAUTHORIZED" -> assertThat(status).isEqualTo(401);
            default -> throw new IllegalArgumentException(expected);
        }
    }
}