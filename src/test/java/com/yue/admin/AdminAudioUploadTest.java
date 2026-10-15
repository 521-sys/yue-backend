package com.yue.admin;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.*;

class AdminAudioUploadTest extends AdminFlowTest {
 @org.springframework.beans.factory.annotation.Autowired AdminAudioService audioService;
 @org.springframework.beans.factory.annotation.Autowired com.yue.controller.AudioResourceController audioController;
 @org.springframework.beans.factory.annotation.Autowired com.yue.service.SceneService sceneService;
 @Test void privateScenesRejectDeletedOrInvalidAudioKeys() throws Exception {
  for(String key:new String[]{"upload:missing","../missing.wav"}) {
   var request=json.readValue(body("private-audio",0).replace("\"cantoneseText\"","\"audioKey\":\""+key+"\",\"cantoneseText\""),com.yue.dto.SceneCreateRequest.class);
   org.junit.jupiter.api.Assertions.assertThrows(AdminApiException.class,()->sceneService.create(123L,request));
  }
 }
 @Test @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 void readingResponseSurvivesCommittedReplacement() throws Exception {
  var item=audioService.upload("preview",new MockMultipartFile("file","old.wav","audio/wav",wav(1000)));
  long id=Long.parseLong(item.id().substring(7));
  var response=audioController.get(item.audioKey().substring(7));
  var newer=audioService.replace("preview",id,item.version(),new MockMultipartFile("file","new.wav","audio/wav",wav(2000)));
  try(var input=response.getBody().getInputStream()){org.junit.jupiter.api.Assertions.assertArrayEquals(wav(1000),input.readAllBytes());}
  finally{audioService.delete("preview",id,newer.version());}
 }
 static byte[] wav(int size){byte[] data=new byte[size];ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes()).putInt(size-8).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(size-44);return data;}
 @Test void uploadReplaceAndReferenceProtection() throws Exception {
  var response=mvc.perform(multipart("/api/admin/audio").file(new MockMultipartFile("file","../sample.wav","audio/wav",wav(3*1024*1024))).with(user("operator").roles("ADMIN")).with(r->{if(r.getContentType()!=null&&r.getContentType().startsWith("multipart/"))r.setContent(new byte[1]);return r;}))
   .andExpect(status().isOk()).andExpect(jsonPath("$.readOnly").value(false)).andReturn();
  var item=json.readTree(response.getResponse().getContentAsString());String id=item.path("id").asText().replace("upload:","");String key=item.path("audioKey").asText();
  mvc.perform(get(item.path("url").asText())).andExpect(status().isOk()).andExpect(content().contentType("audio/wav"));
  var scene=mvc.perform(post("/api/admin/scenes").with(user("operator").roles("ADMIN")).with(r->{if(r.getContentType()!=null&&r.getContentType().startsWith("multipart/"))r.setContent(new byte[1]);return r;}).contentType("application/json").content(body("audio-tea",0).replace("\"speaker\":\"A\"","\"audioKey\":\""+key+"\",\"speaker\":\"A\""))).andExpect(status().isOk()).andReturn();
  mvc.perform(delete("/api/admin/audio/"+id).param("version",item.path("version").asText()).with(user("operator").roles("ADMIN")).with(r->{if(r.getContentType()!=null&&r.getContentType().startsWith("multipart/"))r.setContent(new byte[1]);return r;})).andExpect(status().isConflict());
  mvc.perform(multipart("/api/admin/audio/"+id).file(new MockMultipartFile("file","replacement.wav","audio/wav",wav(1000))).param("version",item.path("version").asText()).with(r->{r.setMethod("PUT");return r;}).with(user("operator").roles("ADMIN")).with(r->{if(r.getContentType()!=null&&r.getContentType().startsWith("multipart/"))r.setContent(new byte[1]);return r;}))
   .andExpect(status().isOk()).andExpect(jsonPath("$.audioKey").value(key));
 }
 @Test void badAndOversizeAudioAreRejected() throws Exception {
  mvc.perform(multipart("/api/admin/audio").file(new MockMultipartFile("file","bad.mp3","audio/mpeg","not audio".getBytes())).with(user("operator").roles("ADMIN")).with(r->{if(r.getContentType()!=null&&r.getContentType().startsWith("multipart/"))r.setContent(new byte[1]);return r;})).andExpect(status().isUnsupportedMediaType());
  mvc.perform(multipart("/api/admin/audio").file(new MockMultipartFile("file","big.wav","audio/wav",new byte[20*1024*1024+1])).with(user("operator").roles("ADMIN")).with(r->{if(r.getContentType()!=null&&r.getContentType().startsWith("multipart/"))r.setContent(new byte[1]);return r;})).andExpect(status().isPayloadTooLarge());
 }
}
