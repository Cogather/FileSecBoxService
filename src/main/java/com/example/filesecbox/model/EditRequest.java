package com.example.filesecbox.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EditRequest {
    @JsonProperty("file_path")
    private String filePath;
    @JsonProperty("old_string")
    private String oldString;
    @JsonProperty("new_string")
    private String newString;
    @JsonProperty("expected_replacements")
    private int expectedReplacements;

    public EditRequest() {}

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getOldString() { return oldString; }
    public void setOldString(String oldString) { this.oldString = oldString; }
    public String getNewString() { return newString; }
    public void setNewString(String newString) { this.newString = newString; }
    public int getExpectedReplacements() { return expectedReplacements; }
    public void setExpectedReplacements(int expectedReplacements) { this.expectedReplacements = expectedReplacements; }
}
