package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student_assignment_analyses")
public class StudentAssignmentAnalysisDto {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("teacher_id")
    private Integer teacherId;

    @TableField("course_code")
    private String courseCode;

    @TableField("assignment_id")
    private Integer assignmentId;

    @TableField("student_id")
    private Integer studentId;

    @TableField("answer_count")
    private Integer answerCount;

    @TableField("avg_score")
    private Double avgScore;

    @TableField("preparedness_score")
    private Double preparednessScore;

    @TableField("mastery_level")
    private String masteryLevel;

    @TableField("recommendation")
    private String recommendation;

    @TableField("analysis_json")
    private String analysisJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
