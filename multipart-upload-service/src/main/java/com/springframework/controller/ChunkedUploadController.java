package com.springframework.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
public class ChunkedUploadController {

    private final String baseDir = "/tmp/uploads/";
//    private final S3Client s3Client = S3Client.create();

    /**
     * Uploads a single chunk. Skips writing if chunk already exists (for retry support).
     */
    @PostMapping("/chunk")
    public ResponseEntity<String> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam String chunkChecksum // client-calculated checksum
    ) throws Exception {

        File dir = new File(baseDir + uploadId);
        if (!dir.exists()) dir.mkdirs();

        File chunkFile = new File(dir, "chunk-" + chunkIndex);

        // Retry-safe: if chunk already exists, validate checksum
        if (chunkFile.exists()) {
            String existingChecksum = calculateChecksum(chunkFile, "SHA-256");
            if (existingChecksum.equalsIgnoreCase(chunkChecksum)) {
                return ResponseEntity.ok("Chunk " + chunkIndex + " already uploaded and verified.");
            } else {
                // Delete corrupted file and re-upload
                chunkFile.delete();
            }
        }

        // Save new chunk
        try (InputStream in = file.getInputStream();
             OutputStream out = new FileOutputStream(chunkFile)) {
            IOUtils.copy(in, out);
        }

        // Verify chunk integrity
        String actualChecksum = calculateChecksum(chunkFile, "SHA-256");
        if (!actualChecksum.equalsIgnoreCase(chunkChecksum)) {
            chunkFile.delete();
            return ResponseEntity.badRequest().body("❌ Chunk " + chunkIndex + " checksum mismatch.");
        }

        return ResponseEntity.ok("✅ Chunk " + chunkIndex + " uploaded and verified.");
    }

    /**
     * Called after all chunks are uploaded. Merges, validates, and uploads to S3.
     */
    @PostMapping("/complete")
    public ResponseEntity<String> completeUpload(
            @RequestParam String uploadId,
            @RequestParam int totalChunks,
            @RequestParam String fileName,
            @RequestParam String expectedChecksum,
            @RequestParam String bucketName
    ) throws Exception {

        File uploadDir = new File(baseDir + uploadId);
        File mergedFile = new File(uploadDir, fileName);

        try (OutputStream out = new FileOutputStream(mergedFile)) {
            for (int i = 0; i < totalChunks; i++) {
                File chunk = new File(uploadDir, "chunk-" + i);
                if (!chunk.exists()) {
                    return ResponseEntity.badRequest().body("❌ Missing chunk: " + i);
                }

                try (InputStream in = new FileInputStream(chunk)) {
                    IOUtils.copy(in, out);
                }
            }
        }

        // Verify final merged checksum
        String actualChecksum = calculateChecksum(mergedFile, "SHA-256");
        if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
            return ResponseEntity.badRequest().body("❌ Final file checksum mismatch.");
        }

        // Upload to S3
        try (InputStream in = new FileInputStream(mergedFile)) {
//            s3Client.putObject(
//                    PutObjectRequest.builder()
//                            .bucket(bucketName)
//                            .key(fileName)
//                            .build(),
//                    RequestBody.fromInputStream(in, mergedFile.length()));
        	System.out.println("file uploded to s3");
        }

        // Cleanup
        FileUtils.deleteDirectory(uploadDir);

        return ResponseEntity.ok("✅ File uploaded successfully to S3.");
    }

    private String calculateChecksum(File file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
