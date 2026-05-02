package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_analysis_logs")
public class LearningAnalysisLogDto {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("teacher_id")
    private Integer teacherId;

    @TableField("course_code")
    private String courseCode;

    @TableField("assignment_id")
    private Integer assignmentId;

    @TableField("total_students")
    private Integer totalStudents;

    @TableField("total_answers")
    private Integer totalAnswers;

    @TableField("avg_score")
    private Double avgScore;

    @TableField("mastery_level")
    private String masteryLevel;

    @TableField("mastery_rate")
    private Double masteryRate;

    @TableField("risk_student_count")
    private Integer riskStudentCount;

    @TableField("knowledge_points_json")
    private String knowledgePointsJson;

    @TableField("student_snapshot_json")
    private String studentSnapshotJson;

    @TableField("analysis_summary")
    private String analysisSummary;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
