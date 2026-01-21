package com.example.filesecbox.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExecutionResult {
    private String stdout;
    private String stderror;
    
    @JsonProperty("exit_code")
    private int exitCode;

    public ExecutionResult() {}

    public ExecutionResult(String stdout, String stderror, int exitCode) {
        this.stdout = stdout;
        this.stderror = stderror;
        this.exitCode = exitCode;
    }

    public String getStdout() { return stdout; }
    public void setStdout(String stdout) { this.stdout = stdout; }
    public String getStderror() { return stderror; }
    public void setStderror(String stderror) { this.stderror = stderror; }
    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) { this.exitCode = exitCode; }
}
