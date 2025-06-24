package com.springframework.controller;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DownloadController {

	    private static final String SERVER_URL = "http://localhost:8080/files/chunkWithMeta";
	    private static final int CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB
	    private static final int MAX_RETRIES = 3;
	    private static final File TEMP_DIR = new File(System.getProperty("java.io.tmpdir"), "chunk-download");
	    
	    
	    public static void main(String[] args) throws Exception {
	        File downloaded = downloadFile("your-bucket", "example.pdf");
	        System.out.println("📦 Final file downloaded at: " + downloaded.getAbsolutePath());
	    }

	    public static File downloadFile(String bucket, String key) throws Exception {
	        TEMP_DIR.mkdirs();
	        ObjectMapper mapper = new ObjectMapper();

	        int chunkIndex = 0;
	        String expectedChecksum = null;
	        String fileName = null;
	        long fileSize = -1;
	        boolean isLastChunk = false;

	        List<File> chunkFiles = new ArrayList<>();

	        while (!isLastChunk) {
	            File chunkFile = new File(TEMP_DIR, "chunk_" + chunkIndex + ".tmp");

	            // Resume support: Skip already downloaded chunks
	            if (chunkFile.exists()) {
	                System.out.println("⏩ Resuming: Chunk " + chunkIndex + " already downloaded.");
	                chunkFiles.add(chunkFile);
	                chunkIndex++;
	                continue;
	            }

	            int attempts = 0;
	            boolean success = false;

	            while (attempts < MAX_RETRIES && !success) {
	                try {
	                    Map<String, Object> body = new HashMap<>();
	                    body.put("bucket", bucket);
	                    body.put("key", key);
	                    body.put("chunkIndex", chunkIndex);

	                    HttpURLConnection conn = (HttpURLConnection) new URL(SERVER_URL).openConnection();
	                    conn.setRequestMethod("POST");
	                    conn.setDoOutput(true);
	                    conn.setRequestProperty("Content-Type", "application/json");

	                    try (OutputStream os = conn.getOutputStream()) {
	                        os.write(mapper.writeValueAsBytes(body));
	                    }

	                    if (conn.getResponseCode() != 200) {
	                        throw new IOException("Server responded with " + conn.getResponseCode());
	                    }

	                    if (chunkIndex == 0) {
	                        fileName = conn.getHeaderField("X-File-Name");
	                        fileSize = Long.parseLong(conn.getHeaderField("X-File-Size"));
	                        System.out.println("🟢 Downloading file: " + fileName + " | Size: " + fileSize);
	                    }

	                    try (InputStream is = conn.getInputStream();
	                         FileOutputStream fos = new FileOutputStream(chunkFile)) {
	                        byte[] buffer = is.readAllBytes();
	                        fos.write(buffer);
	                        System.out.println("✅ Chunk " + chunkIndex + " downloaded: " + buffer.length + " bytes");
	                        chunkFiles.add(chunkFile);
	                        success = true;
	                    }

	                    String checksumHeader = conn.getHeaderField("X-Expected-Checksum");
	                    if (checksumHeader != null) {
	                        expectedChecksum = checksumHeader;
	                        isLastChunk = true;
	                    }

	                } catch (IOException e) {
	                    attempts++;
	                    System.err.println("⚠️ Error downloading chunk " + chunkIndex + " (attempt " + attempts + "): " + e.getMessage());
	                    if (attempts == MAX_RETRIES) {
	                        throw new RuntimeException("❌ Failed to download chunk " + chunkIndex + " after " + MAX_RETRIES + " attempts");
	                    }
	                }
	            }

	            chunkIndex++;
	        }

	        // ✅ Sort and validate chunks before merge
	        chunkFiles.sort(Comparator.comparingInt(f -> extractChunkIndex(f.getName())));

	        for (int i = 0; i < chunkFiles.size(); i++) {
	            File chunk = chunkFiles.get(i);
	            if (!chunk.exists()) {
	                throw new IllegalStateException("❌ Missing chunk file: chunk_" + i + ".tmp");
	            }
	            if (extractChunkIndex(chunk.getName()) != i) {
	                throw new IllegalStateException("❌ Chunk order mismatch at index: " + i);
	            }
	        }

	        // ✅ Merge chunks into final file
	        File mergedFile = new File("downloaded_" + fileName);
	        try (FileOutputStream out = new FileOutputStream(mergedFile)) {
	            for (File chunk : chunkFiles) {
	                FileUtils.copyFile(chunk, out);
	            }
	        }

	        // Cleanup temporary chunk files
	        for (File chunk : chunkFiles) chunk.delete();

	        // ✅ Verify checksum
	        String actualChecksum = calculateChecksum(mergedFile);
	        System.out.println("🔍 Expected Checksum: " + expectedChecksum);
	        System.out.println("🔍 Actual Checksum:   " + actualChecksum);
	        System.out.println("✅ Checksum Match: " + actualChecksum.equalsIgnoreCase(expectedChecksum));

	        return mergedFile;
	    }

	    private static int extractChunkIndex(String fileName) {
	        try {
	            return Integer.parseInt(fileName.replace("chunk_", "").replace(".tmp", ""));
	        } catch (NumberFormatException e) {
	            throw new RuntimeException("Invalid chunk file name format: " + fileName);
	        }
	    }

	    private static String calculateChecksum(File file) throws Exception {
	        MessageDigest digest = MessageDigest.getInstance("SHA-256");
	        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
	            byte[] buffer = new byte[8192];
	            int read;
	            while ((read = is.read(buffer)) != -1) {
	                digest.update(buffer, 0, read);
	            }
	        }
	        StringBuilder hex = new StringBuilder();
	        for (byte b : digest.digest()) {
	            hex.append(String.format("%02x", b));
	        }
	        return hex.toString();
	    }

	   
	}



