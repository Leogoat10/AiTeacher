package com.leo.aiteacher.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("messages")
public class MessageDto {
    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("conversation_id")
    private Integer conversationId;

    private String question;
    private String answer;

    @TableField("created_at")
    private LocalDateTime createdAt;

}