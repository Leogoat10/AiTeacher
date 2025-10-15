package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student_assignments")
public class StudentAssignmentDto {
    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("assignment_id")
    private Integer assignmentId;

    @TableField("student_id")
    private Integer studentId;

    @TableField("received_at")
    private LocalDateTime receivedAt;

    @TableField("is_read")
    private Boolean isRead;
}
