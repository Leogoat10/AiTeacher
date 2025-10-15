package com.leo.aiteacher.service.impl;

import com.leo.aiteacher.pojo.dto.CourseDto;
import com.leo.aiteacher.pojo.mapper.CourseMapper;
import com.leo.aiteacher.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseServiceImpl implements CourseService {

    @Autowired
    private CourseMapper courseMapper;

    /**
     * 根据教师ID获取课程列表
     * @param teacherId 教师ID
     * @return 课程列表
     */
    @Override
    public List<CourseDto> getCoursesByTeacherId(Integer teacherId) {
        return courseMapper.getCoursesByTeacherId(teacherId);
    }

    /**
     * 添加课程
     * @param courseDto 课程信息
     * @param teacherId 教师ID（字符串形式）
     * @return 添加是否成功
     */
    @Override
    public boolean addCourse(CourseDto courseDto, Integer teacherId) {
        if (courseDto == null || teacherId == null) {
            return false;
        }
        try {
            courseDto.setTeacherId(teacherId);
        } catch (NumberFormatException e) {
            return false;
        }
        // 调用 mapper 插入，mapper 方法应返回受影响行数
        int rows = courseMapper.addCourse(courseDto);
        return rows > 0;
    }

    /**
     * 删除课程（未实现）
     * @param courseCode 课程代码
     * @param teacherId 教师ID
     * @return 删除是否成功
     */
    @Override
    public boolean removeCourse(String courseCode, Integer teacherId) {
        if (courseCode == null || courseCode.isEmpty() || teacherId == null) {
            return false;
        }
        Integer tId;
        try {
            tId = teacherId;
        } catch (NumberFormatException e) {
            return false;
        }
        try {
            int rows = courseMapper.removeCourse(courseCode, tId);
            return rows > 0;
        } catch (Exception e) {
            return false;
        }
    }

}