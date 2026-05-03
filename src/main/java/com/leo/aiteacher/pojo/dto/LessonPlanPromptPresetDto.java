package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lesson_plan_prompt_presets")
public class LessonPlanPromptPresetDto {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("teacher_id")
    private Integer teacherId;

    private String title;

    @TableField("prompt_content")
    private String promptContent;

    @TableField("is_system_default")
    private Boolean systemDefault;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
