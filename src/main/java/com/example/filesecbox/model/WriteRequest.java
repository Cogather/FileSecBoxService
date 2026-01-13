package com.example.filesecbox.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WriteRequest {
    @JsonProperty("file_path")
    private String filePath;
    private String content;
}

