package com.moviebooking.file.dto;

import java.util.List;

/** Summary returned after importing a seed file. */
public final class FileDto {

    private FileDto() {
    }

    public record ImportResult(String file, int imported, int skipped, List<String> errors) {
    }
}
