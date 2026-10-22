package com.yue.dubbingmaterial;

import com.yue.entity.DubbingCue;
import com.yue.entity.DubbingMaterial;
import com.yue.entity.DubbingPart;
import com.yue.repository.DubbingCueRepository;
import com.yue.repository.DubbingMaterialRepository;
import com.yue.repository.DubbingPartRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dubbing_public;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-only-0123456789012345678901234567890123456789"
})
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class PublicDubbingMaterialTest {
    @Autowired MockMvc mvc;
    @Autowired DubbingMaterialRepository materials;
    @Autowired DubbingPartRepository parts;
    @Autowired DubbingCueRepository cues;
    @MockBean DubbingVideoStorage storage;

    @Test void exposesOnlyCompletePublishedContentAndStreamsItsVideo() throws Exception {
        fixture("published", "PUBLISHED", true, true);
        fixture("draft", "DRAFT", true, true);
        Path video = Files.createTempFile("public-dubbing-", ".mp4");
        Files.write(video, new byte[1000]);
        when(storage.path("published.mp4")).thenReturn(video);

        mvc.perform(get("/api/public/dubbing-materials"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("published"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("published.mp4"))));
        mvc.perform(get("/api/public/dubbing-materials/draft")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/dubbing-materials/draft/parts/part-01/video")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/dubbing-materials/published/parts/part-01/video").header("Range", "bytes=0-99"))
                .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range", "bytes 0-99/1000"));
    }

    @Test void failsTheWholePublicListWhenAnyPublishedRecordIsCorrupt() throws Exception {
        fixture("good", "PUBLISHED", true, true);
        fixture("missing-video", "PUBLISHED", false, true);
        mvc.perform(get("/api/public/dubbing-materials")).andExpect(status().is5xxServerError());
    }

    @Test void missingCuesAlsoFailsTheWholePublicList() throws Exception {
        fixture("missing-cues", "PUBLISHED", true, false);
        mvc.perform(get("/api/public/dubbing-materials")).andExpect(status().is5xxServerError());
    }

    private void fixture(String code, String status, boolean video, boolean cue) {
        var material = materials.saveAndFlush(DubbingMaterial.builder().code(code).title(code).difficulty("B1")
                .tagsJson("[\"家庭\"]").sourceLabel("source").sourceUrl("https://example.com/source")
                .licenseLabel("CC").licenseUrl("https://example.com/license").status(status).sortOrder(1).build());
        var part = DubbingPart.builder().materialId(material.getId()).code("part-01").label("Part 01").title("标题")
                .sortOrder(1).build();
        if (video) {
            part.setVideoKey("key-" + code); part.setOriginalName("part.mp4"); part.setFileName(code + ".mp4");
            part.setContentType("video/mp4"); part.setSizeBytes(1000L); part.setDurationMs(5000L);
            part.setVideoCodec("h264"); part.setAudioCodec("aac"); part.setVideoVersion(1);
        }
        part = parts.saveAndFlush(part);
        if (cue) cues.saveAndFlush(DubbingCue.builder().partId(part.getId()).code("cue-01").sequenceNo(1)
                .startMs(0L).endMs(1000L).text("你好").build());
    }
}
