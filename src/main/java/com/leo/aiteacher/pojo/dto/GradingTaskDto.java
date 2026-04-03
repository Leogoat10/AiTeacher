package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("grading_tasks")
public class GradingTaskDto {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("answer_id")
    private Integer answerId;

    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    @TableField("last_error")
    private String lastError;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
