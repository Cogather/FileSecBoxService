package com.example.filesecbox.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 技能元数据对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillMetadata {
    private String name;
    private String description;
}

