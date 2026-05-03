package com.leo.aiteacher.pojo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.aiteacher.pojo.dto.TeacherDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TeacherMapper extends BaseMapper<TeacherDto> {
    TeacherDto getTeacherByName(String name);

    TeacherDto getTeacherById(String id);

    int insertTeacher(TeacherDto newTeacher);

    int updateTeacherPassword(@Param("teacherId") Integer teacherId, @Param("newPassword") String newPassword);
}
