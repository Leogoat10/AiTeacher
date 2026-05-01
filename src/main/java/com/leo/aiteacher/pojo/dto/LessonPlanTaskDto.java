package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lesson_plan_tasks")
public class LessonPlanTaskDto {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("teacher_id")
    private Integer teacherId;

    @TableField("conversation_id")
    private Integer conversationId;

    private String status;
    private String subject;
    private String grade;

    @TableField("teaching_topic")
    private String teachingTopic;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    @TableField("interaction_count")
    private Integer interactionCount;

    @TableField("context_used")
    private Boolean contextUsed;

    @TableField("context_rounds")
    private Integer contextRounds;

    @TableField("custom_requirement")
    private String customRequirement;

    @TableField("request_prompt")
    private String requestPrompt;

    @TableField("raw_response")
    private String rawResponse;

    @TableField("result_json")
    private String resultJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
