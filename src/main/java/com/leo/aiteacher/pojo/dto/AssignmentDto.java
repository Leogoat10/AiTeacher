package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("assignments")
public class AssignmentDto {
    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("message_id")
    private Integer messageId;

    @TableField("teacher_id")
    private Integer teacherId;

    @TableField("course_code")
    private String courseCode;

    private String title;
    private String content;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
