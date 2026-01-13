package com.example.filesecbox.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileContentResult {
    private String content;      // 字符串格式
    private List<String> lines;  // 列表格式
}
