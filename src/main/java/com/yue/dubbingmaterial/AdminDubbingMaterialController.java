package com.yue.dubbingmaterial;

import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

import static com.yue.dubbingmaterial.DubbingMaterialDtos.*;

@RestController
@RequestMapping("/api/admin/dubbing-materials")
public class AdminDubbingMaterialController {
    private final AdminDubbingMaterialService service;
    private final DubbingVideoResponse responses;

    public AdminDubbingMaterialController(AdminDubbingMaterialService service, DubbingVideoResponse responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping public Object list(@RequestParam(defaultValue = "") String q,
                                   @RequestParam(defaultValue = "") String status,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        return service.list(q, status, page, pageSize);
    }

    @GetMapping("/{id}") public MaterialDetail get(@PathVariable long id) { return service.get(id); }

    @PostMapping public MaterialDetail create(Principal principal, @RequestBody MaterialSaveRequest request) {
        return service.create(principal.getName(), request);
    }

    @PutMapping("/{id}") public MaterialDetail update(Principal principal, @PathVariable long id,
                                                       @RequestBody MaterialSaveRequest request) {
        return service.update(principal.getName(), id, request);
    }

    @PutMapping("/{id}/status") public MaterialDetail status(Principal principal, @PathVariable long id,
                                                              @RequestBody StatusRequest request) {
        return service.setStatus(principal.getName(), id, request);
    }

    @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable long id, @RequestParam Long version) {
        service.delete(principal.getName(), id, version);
    }

    @PostMapping("/{id}/parts") public MaterialDetail addPart(Principal principal, @PathVariable long id,
                                                               @RequestBody PartSaveInput request) {
        return service.addPart(principal.getName(), id, request);
    }

    @PostMapping("/{id}/parts/{partId}/video")
    public MaterialDetail upload(Principal principal, @PathVariable long id, @PathVariable long partId,
                                 @RequestParam Long materialVersion, @RequestParam Long partVersion,
                                 @RequestParam MultipartFile file) {
        return service.upload(principal.getName(), id, partId, materialVersion, partVersion, file);
    }

    @PutMapping("/{id}/parts/{partId}/video")
    public MaterialDetail replace(Principal principal, @PathVariable long id, @PathVariable long partId,
                                  @RequestParam Long materialVersion, @RequestParam Long partVersion,
                                  @RequestParam MultipartFile file) {
        return service.upload(principal.getName(), id, partId, materialVersion, partVersion, file);
    }

    @GetMapping("/{id}/parts/{partId}/video")
    public ResponseEntity<ResourceRegion> video(@PathVariable long id, @PathVariable long partId,
                                                 @RequestHeader HttpHeaders headers) {
        var video = service.video(id, partId);
        return responses.respond(video.path(), video.contentType(), video.etag(), headers);
    }
}
