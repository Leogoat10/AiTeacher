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

    @TableField("submitted_at")
    private LocalDateTime submittedAt;
}
