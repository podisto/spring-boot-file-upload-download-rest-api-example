package com.example.filedemo.service;

import com.example.filedemo.exception.FileStorageException;
import com.example.filedemo.exception.MyFileNotFoundException;
import com.example.filedemo.payload.UploadFile;
import com.example.filedemo.property.FileStorageProperties;
import com.example.filedemo.repository.UploadFileRepository;
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
import java.util.Calendar;
import java.util.Optional;

@Service
@Slf4j
public class FileStorageService {

    private final Path fileStorageLocation;
    private final UploadFileService uploadFileService;

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

    public String storeFile(MultipartFile file) {
        // Normalize file name
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        // Check if the file's name contains invalid characters
        if(fileName.contains("..")) {
            throw new FileStorageException("Sorry! Filename contains invalid path sequence " + fileName);
        }
        if (getExtensionByStringHandling(fileName).isPresent()) {
            try {
                String serverFileName = Calendar.getInstance().getTimeInMillis() + "." +this.getExtensionByStringHandling(fileName).get();
                // Copy file to the target location (Replacing existing file with the same name)
                Path targetLocation = this.fileStorageLocation.resolve(serverFileName);
                log.info("TargetLocation: {}", targetLocation);
                Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
                uploadFileService.save(this.buildUploadFile(file, serverFileName, targetLocation.toString()));
                return serverFileName;
            } catch (IOException ex) {
                throw new FileStorageException("Could not store file " + fileName + ". Please try again!", ex);
            }
        } else {
            throw new FileStorageException("Sorry! Filename does not contains valid extension" + fileName);
        }
    }

    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if(resource.exists()) {
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
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    private UploadFile buildUploadFile(MultipartFile file, String serverFileName, String path) {
        UploadFile uploadFile = new UploadFile();
        uploadFile.setFileName(file.getOriginalFilename());
        uploadFile.setServerFilename(serverFileName);
        uploadFile.setPath(path);
        uploadFile.setFileType(file.getContentType());
        return uploadFile;
    }
}
