package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("generation_tasks")
public class GenerationTaskDto {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("teacher_id")
    private Integer teacherId;

    @TableField("conversation_id")
    private Integer conversationId;

    private String status;
    private String subject;
    private String grade;
    private String difficulty;

    @TableField("question_type")
    private String questionType;

    @TableField("question_count")
    private Integer questionCount;

    @TableField("custom_message")
    private String customMessage;

    @TableField("request_prompt")
    private String requestPrompt;

    @TableField("raw_response")
    private String rawResponse;

    @TableField("result_json")
    private String resultJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField("quality_passed")
    private Boolean qualityPassed;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
