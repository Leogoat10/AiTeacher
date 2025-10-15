package com.leo.aiteacher.pojo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.aiteacher.pojo.dto.CourseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseMapper extends BaseMapper<CourseDto> {
    List<CourseDto> getCoursesByTeacherId(@Param("teacherId") Integer teacherId);

    int addCourse(CourseDto courseDto);

    int removeCourse(@Param("courseCode") String courseCode, @Param("teacherId") Integer teacherId);

    CourseDto findByCourseCode(@Param("courseCode") String courseCode);

    Integer countByCourseCodeAndTeacherId(@Param("courseCode") String courseCode,
                                          @Param("teacherId") Integer teacherId);

}