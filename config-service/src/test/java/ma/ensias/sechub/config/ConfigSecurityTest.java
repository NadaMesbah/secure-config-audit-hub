package ma.ensias.sechub.config;

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

import static ma.ensias.sechub.config.TestJwt.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigSecurityTest {

    private static final String BODY =
            "{\"application\":\"payment-api\",\"environment\":\"prod\",\"key\":\"k\",\"value\":\"v\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @ParameterizedTest(name = "{0} {1} as {2} -> {3}")
    @CsvSource({
            "GET,/configs,ADMIN,ALLOWED",
            "GET,/configs,DEVELOPER,ALLOWED",
            "GET,/configs,AUDITOR,ALLOWED",
            "GET,/configs,SVC_AUDIT_WRITER,FORBIDDEN",
            "GET,/configs,NONE,UNAUTHORIZED",
            "GET,/configs/1,ADMIN,ALLOWED",
            "GET,/configs/1,DEVELOPER,ALLOWED",
            "GET,/configs/1,AUDITOR,ALLOWED",
            "GET,/configs/1,SVC_AUDIT_WRITER,FORBIDDEN",
            "GET,/configs/1,NONE,UNAUTHORIZED",
            "POST,/configs,ADMIN,ALLOWED",
            "POST,/configs,DEVELOPER,ALLOWED",
            "POST,/configs,AUDITOR,FORBIDDEN",
            "POST,/configs,SVC_AUDIT_WRITER,FORBIDDEN",
            "POST,/configs,NONE,UNAUTHORIZED",
            "PUT,/configs/1,ADMIN,ALLOWED",
            "PUT,/configs/1,DEVELOPER,ALLOWED",
            "PUT,/configs/1,AUDITOR,FORBIDDEN",
            "PUT,/configs/1,SVC_AUDIT_WRITER,FORBIDDEN",
            "PUT,/configs/1,NONE,UNAUTHORIZED",
            "DELETE,/configs/1,ADMIN,ALLOWED",
            "DELETE,/configs/1,DEVELOPER,FORBIDDEN",
            "DELETE,/configs/1,AUDITOR,FORBIDDEN",
            "DELETE,/configs/1,SVC_AUDIT_WRITER,FORBIDDEN",
            "DELETE,/configs/1,NONE,UNAUTHORIZED"
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