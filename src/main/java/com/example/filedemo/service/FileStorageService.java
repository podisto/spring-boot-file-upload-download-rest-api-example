package com.example.filedemo.service;

import com.example.filedemo.exception.FileStorageException;
import com.example.filedemo.exception.MyFileNotFoundException;
import com.example.filedemo.payload.UploadFile;
import com.example.filedemo.payload.UploadFileResponse;
import com.example.filedemo.property.FileStorageProperties;
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
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FileStorageService {

    private final Path fileStorageLocation;
    private final UploadFileService uploadFileService;
    private final Integer maxSize;

    @Autowired
    public FileStorageService(FileStorageProperties fileStorageProperties, UploadFileService uploadFileService) {
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir()).toAbsolutePath().normalize();
        this.uploadFileService = uploadFileService;
        this.maxSize = Integer.valueOf(fileStorageProperties.getMaxSize());
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new FileStorageException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public UploadFileResponse storeFile(String msisdn, MultipartFile file) {
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        // Check if the file's name contains invalid characters
        if(fileName.contains("..")) {
            throw new FileStorageException("Sorry! Filename contains invalid path sequence " + fileName);
        }
        if (getExtensionByStringHandling(fileName).isPresent()) {
            try {
                String serverFileName = Calendar.getInstance().getTimeInMillis() + "." +this.getExtensionByStringHandling(fileName).get();
                // Copy file to the target location
                Path targetLocation = this.createMsisdnDirectory(msisdn).resolve(serverFileName);
                Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
                uploadFileService.save(this.buildUploadFile(file, serverFileName, targetLocation.toString()));
                return UploadFileResponse.builder().fileName(serverFileName).fileType(file.getContentType()).size(file.getSize()).build();
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

    public List<UploadFileResponse> uploadFiles(String msisdn, MultipartFile[] files) {
        log.info("maxSize: {}", this.maxSize);
        List<UploadFileResponse> responseList = Arrays.asList(Optional.ofNullable(files).orElse(new MultipartFile[0]))
                .stream()
                .map(file -> this.storeFile(msisdn, file))
                .collect(Collectors.toList());
        if (responseList.size() == this.maxSize) {
            // process zip folder zipUploadedFile()
            this.zipUploadedFile(msisdn);
        }
        return responseList;
    }

    private void zipUploadedFile(String msisdn) {
        log.info("Directory to zip: {}", this.createMsisdnDirectory(msisdn));
    }

    private UploadFile buildUploadFile(MultipartFile file, String serverFileName, String path) {
        UploadFile uploadFile = new UploadFile();
        uploadFile.setFileName(file.getOriginalFilename());
        uploadFile.setServerFilename(serverFileName);
        uploadFile.setPath(path);
        uploadFile.setFileType(file.getContentType());
        return uploadFile;
    }

    private Path createMsisdnDirectory(String msisdn) {
        try {
            String directoryName = this.fileStorageLocation.toString().concat("/").concat(msisdn);
            Path path = Paths.get(directoryName);
            log.info("Path: {}", path);
            if (!Files.exists(path)) {
                Files.createDirectory(path);
                log.info("Directory created");
            }
            return path;
        } catch (IOException e) {
            throw new FileStorageException("Exception {} ", e);
        }
    }
}
