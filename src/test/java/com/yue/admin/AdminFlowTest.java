package com.yue.admin;

import com.fasterxml.jackson.databind.*;
import com.yue.entity.*;
import com.yue.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:admin;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "app.jwt.secret=test-only-0123456789012345678901234567890123456789"})
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class AdminFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SceneRepository scenes;
    @Autowired SceneLineRepository lines;
    @Autowired UserRepository users;
    @Autowired AdminAuditLogRepository audit;
    String body(String code, long version) { return """
        {"code":"%s","title":"茶餐厅","place":"香港","roleA":"店员","roleB":"顾客","category":"日常生活","difficulty":"初级","sortOrder":0,"version":%d,
         "lines":[{"speaker":"A","cantoneseText":"食啲咩？","mandarinText":"吃什么？","jyutping":"sik6 di1 me1"},{"speaker":"B","cantoneseText":"唔该","mandarinText":"谢谢"}]}
        """.formatted(code,version); }
    @Test void publishEditOfflineAndDeleteAreAtomicAndVersioned() throws Exception {
        long auditBefore=audit.count();
        var create=mvc.perform(post("/api/admin/scenes").with(user("operator").roles("ADMIN")).contentType("application/json").content(body("tea",0)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.scene.status").value("DRAFT")).andReturn();
        var draft=json.readTree(create.getResponse().getContentAsString()).get("scene");
        long id=draft.get("id").asLong(), version=draft.get("version").asLong();
        mvc.perform(get("/api/public/scenes/"+id)).andExpect(status().isNotFound());
        var pub=mvc.perform(put("/api/admin/scenes/"+id+"/status").with(user("operator").roles("ADMIN")).contentType("application/json").content("{\"status\":\"PUBLISHED\",\"version\":"+version+"}"))
            .andExpect(status().isOk()).andReturn();
        long publishedVersion=json.readTree(pub.getResponse().getContentAsString()).path("scene").path("version").asLong();
        mvc.perform(get("/api/public/scenes/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].jyutping").value("sik6 di1 me1"));
        mvc.perform(put("/api/admin/scenes/"+id).with(user("operator").roles("ADMIN")).contentType("application/json").content(body("tea",version))).andExpect(status().isConflict());
        assertEquals(2,lines.findBySceneIdOrderBySequenceNoAsc(id).size());
        mvc.perform(delete("/api/admin/scenes/"+id).param("version",""+publishedVersion).with(user("operator").roles("ADMIN"))).andExpect(status().isConflict());
        var off=mvc.perform(put("/api/admin/scenes/"+id+"/status").with(user("operator").roles("ADMIN")).contentType("application/json").content("{\"status\":\"OFFLINE\",\"version\":"+publishedVersion+"}"))
            .andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/public/scenes/"+id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/scenes/"+id).param("version",json.readTree(off.getResponse().getContentAsString()).path("scene").path("version").asText()).with(user("operator").roles("ADMIN"))).andExpect(status().isNoContent());
        assertFalse(scenes.existsById(id)); assertTrue(lines.findBySceneIdOrderBySequenceNoAsc(id).isEmpty());
        mvc.perform(get("/api/admin/audit-logs").with(user("operator").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(auditBefore+4));
    }
    @Test void adminIsRequiredAndPublicNeverLeaksPrivateScenes() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").with(user("learner").roles("USER"))).andExpect(status().isForbidden());
        var s=scenes.save(Scene.builder().code("private").title("私人").place("家").roleA("A").roleB("B").ownerUserId(42L).builtin(false).build());
        mvc.perform(get("/api/public/scenes/"+s.getId())).andExpect(status().isNotFound());
    }
    @Test void userDetailsNeverExposeSecrets() throws Exception {
        var u=users.save(User.builder().username("learner").passwordHash("SECRET_HASH").phone("123").wxOpenid("SECRET_OPENID").build());
        mvc.perform(get("/api/admin/users/"+u.getId()).with(user("operator").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.phone").value("***"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
    }
}
