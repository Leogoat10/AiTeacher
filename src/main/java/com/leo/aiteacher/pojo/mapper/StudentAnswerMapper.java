package com.leo.aiteacher.pojo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.aiteacher.pojo.dto.StudentAnswerDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface StudentAnswerMapper extends BaseMapper<StudentAnswerDto> {
    
    @Select("SELECT sa.*, a.title, a.content " +
            "FROM student_answers sa " +
            "INNER JOIN assignments a ON sa.assignment_id = a.id " +
            "WHERE sa.student_id = #{studentId} " +
            "ORDER BY sa.submitted_at DESC")
    List<Map<String, Object>> getStudentAnswersWithDetails(Integer studentId);

    @Select("SELECT * FROM student_answers WHERE assignment_id = #{assignmentId} AND student_id = #{studentId}")
    StudentAnswerDto getByAssignmentAndStudent(Integer assignmentId, Integer studentId);
}
