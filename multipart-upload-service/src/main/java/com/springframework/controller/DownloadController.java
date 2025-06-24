package com.springframework.controller;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springframework.dto.ChunkDownloadRequest;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/files")
public class DownloadController {

	private static final String BASE_PATH = "D:\\Documents\\StudyMaterial\\course-presentation-master-spring-and-spring-boot.pdf"; // change
	private static final int CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB

	@PostMapping("/stream")
	public ResponseEntity<InputStreamResource> uploadAndStreamBack(@RequestParam("fileName") String fileName)
			throws FileNotFoundException {

		File file = new File(BASE_PATH + fileName);

		if (!file.exists() || file.isDirectory()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
		}

		InputStreamResource resource = new InputStreamResource(new BufferedInputStream(new FileInputStream(file)));

		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName())
				.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()))
				.contentType(MediaType.APPLICATION_OCTET_STREAM).body(resource);
	}

	@PostMapping("/chunkWithMeta")
	public void downloadChunkByIndex(@RequestBody ChunkDownloadRequest request, HttpServletResponse response) {
		try {
//			String bucket = request.getBucket();
			String key = request.getKey();
			int chunkIndex = request.getChunkIndex();
			long offset = (long) chunkIndex * CHUNK_SIZE;

			File localFile = new File(BASE_PATH);

			long fileSize = localFile.length();
			long remaining = fileSize - offset;
			long sizeToRead = Math.min(CHUNK_SIZE, remaining);

			try (RandomAccessFile raf = new RandomAccessFile(localFile, "r")) {
				raf.seek(offset);
				byte[] buffer = new byte[(int) sizeToRead];
				int bytesRead = raf.read(buffer);

				response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
				response.setContentLength(bytesRead);
				response.setHeader("X-File-Size", String.valueOf(fileSize));
				response.setHeader("X-File-Name", key);

				if (offset + sizeToRead >= fileSize) {
					String checksum = calculateChecksum(localFile);
					response.setHeader("X-Expected-Checksum", checksum);
				}

				response.getOutputStream().write(buffer, 0, bytesRead);
				response.getOutputStream().flush();
			}

		} catch (Exception e) {
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
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
