package com.example.filesecbox.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 命令执行结果对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionResult {
    private String stdout;
    private String stderror;
    
    @JsonProperty("exit_code")
    private int exitCode;
}
