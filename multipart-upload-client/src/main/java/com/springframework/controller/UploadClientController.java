package com.springframework.controller;

import java.io.File;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/client-uploader")
public class UploadClientController {

    private final String defaultDmsUrl = "http://localhost:8080/api/upload";

    @PostMapping("/upload-multipart")
    public ResponseEntity<String> uploadMultipartFileToDMS(
            @RequestParam("file") MultipartFile multipartFile,
            @RequestParam("bucketName") String bucketName,
            @RequestParam(value = "dmsUrl", required = false) String dmsUrl
    ) {
        try {
            if (dmsUrl == null || dmsUrl.isEmpty()) {
                dmsUrl = defaultDmsUrl;
            }

            // Convert MultipartFile to temp file
            File tempFile = File.createTempFile("upload-", multipartFile.getOriginalFilename());
            multipartFile.transferTo(tempFile);

            // Trigger upload using your uploader
            ChunkedFileUploader uploader = new ChunkedFileUploader(dmsUrl);
            uploader.upload(tempFile, bucketName);

            // Clean up	
            tempFile.delete();

            return ResponseEntity.ok("File uploaded successfully to DMS via multipart.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload: " + e.getMessage());
        }
    }
}

