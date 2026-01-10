package com.example.filesecbox.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传响应对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadResponse {
    private String skillName;
    private String uploadPath;
    private String status;
}

