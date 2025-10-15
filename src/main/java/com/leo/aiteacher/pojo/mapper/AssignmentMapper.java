package com.leo.aiteacher.pojo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.aiteacher.pojo.dto.AssignmentDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AssignmentMapper extends BaseMapper<AssignmentDto> {
    
    @Select("SELECT * FROM assignments WHERE course_code = #{courseCode} ORDER BY created_at DESC")
    List<AssignmentDto> getByCourseCode(String courseCode);
    
    @Select("SELECT * FROM assignments WHERE teacher_id = #{teacherId} ORDER BY created_at DESC")
    List<AssignmentDto> getByTeacherId(Integer teacherId);
}
