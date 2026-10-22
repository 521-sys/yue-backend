package com.yue.dubbingmaterial;

import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.yue.dubbingmaterial.DubbingMaterialDtos.PublicMaterial;

@RestController
@RequestMapping("/api/public/dubbing-materials")
public class PublicDubbingMaterialController {
    private final PublicDubbingMaterialService service;
    private final DubbingVideoResponse responses;

    public PublicDubbingMaterialController(PublicDubbingMaterialService service, DubbingVideoResponse responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping public List<PublicMaterial> list() { return service.list(); }
    @GetMapping("/{code}") public PublicMaterial get(@PathVariable String code) { return service.get(code); }

    @GetMapping("/{materialCode}/parts/{partCode}/video")
    public ResponseEntity<ResourceRegion> video(@PathVariable String materialCode, @PathVariable String partCode,
                                                 @RequestParam(required = false) Long v,
                                                 @RequestHeader HttpHeaders headers) {
        var video = service.video(materialCode, partCode);
        return responses.respond(video.path(), video.contentType(), video.etag(), headers);
    }
}
