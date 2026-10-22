package com.yue.dubbingmaterial;

import com.yue.entity.DubbingCue;
import com.yue.entity.DubbingMaterial;
import com.yue.entity.DubbingPart;
import com.yue.repository.DubbingCueRepository;
import com.yue.repository.DubbingMaterialRepository;
import com.yue.repository.DubbingPartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dubbing_video_tx;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-only-0123456789012345678901234567890123456789"
})
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
class DubbingVideoTransactionTest {
    @Autowired AdminDubbingMaterialService service;
    @Autowired DubbingMaterialRepository materials;
    @Autowired DubbingPartRepository parts;
    @Autowired DubbingCueRepository cues;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean DubbingVideoStorage storage;
    TransactionTemplate transactions;

    @BeforeEach void clean() {
        transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(status -> { cues.deleteAll(); parts.deleteAll(); materials.deleteAll(); });
        reset(storage);
    }

    @Test void rollbackDeletesTheNewFileAndKeepsTheOldReference() {
        Ids ids = fixture("old.mp4");
        when(storage.store(any())).thenReturn(stored("new.mp4", 5000));
        assertThrows(IntentionalRollback.class, () -> transactions.executeWithoutResult(status -> {
            service.upload("operator", ids.materialId, ids.partId, ids.materialVersion, ids.partVersion,
                    new MockMultipartFile("file", "new.mp4", "video/mp4", new byte[12]));
            throw new IntentionalRollback();
        }));

        verify(storage).remove("new.mp4");
        assertEquals("old.mp4", parts.findById(ids.partId).orElseThrow().getFileName());
        verify(storage, never()).remove("old.mp4");
    }

    @Test void cleanupFailureAfterCommitDoesNotUndoTheNewReference() {
        Ids ids = fixture("old.mp4");
        when(storage.store(any())).thenReturn(stored("new.mp4", 5000));
        doThrow(new RuntimeException("disk busy")).when(storage).remove("old.mp4");

        assertDoesNotThrow(() -> transactions.executeWithoutResult(status -> service.upload("operator",
                ids.materialId, ids.partId, ids.materialVersion, ids.partVersion,
                new MockMultipartFile("file", "new.mp4", "video/mp4", new byte[12]))));
        assertEquals("new.mp4", parts.findById(ids.partId).orElseThrow().getFileName());
        verify(storage).remove("old.mp4");
        verify(storage, never()).remove("new.mp4");
    }

    @Test void shorterReplacementIsRejectedAndOnlyTheNewFileIsCleaned() {
        Ids ids = fixture("old.mp4");
        when(storage.store(any())).thenReturn(stored("too-short.mp4", 500));

        var error = assertThrows(com.yue.admin.AdminApiException.class, () -> service.upload("operator",
                ids.materialId, ids.partId, ids.materialVersion, ids.partVersion,
                new MockMultipartFile("file", "short.mp4", "video/mp4", new byte[12])));
        assertEquals(400, error.status);
        verify(storage).remove("too-short.mp4");
        verify(storage, never()).remove("old.mp4");
        assertEquals("old.mp4", parts.findById(ids.partId).orElseThrow().getFileName());
    }

    private Ids fixture(String fileName) {
        return transactions.execute(status -> {
            var material = materials.saveAndFlush(DubbingMaterial.builder().code("movie-tx").title("事务")
                    .difficulty("B1").tagsJson("[]").sourceLabel("source").sourceUrl("https://example.com/source")
                    .licenseLabel("CC").licenseUrl("https://example.com/license").build());
            var part = DubbingPart.builder().materialId(material.getId()).code("part-01").label("Part 01").title("标题")
                    .fileName(fileName).videoKey("old-key").contentType("video/mp4").sizeBytes(1000L).durationMs(5000L)
                    .videoCodec("h264").audioCodec("aac").videoVersion(1).build();
            part = parts.saveAndFlush(part);
            cues.saveAndFlush(DubbingCue.builder().partId(part.getId()).code("cue-01").sequenceNo(1)
                    .startMs(0L).endMs(1000L).text("你好").build());
            return new Ids(material.getId(), part.getId(), material.getVersion(), part.getVersion());
        });
    }

    private DubbingVideoStorage.StoredVideo stored(String file, long duration) {
        return new DubbingVideoStorage.StoredVideo(file, "video/mp4", 1200, duration, "h264", "aac");
    }

    private record Ids(long materialId, long partId, long materialVersion, long partVersion) {}
    private static final class IntentionalRollback extends RuntimeException {}
}
