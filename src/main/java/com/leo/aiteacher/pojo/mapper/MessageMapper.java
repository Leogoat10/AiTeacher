package com.leo.aiteacher.pojo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.aiteacher.pojo.dto.MessageDto;

import java.util.List;

public interface MessageMapper extends BaseMapper<MessageDto> {
    void insertMessage(MessageDto messageDto);

    List<MessageDto> getMessagesByConversationId(Integer conversationId);

}
