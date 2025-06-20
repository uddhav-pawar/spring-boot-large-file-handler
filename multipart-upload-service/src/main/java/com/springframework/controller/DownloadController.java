package com.springframework.controller;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/files")
public class DownloadController {

	private static final String BASE_PATH = "D:\\Documents\\StudyMaterial\\"; // change to your folder

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
}
