// ConversationMapper.java
package com.leo.aiteacher.pojo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.aiteacher.pojo.dto.ConversationDto;

import java.util.List;

public interface ConversationMapper extends BaseMapper<ConversationDto> {
    void insertConversation(ConversationDto conversationDto);

    ConversationDto getConversationById(Integer actualConversationId);

    List<ConversationDto> getConversationsByTeacherId(Integer teacherId);

    List<ConversationDto> getConversationsByTeacherIdWithLatestMessage(Integer teacherId);

    void deleteConversationById(Integer conversationId);
}
