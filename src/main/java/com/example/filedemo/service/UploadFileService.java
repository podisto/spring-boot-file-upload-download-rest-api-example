package com.example.filedemo.service;

import com.example.filedemo.payload.UploadFile;
import com.example.filedemo.repository.UploadFileRepository;
import org.springframework.stereotype.Service;

@Service
public class UploadFileService {

    private final UploadFileRepository repository;


    public UploadFileService(UploadFileRepository repository) {
        this.repository = repository;
    }

    public UploadFile save(UploadFile uploadFile) {
        return repository.save(uploadFile);
    }
}
