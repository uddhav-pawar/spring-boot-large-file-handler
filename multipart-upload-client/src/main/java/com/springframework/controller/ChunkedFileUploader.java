package com.springframework.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class ChunkedFileUploader {

	private static final int CHUNK_SIZE = 5 * 1024 * 1024;
	private static final int MAX_RETRIES = 3;
	private final String serverUrl;

	public ChunkedFileUploader(String serverUrl) {
		this.serverUrl = serverUrl;
	}

	public void upload(File file, String bucketName) throws Exception {
		String uploadId = UUID.randomUUID().toString();
		int totalChunks = (int) Math.ceil((double) file.length() / CHUNK_SIZE);
		String fileChecksum = calculateChecksum(file);

		byte[] fileBytes = FileUtils.readFileToByteArray(file);
		for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
			boolean uploaded = false;
			int attempt = 0;

			while (!uploaded && attempt < MAX_RETRIES) {
				attempt++;

				int start = chunkIndex * CHUNK_SIZE;
				int end = Math.min(start + CHUNK_SIZE, fileBytes.length);
				byte[] chunkData = new byte[end - start];
				System.arraycopy(fileBytes, start, chunkData, 0, end - start);

				File chunkFile = File.createTempFile("chunk-" + chunkIndex, ".tmp");
				FileUtils.writeByteArrayToFile(chunkFile, chunkData);
				String chunkChecksum = calculateChecksum(chunkFile);

				uploaded = uploadChunk(uploadId, chunkIndex, chunkFile, chunkChecksum);
				chunkFile.delete();

				if (!uploaded && attempt < MAX_RETRIES) {
					System.out.println("Retrying chunk " + chunkIndex + " (attempt " + attempt + ")");
				}
			}

			if (!uploaded) {
				throw new IOException("Failed to upload chunk " + chunkIndex);
			}
		}

		completeUpload(uploadId, file.getName(), totalChunks, fileChecksum, bucketName);
	}

	private boolean uploadChunk(String uploadId, int chunkIndex, File chunkFile, String chunkChecksum) {
		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpPost upload = new HttpPost(serverUrl + "/chunk");

			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			builder.addPart("file", new FileBody(chunkFile));
			builder.addPart("uploadId", new StringBody(uploadId, ContentType.TEXT_PLAIN));
			builder.addPart("chunkIndex", new StringBody(String.valueOf(chunkIndex), ContentType.TEXT_PLAIN));
			builder.addPart("chunkChecksum", new StringBody(chunkChecksum, ContentType.TEXT_PLAIN));

			upload.setEntity(builder.build());
			HttpResponse response = httpClient.execute(upload);
			int status = response.getStatusLine().getStatusCode();
			String responseBody = EntityUtils.toString(response.getEntity());

			System.out.println("Chunk " + chunkIndex + " - " + status + ": " + responseBody);
			return status == 200;
		} catch (Exception e) {
			System.err.println("Error uploading chunk " + chunkIndex + ": " + e.getMessage());
			return false;
		}
	}

	private void completeUpload(String uploadId, String fileName, int totalChunks, String checksum, String bucket) {
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			HttpPost post = new HttpPost(serverUrl + "/complete");

			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			builder.addTextBody("uploadId", uploadId);
			builder.addTextBody("fileName", fileName);
			builder.addTextBody("totalChunks", String.valueOf(totalChunks));
			builder.addTextBody("expectedChecksum", checksum);
			builder.addTextBody("bucketName", bucket);

			post.setEntity(builder.build());
			CloseableHttpResponse response = client.execute(post);
			String responseBody = EntityUtils.toString(response.getEntity());
			System.out
					.println("Upload completed: " + response.getStatusLine().getStatusCode() + " - " + responseBody);
		} catch (Exception e) {
			System.err.println("Failed to complete upload: " + e.getMessage());
		}
	}

	private String calculateChecksum(File file) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream fis = new FileInputStream(file)) {
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = fis.read(buffer)) != -1) {
				digest.update(buffer, 0, bytesRead);
			}
		}

		StringBuilder hexString = new StringBuilder();
		for (byte b : digest.digest()) {
			hexString.append(String.format("%02x", b));
		}
		return hexString.toString();
	}
}
