package com.example.filedemo.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public class Utils {

    private Utils() {}

    public static void zip(String sourceDirectoryPath, String zipPath) throws IOException {
        log.info("sourceDirectoryPath: {}, zipPath: {}", sourceDirectoryPath, zipPath);
        Path p = Files.createFile(Paths.get(zipPath));
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p))) {
            Path pp = Paths.get(sourceDirectoryPath);
            Files.walk(pp)
                    .filter(path -> !path.toFile().isDirectory())
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(pp.relativize(path).toString());
                        try {
                            zs.putNextEntry(zipEntry);
                            Files.copy(path, zs);
                            zs.closeEntry();
                        } catch (IOException e) {
                            log.error("IOException error", e);
                        }
                    });
        }
    }
}
