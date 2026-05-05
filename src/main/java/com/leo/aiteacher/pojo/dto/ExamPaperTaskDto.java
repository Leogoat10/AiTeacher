package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_paper_tasks")
public class ExamPaperTaskDto {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("teacher_id")
    private Integer teacherId;

    private String status;
    private String subject;
    private String grade;

    @TableField("exam_type")
    private String examType;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    @TableField("total_score")
    private Integer totalScore;

    @TableField("question_count")
    private Integer questionCount;

    private String difficulty;

    @TableField("knowledge_points")
    private String knowledgePoints;

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
