package org.example.controller;

import org.example.dto.PhotoUploadResultDto;
import org.example.service.PhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** POST /photos (step 1 of 2) + GET /photos/{id} (served at /api/photos via server.servlet.context-path) so the returned photoUrl actually resolves. See openapi.yaml. */
@RestController
@RequestMapping("/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoUploadResultDto> upload(@RequestPart("file") MultipartFile file) {
        PhotoUploadResultDto result = photoService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{photoId}")
    public ResponseEntity<byte[]> get(@PathVariable String photoId) {
        return photoService.get(photoId)
                .map(content -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(content.contentType()))
                        .body(content.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
