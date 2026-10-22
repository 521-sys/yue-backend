package com.yue.dubbingmaterial;

import com.yue.entity.DubbingCue;
import com.yue.entity.DubbingMaterial;
import com.yue.entity.DubbingPart;
import com.yue.repository.DubbingCueRepository;
import com.yue.repository.DubbingMaterialRepository;
import com.yue.repository.DubbingPartRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dubbing_material_schema;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-only-0123456789012345678901234567890123456789"
})
@org.springframework.transaction.annotation.Transactional
class DubbingMaterialSchemaTest {
    @Autowired DubbingMaterialRepository materials;
    @Autowired DubbingPartRepository parts;
    @Autowired DubbingCueRepository cues;

    @Test
    void newMaterialsStartAsDraftWithVersionedOrdering() {
        var material = material("movie-one", 7);
        material = materials.saveAndFlush(material);

        assertEquals("DRAFT", material.getStatus());
        assertEquals(7, material.getSortOrder());
        assertEquals(0L, material.getVersion());
    }

    @Test
    void materialCodesAreUnique() {
        materials.saveAndFlush(material("movie-one", 0));
        assertThrows(DataIntegrityViolationException.class,
                () -> materials.saveAndFlush(material("movie-one", 1)));
    }

    @Test
    void partCodesAreUniqueWithinOneMaterialAndOrdered() {
        var first = materials.saveAndFlush(material("first", 0));
        var second = materials.saveAndFlush(material("second", 0));
        parts.saveAndFlush(part(first.getId(), "part-02", 20));
        parts.saveAndFlush(part(first.getId(), "part-01", 10));
        parts.saveAndFlush(part(second.getId(), "part-01", 10));

        assertEquals(
                java.util.List.of("part-01", "part-02"),
                parts.findByMaterialIdOrderBySortOrderAscIdAsc(first.getId()).stream().map(DubbingPart::getCode).toList()
        );
        assertThrows(DataIntegrityViolationException.class,
                () -> parts.saveAndFlush(part(first.getId(), "part-01", 30)));
    }

    @Test
    void cueCodesAreUniqueWithinOnePartAndOrdered() {
        var material = materials.saveAndFlush(material("movie", 0));
        var firstPart = parts.saveAndFlush(part(material.getId(), "part-01", 10));
        var secondPart = parts.saveAndFlush(part(material.getId(), "part-02", 20));
        cues.saveAndFlush(cue(firstPart.getId(), "cue-02", 2));
        cues.saveAndFlush(cue(firstPart.getId(), "cue-01", 1));
        cues.saveAndFlush(cue(secondPart.getId(), "cue-01", 1));

        assertEquals(
                java.util.List.of("cue-01", "cue-02"),
                cues.findByPartIdOrderBySequenceNoAscIdAsc(firstPart.getId()).stream().map(DubbingCue::getCode).toList()
        );
        assertThrows(DataIntegrityViolationException.class,
                () -> cues.saveAndFlush(cue(firstPart.getId(), "cue-01", 3)));
    }

    private static DubbingMaterial material(String code, int sortOrder) {
        return DubbingMaterial.builder()
                .code(code).title("测试素材").difficulty("B1").tagsJson("[]")
                .sourceLabel("测试来源").sourceUrl("https://example.com/source")
                .licenseLabel("CC BY 4.0").licenseUrl("https://example.com/license")
                .sortOrder(sortOrder).build();
    }

    private static DubbingPart part(long materialId, String code, int sortOrder) {
        return DubbingPart.builder()
                .materialId(materialId).code(code).label("Part").title("片段")
                .sortOrder(sortOrder).build();
    }

    private static DubbingCue cue(long partId, String code, int sequenceNo) {
        return DubbingCue.builder()
                .partId(partId).code(code).sequenceNo(sequenceNo)
                .startMs(0L).endMs(1000L).text("你好").build();
    }
}
