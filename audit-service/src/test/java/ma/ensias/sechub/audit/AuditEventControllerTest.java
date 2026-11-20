package ma.ensias.sechub.audit;

import ma.ensias.sechub.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static ma.ensias.sechub.audit.TestJwt.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditEventControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AuditEventRepository repository;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private static String body(String actor, String outcome) {
        return """
                {"correlationId":"c-%s","actor":"%s","actorType":"USER","action":"UPDATE_CONFIG",
                 "resource":"config/42","outcome":"%s","sourceService":"hub-service"}
                """.formatted(actor, actor, outcome);
    }

    private void postEvent(String json) throws Exception {
        mvc.perform(post("/audit/events").with(as("hub-service", "SVC_AUDIT_WRITER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void post_returns201_andServerSetsTimestamp() throws Exception {
        mvc.perform(post("/audit/events").with(as("hub-service", "SVC_AUDIT_WRITER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("alice", "ALLOWED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.actor").value("alice"))
                .andExpect(jsonPath("$.outcome").value("ALLOWED"));
    }

    @Test
    void get_returnsNewestFirst() throws Exception {
        postEvent(body("a1", "ALLOWED"));
        postEvent(body("a2", "ALLOWED"));
        postEvent(body("a3", "ALLOWED"));

        mvc.perform(get("/audit/events").with(as("carol", "AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].actor").value("a3"))
                .andExpect(jsonPath("$.content[2].actor").value("a1"));
    }

    @Test
    void get_filtersByActorAndOutcome() throws Exception {
        postEvent(body("alice", "ALLOWED"));
        postEvent(body("bob", "DENIED"));
        postEvent(body("bob", "ALLOWED"));

        mvc.perform(get("/audit/events").param("actor", "bob").param("outcome", "DENIED")
                        .with(as("carol", "AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actor").value("bob"))
                .andExpect(jsonPath("$.content[0].outcome").value("DENIED"));
    }

    @Test
    void get_paging() throws Exception {
        postEvent(body("a1", "ALLOWED"));
        postEvent(body("a2", "ALLOWED"));
        postEvent(body("a3", "ALLOWED"));

        mvc.perform(get("/audit/events").param("size", "2").with(as("alice", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void get_sizeIsCappedAt100() throws Exception {
        mvc.perform(get("/audit/events").param("size", "1000").with(as("alice", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void get_filtersByDateRange() throws Exception {
        postEvent(body("a1", "ALLOWED"));

        mvc.perform(get("/audit/events").param("from", "2999-01-01T00:00:00Z")
                        .with(as("alice", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/audit/events").param("to", "2000-01-01T00:00:00Z")
                        .with(as("alice", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void post_missingField_returns400_withFieldErrors() throws Exception {
        String invalid = """
                {"correlationId":"c-1","actorType":"USER","action":"UPDATE_CONFIG",
                 "resource":"config/42","outcome":"ALLOWED","sourceService":"hub-service"}
                """;

        mvc.perform(post("/audit/events").with(as("hub-service", "SVC_AUDIT_WRITER"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.actor").exists());
    }

    @Test
    void post_invalidEnum_returns400() throws Exception {
        String invalid = body("alice", "ALLOWED").replace("UPDATE_CONFIG", "HACK_THE_PLANET");

        mvc.perform(post("/audit/events").with(as("hub-service", "SVC_AUDIT_WRITER"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
    }
}