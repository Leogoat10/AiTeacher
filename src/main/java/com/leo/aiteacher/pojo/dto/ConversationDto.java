package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;


import com.baomidou.mybatisplus.annotation.*;
import java.util.Date;

@TableName("conversations")
@Data
public class ConversationDto{

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("teacher_id")
    private Integer teacherId;

    private String title;

    @TableField("created_at")
    private LocalDateTime createdAt;

    private Date latestMessageUpdatedAt;

}
