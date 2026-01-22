package com.example.filesecbox.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkillMetadata {
    private String name;
    private String description;
    private String status; // UNCHANGED, MODIFIED, NEW, OUT_OF_SYNC, DELETED
    private String lastSyncTime;

    public SkillMetadata() {}

    public SkillMetadata(String name, String description, String status, String lastSyncTime) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.lastSyncTime = lastSyncTime;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(String lastSyncTime) { this.lastSyncTime = lastSyncTime; }
}
