package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("assignment_analysis_snapshots")
public class AssignmentAnalysisSnapshotDto {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("teacher_id")
    private Integer teacherId;

    @TableField("course_code")
    private String courseCode;

    @TableField("assignment_id")
    private Integer assignmentId;

    @TableField("assignment_title")
    private String assignmentTitle;

    @TableField("overview_json")
    private String overviewJson;

    @TableField("distribution_json")
    private String distributionJson;

    @TableField("trend_json")
    private String trendJson;

    @TableField("weak_points_json")
    private String weakPointsJson;

    @TableField("student_profiles_json")
    private String studentProfilesJson;

    @TableField("ai_recommendation_json")
    private String aiRecommendationJson;

    @TableField("summary")
    private String summary;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
