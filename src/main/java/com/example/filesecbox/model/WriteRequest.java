package com.example.filesecbox.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WriteRequest {
    @JsonProperty("file_path")
    private String filePath;
    private String content;

    public WriteRequest() {}

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
