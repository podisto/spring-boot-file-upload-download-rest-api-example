package com.example.filedemo.controller;

import com.example.filedemo.payload.UploadFileResponse;
import com.example.filedemo.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;

@RestController
@Slf4j
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/uploadFile/{msisdn}")
    public ResponseEntity<UploadFileResponse> uploadFile(@PathVariable("msisdn") String msisdn, @RequestParam("file") MultipartFile file) {
        if (!fileStorageService.getExtensionByStringHandling(file.getOriginalFilename()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }
        UploadFileResponse response = fileStorageService.storeFile(msisdn, file);
        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/downloadFile/")
                .path(response.getFileName())
                .toUriString();
        response.setFileDownloadUri(fileDownloadUri);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/uploadMultipleFiles/{msisdn}")
    public ResponseEntity<List<UploadFileResponse>> uploadMultipleFiles(@PathVariable("msisdn") String msisdn, @NotNull @RequestParam("files") MultipartFile[] files) {
        List<UploadFileResponse> responseList = fileStorageService.uploadFiles(msisdn, files);
        return ResponseEntity.ok(responseList);
    }

    @GetMapping("/downloadFile/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request) {
        // Load file as Resource
        Resource resource = fileStorageService.loadFileAsResource(fileName);

        // Try to determine file's content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            logger.info("Could not determine file type.");
        }

        // Fallback to the default content type if type could not be determined
        if(contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

}
