package com.example.filesecbox.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件内容返回对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileContentResult {
    private Object content;
}

