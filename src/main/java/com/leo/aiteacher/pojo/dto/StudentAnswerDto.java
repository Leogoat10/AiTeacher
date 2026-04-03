package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student_answers")
public class StudentAnswerDto {
    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("assignment_id")
    private Integer assignmentId;

    @TableField("student_id")
    private Integer studentId;

    @TableField("student_answer")
    private String studentAnswer;

    @TableField("ai_score")
    private String aiScore;

    @TableField("ai_analysis")
    private String aiAnalysis;

    @TableField("grading_status")
    private String gradingStatus;

    @TableField("grading_error")
    private String gradingError;

    @TableField("model_name")
    private String modelName;

    @TableField("prompt_version")
    private String promptVersion;

    @TableField("raw_response")
    private String rawResponse;

    @TableField("evaluation_json")
    private String evaluationJson;

    @TableField("grading_started_at")
    private LocalDateTime gradingStartedAt;

    @TableField("grading_completed_at")
    private LocalDateTime gradingCompletedAt;

    @TableField("submitted_at")
    private LocalDateTime submittedAt;
}
