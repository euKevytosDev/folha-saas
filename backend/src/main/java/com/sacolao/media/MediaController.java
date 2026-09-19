package com.sacolao.media;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
public class MediaController {

    private final CloudinaryMediaService mediaService;

    public MediaController(CloudinaryMediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/config")
    public MediaConfigResponse config() {
        return new MediaConfigResponse(mediaService.isEnabled(), mediaService.maxBytes());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MediaUploadResponse upload(@RequestPart("file") MultipartFile file) {
        return mediaService.upload(file);
    }
}
