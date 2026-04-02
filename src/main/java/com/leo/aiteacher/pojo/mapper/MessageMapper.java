package com.leo.aiteacher.pojo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.aiteacher.pojo.dto.MessageDto;

import java.util.List;

public interface MessageMapper extends BaseMapper<MessageDto> {
    List<MessageDto> getMessagesByConversationId(Integer conversationId);
    Integer countByConversationId(Integer conversationId);
}
