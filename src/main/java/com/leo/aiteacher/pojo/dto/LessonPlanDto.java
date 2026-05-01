package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lesson_plans")
public class LessonPlanDto {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("teacher_id")
    private Integer teacherId;

    private String subject;
    private String grade;

    @TableField("teaching_topic")
    private String teachingTopic;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    @TableField("interaction_count")
    private Integer interactionCount;

    private String title;
    private String overview;

    @TableField("objectives_json")
    private String objectivesJson;

    @TableField("key_points_json")
    private String keyPointsJson;

    @TableField("difficulty_points_json")
    private String difficultyPointsJson;

    @TableField("teaching_process_json")
    private String teachingProcessJson;

    private String homework;
    private String assessment;
    private String extensions;

    @TableField("markdown_content")
    private String markdownContent;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
