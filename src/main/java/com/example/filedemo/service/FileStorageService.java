package com.example.filedemo.service;

import com.example.filedemo.exception.FileStorageException;
import com.example.filedemo.exception.MyFileNotFoundException;
import com.example.filedemo.payload.UploadFile;
import com.example.filedemo.payload.UploadFileResponse;
import com.example.filedemo.property.FileStorageProperties;
import com.example.filedemo.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FileStorageService {

    private final Path fileStorageLocation;
    private final UploadFileService uploadFileService;
    private static final String EXTENSION = ".zip";

    @Autowired
    public FileStorageService(FileStorageProperties fileStorageProperties, UploadFileService uploadFileService) {
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir()).toAbsolutePath().normalize();
        this.uploadFileService = uploadFileService;
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new FileStorageException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public UploadFileResponse storeFile(String msisdn, MultipartFile file) {
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        // Check if the file's name contains invalid characters
        if (fileName.contains("..")) {
            throw new FileStorageException("Sorry! Filename contains invalid path sequence " + fileName);
        }
        String extension = this.getExtensionByStringHandling(fileName).orElseThrow(() -> new FileStorageException("Sorry! Filename does not contains valid extension " + fileName));
        try {
            String serverFileName = Calendar.getInstance().getTimeInMillis() + "." + extension;
            // Copy file to the target location
            Path targetLocation = Paths.get(this.createMsisdnDirectory(msisdn)).resolve(serverFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            uploadFileService.save(this.buildUploadFile(file, serverFileName, targetLocation.toString()));
            return UploadFileResponse.builder().fileName(serverFileName).fileType(file.getContentType()).size(file.getSize()).build();
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                throw new MyFileNotFoundException("File not found " + fileName);
            }
        } catch (MalformedURLException ex) {
            throw new MyFileNotFoundException("File not found " + fileName, ex);
        }
    }

    public Optional<String> getExtensionByStringHandling(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf('.') + 1));
    }

    public Set<UploadFileResponse> uploadFiles(String msisdn, MultipartFile[] files) {
       return Arrays.asList(Optional.ofNullable(files).orElse(new MultipartFile[0]))
                .stream()
                .map(file -> this.storeFile(msisdn, file))
                .collect(Collectors.toSet());
    }

    public void zip(String msisdn) throws IOException {
        String sourceDirectoryPath = this.createMsisdnDirectory(msisdn);
        String zipPath = sourceDirectoryPath.concat(EXTENSION);
        Utils.zip(sourceDirectoryPath, zipPath);
        log.info("file zipped successfully!");
    }

    private UploadFile buildUploadFile(MultipartFile file, String serverFileName, String path) {
        UploadFile uploadFile = new UploadFile();
        uploadFile.setFileName(file.getOriginalFilename());
        uploadFile.setServerFilename(serverFileName);
        uploadFile.setPath(path);
        uploadFile.setFileType(file.getContentType());
        return uploadFile;
    }

    private String createMsisdnDirectory(String msisdn) {
        try {
            String directoryName = this.fileStorageLocation.toString().concat("/").concat(msisdn);
            Path path = Paths.get(directoryName);
            if (!path.toFile().exists()) {
                Files.createDirectory(path);
                log.info("Directory created");
            }
            return path.toString();
        } catch (IOException e) {
            throw new FileStorageException("Exception {} ", e);
        }
    }
}
