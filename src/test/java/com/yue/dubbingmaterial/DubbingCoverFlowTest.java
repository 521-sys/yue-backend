package com.yue.dubbingmaterial;

import com.yue.entity.DubbingMaterial;
import com.yue.repository.DubbingMaterialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:cover_flow;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","app.jwt.secret=test-only-0123456789012345678901234567890123456789"})
@AutoConfigureMockMvc
class DubbingCoverFlowTest {
    @Autowired MockMvc mvc; @Autowired DubbingMaterialRepository materials;
    @Autowired AdminDubbingMaterialService service; @Autowired PlatformTransactionManager tm;
    @MockBean DubbingCoverStorage storage;
    DubbingMaterial fixture(String code){return materials.saveAndFlush(DubbingMaterial.builder().code(code).title(code).difficulty("B1").tagsJson("[]").sourceLabel("source").sourceUrl("https://example.com/s").licenseLabel("CC").licenseUrl("https://example.com/l").build());}
    @Test void coverUploadIsVersionedAndPrivateUntilPublished() throws Exception {
        var m=fixture("cover-http");var path=Files.createTempFile("cover-", ".png");Files.write(path,DubbingCoverStorageTest.image("png",16,9));
        when(storage.store(any())).thenReturn(new DubbingCoverStorage.StoredCover("cover.png","image/png",100,16,9));when(storage.path("cover.png")).thenReturn(path);
        try{
            mvc.perform(multipart("/api/admin/dubbing-materials/"+m.getId()+"/cover").file(new MockMultipartFile("file","cover.png","image/png",new byte[]{1})).param("materialVersion",m.getVersion().toString()).with(user("op").roles("ADMIN")).with(r->{r.setContent(new byte[1]);return r;}))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cover.width").value(16)).andExpect(jsonPath("$.cover.coverVersion").value(1));
            mvc.perform(get("/api/admin/dubbing-materials/"+m.getId()+"/cover")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/public/dubbing-materials/cover-http/cover")).andExpect(status().isNotFound());
            m=materials.findById(m.getId()).orElseThrow();m.setStatus("PUBLISHED");materials.saveAndFlush(m);
            var result=mvc.perform(get("/api/public/dubbing-materials/cover-http/cover")).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().exists("ETag")).andReturn();
            mvc.perform(get("/api/public/dubbing-materials/cover-http/cover").header("If-None-Match",result.getResponse().getHeader("ETag"))).andExpect(status().isNotModified());
            long id=m.getId();Long version=m.getVersion();assertThrows(com.yue.admin.AdminApiException.class,()->service.uploadCover("op",id,version,new MockMultipartFile("file",new byte[]{1})));
        }finally{Files.deleteIfExists(path);materials.deleteById(m.getId());}
    }
    @Test void rollbackKeepsOldCoverAndCommitCleansOnlyOldCover(){
        var m=fixture("cover-tx");m.setCoverFileName("old.png");m=materials.saveAndFlush(m);long id=m.getId();Long version=m.getVersion();
        when(storage.store(any())).thenReturn(new DubbingCoverStorage.StoredCover("new.png","image/png",100,16,9));
        var tx=new TransactionTemplate(tm);
        tx.executeWithoutResult(s->{service.uploadCover("op",id,version,new MockMultipartFile("file",new byte[]{1}));s.setRollbackOnly();});
        assertEquals("old.png",materials.findById(id).orElseThrow().getCoverFileName());verify(storage).remove("new.png");verify(storage,never()).remove("old.png");
        clearInvocations(storage);service.uploadCover("op",id,version,new MockMultipartFile("file",new byte[]{1}));
        verify(storage).remove("old.png");verify(storage,never()).remove("new.png");
        assertThrows(com.yue.admin.AdminApiException.class,()->service.uploadCover("op",id,version,new MockMultipartFile("file",new byte[]{1})));
        materials.deleteById(id);
    }
}
