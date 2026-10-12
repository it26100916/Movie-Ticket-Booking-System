package com.moviebooking.file.controller;

import com.moviebooking.file.dto.FileDto.ImportResult;
import com.moviebooking.file.service.FileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

/** Admin file tools: import seed text files and download the sales report. */
@RestController
@RequestMapping("/api/admin/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/import-movies")
    public ImportResult importMovies() {
        return fileService.importMovies();
    }

    @PostMapping("/import-users")
    public ImportResult importUsers() {
        return fileService.importUsers();
    }

    @GetMapping("/export-sales")
    public ResponseEntity<Resource> exportSales() {
        Path report = fileService.exportSalesReport();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + report.getFileName() + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(new FileSystemResource(report));
    }
}
