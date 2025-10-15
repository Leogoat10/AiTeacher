package com.leo.aiteacher.pojo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.aiteacher.pojo.dto.StuDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StudentMapper extends BaseMapper<StuDto> {
    StuDto getStudentByName(@Param("studentName") String name);

    StuDto getStudentById(@Param("studentId") Integer id);

    int insertStudent(StuDto newStudent);

    List<StuDto> listByCourseCode(@Param("courseCode") String courseCode);
}