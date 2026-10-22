package com.yue.dubbingmaterial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yue.repository.AdminAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dubbing_admin_flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-only-0123456789012345678901234567890123456789"
})
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class AdminDubbingMaterialFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdminAuditLogRepository audits;
    @MockBean DubbingVideoStorage storage;

    @BeforeEach void storageResult() {
        when(storage.store(any())).thenReturn(new DubbingVideoStorage.StoredVideo(
                "stored.mp4", "video/mp4", 1234, 5000, "h264", "aac"));
    }

    @Test void createEditUploadPublishPublicReadOfflineAndDeleteAreVersionedAndAudited() throws Exception {
        long before = audits.count();
        JsonNode created = node(mvc.perform(post("/api/admin/dubbing-materials")
                        .with(user("operator").roles("ADMIN")).contentType("application/json").content(materialBody("movie-one", 0, "[]")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn());
        long id = created.path("id").asLong();
        long materialVersion = created.path("materialVersion").asLong();

        JsonNode withPart = node(mvc.perform(post("/api/admin/dubbing-materials/" + id + "/parts")
                        .with(user("operator").roles("ADMIN")).contentType("application/json")
                        .content(partBody(materialVersion)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parts[0].cues[0].text").value("你好呀")).andReturn());
        materialVersion = withPart.path("materialVersion").asLong();
        long partId = withPart.path("parts").get(0).path("id").asLong();
        long partVersion = withPart.path("parts").get(0).path("version").asLong();

        JsonNode uploaded = node(mvc.perform(multipart("/api/admin/dubbing-materials/" + id + "/parts/" + partId + "/video")
                        .file(new MockMultipartFile("file", "part.mp4", "video/mp4", new byte[12]))
                        .param("materialVersion", Long.toString(materialVersion)).param("partVersion", Long.toString(partVersion))
                        .with(user("operator").roles("ADMIN"))
                        .with(request -> { request.setContent(new byte[1]); return request; }))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parts[0].video.durationMs").value(5000)).andReturn());
        materialVersion = uploaded.path("materialVersion").asLong();
        partVersion = uploaded.path("parts").get(0).path("version").asLong();

        String editedParts = "[{\"id\":" + partId + ",\"code\":\"part-01\",\"label\":\"Part 01\","
                + "\"title\":\"更新标题\",\"sortOrder\":2,\"version\":" + partVersion + ","
                + "\"cues\":[{\"code\":\"cue-01\",\"startMs\":100,\"endMs\":1600,\"text\":\"更新台词\"}]}]";
        JsonNode edited = node(mvc.perform(put("/api/admin/dubbing-materials/" + id)
                        .with(user("operator").roles("ADMIN")).contentType("application/json")
                        .content(materialBody("movie-one", materialVersion, editedParts)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parts[0].sortOrder").value(2))
                .andExpect(jsonPath("$.parts[0].cues[0].text").value("更新台词")).andReturn());
        materialVersion = edited.path("materialVersion").asLong();

        JsonNode published = node(mvc.perform(put("/api/admin/dubbing-materials/" + id + "/status")
                        .with(user("operator").roles("ADMIN")).contentType("application/json")
                        .content("{\"status\":\"PUBLISHED\",\"materialVersion\":" + materialVersion + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED")).andReturn());
        materialVersion = published.path("materialVersion").asLong();

        mvc.perform(get("/api/public/dubbing-materials"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("movie-one"))
                .andExpect(jsonPath("$[0].parts[0].id").value("part-01"))
                .andExpect(jsonPath("$[0].parts[0].videoSrc").value(org.hamcrest.Matchers.containsString("?v=1")));
        mvc.perform(delete("/api/admin/dubbing-materials/" + id).param("version", Long.toString(materialVersion))
                        .with(user("operator").roles("ADMIN"))).andExpect(status().isConflict());
        long publishedPartVersion = published.path("parts").get(0).path("version").asLong();
        mvc.perform(multipart("/api/admin/dubbing-materials/" + id + "/parts/" + partId + "/video")
                        .file(new MockMultipartFile("file", "replacement.mp4", "video/mp4", new byte[12]))
                        .param("materialVersion", Long.toString(materialVersion))
                        .param("partVersion", Long.toString(publishedPartVersion))
                        .with(request -> { request.setMethod("PUT"); request.setContent(new byte[1]); return request; })
                        .with(user("operator").roles("ADMIN"))).andExpect(status().isConflict());

        JsonNode offline = node(mvc.perform(put("/api/admin/dubbing-materials/" + id + "/status")
                        .with(user("operator").roles("ADMIN")).contentType("application/json")
                        .content("{\"status\":\"OFFLINE\",\"materialVersion\":" + materialVersion + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OFFLINE")).andReturn());
        mvc.perform(get("/api/public/dubbing-materials/movie-one")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/dubbing-materials/" + id)
                        .param("version", offline.path("materialVersion").asText()).with(user("operator").roles("ADMIN")))
                .andExpect(status().isNoContent());
        assertTrue(audits.count() >= before + 6);
    }

    @Test void validatesPublicationVersionsAndAdminRole() throws Exception {
        mvc.perform(get("/api/admin/dubbing-materials")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dubbing-materials").with(user("learner").roles("USER"))).andExpect(status().isForbidden());
        JsonNode created = node(mvc.perform(post("/api/admin/dubbing-materials")
                        .with(user("operator").roles("ADMIN")).contentType("application/json").content(materialBody("incomplete", 0, "[]")))
                .andExpect(status().isOk()).andReturn());
        long id = created.path("id").asLong();
        mvc.perform(put("/api/admin/dubbing-materials/" + id + "/status")
                        .with(user("operator").roles("ADMIN")).contentType("application/json")
                        .content("{\"status\":\"PUBLISHED\",\"materialVersion\":" + created.path("materialVersion") + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/dubbing-materials/" + id)
                        .with(user("operator").roles("ADMIN")).contentType("application/json")
                        .content(materialBody("incomplete", 999, "[]"))).andExpect(status().isConflict());
    }

    private String materialBody(String code, long version, String parts) {
        return """
                {"code":"%s","title":"电影一","difficulty":"B1","tags":["家庭"],"sourceLabel":"Wikimedia Commons",
                 "sourceUrl":"https://commons.wikimedia.org/example","licenseLabel":"CC BY-SA 4.0",
                 "licenseUrl":"https://creativecommons.org/licenses/by-sa/4.0/","sortOrder":1,"materialVersion":%d,"parts":%s}
                """.formatted(code, version, parts);
    }

    private String partBody(long materialVersion) {
        return """
                {"code":"part-01","label":"Part 01","title":"相遇","sortOrder":1,"materialVersion":%d,
                 "cues":[{"code":"cue-01","startMs":0,"endMs":1500,"text":"你好呀"}]}
                """.formatted(materialVersion);
    }

    private JsonNode node(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
