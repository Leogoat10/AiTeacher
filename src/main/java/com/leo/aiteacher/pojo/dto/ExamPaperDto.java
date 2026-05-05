package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_papers")
public class ExamPaperDto {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("teacher_id")
    private Integer teacherId;

    private String title;
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

    private String summary;

    @TableField("structure_json")
    private String structureJson;

    @TableField("markdown_content")
    private String markdownContent;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
