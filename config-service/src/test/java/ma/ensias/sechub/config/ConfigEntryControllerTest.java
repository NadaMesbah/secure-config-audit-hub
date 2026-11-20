package ma.ensias.sechub.config;

import com.jayway.jsonpath.JsonPath;
import ma.ensias.sechub.config.repository.ConfigEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static ma.ensias.sechub.config.TestJwt.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigEntryControllerTest {

    private static final String BODY =
            "{\"application\":\"payment-api\",\"environment\":\"prod\",\"key\":\"db.pool.size\",\"value\":\"20\"}";

    @Autowired
    MockMvc mvc;

    @Autowired
    ConfigEntryRepository repository;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private long createConfig(String body) throws Exception {
        String json = mvc.perform(post("/configs").with(as("alice", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void create_returns201_withVersion1() throws Exception {
        mvc.perform(post("/configs").with(as("alice", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.key").value("db.pool.size"))
                .andExpect(jsonPath("$.updatedBy").value("alice"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void update_incrementsVersion() throws Exception {
        long id = createConfig(BODY);
        String updated = BODY.replace("\"20\"", "\"30\"");

        mvc.perform(put("/configs/" + id).with(as("bob", "DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.value").value("30"))
                .andExpect(jsonPath("$.updatedBy").value("bob"));
    }

    @Test
    void duplicate_returns409() throws Exception {
        createConfig(BODY);

        mvc.perform(post("/configs").with(as("alice", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidInput_returns400_withFieldErrors() throws Exception {
        String invalid = "{\"application\":\"\",\"environment\":\"test\",\"key\":\"k\",\"value\":\"v\"}";

        mvc.perform(post("/configs").with(as("alice", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.application").exists())
                .andExpect(jsonPath("$.fieldErrors.environment").exists());
    }

    @Test
    void unknownId_returns404() throws Exception {
        mvc.perform(get("/configs/9999").with(as("alice", "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204_thenNotFound() throws Exception {
        long id = createConfig(BODY);

        mvc.perform(delete("/configs/" + id).with(as("alice", "ADMIN")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/configs/" + id).with(as("alice", "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_filtersByApplication() throws Exception {
        createConfig(BODY);
        createConfig(BODY.replace("payment-api", "billing-api"));

        mvc.perform(get("/configs").param("application", "payment-api").with(as("carol", "AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}