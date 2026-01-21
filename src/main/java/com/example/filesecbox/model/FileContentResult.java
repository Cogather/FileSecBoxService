package com.example.filesecbox.model;

import java.util.List;

public class FileContentResult {
    private String content;
    private List<String> lines;

    public FileContentResult() {}

    public FileContentResult(String content, List<String> lines) {
        this.content = content;
        this.lines = lines;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<String> getLines() { return lines; }
    public void setLines(List<String> lines) { this.lines = lines; }
}
