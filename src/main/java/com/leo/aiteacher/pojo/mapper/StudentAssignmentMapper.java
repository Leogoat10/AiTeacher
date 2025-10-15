package com.leo.aiteacher.pojo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.aiteacher.pojo.dto.StudentAssignmentDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface StudentAssignmentMapper extends BaseMapper<StudentAssignmentDto> {
    
    @Select("SELECT sa.*, a.title, a.content, a.created_at as assignment_created_at, " +
            "c.course_name, t.teacher_name " +
            "FROM student_assignments sa " +
            "INNER JOIN assignments a ON sa.assignment_id = a.id " +
            "INNER JOIN courses c ON a.course_code = c.course_code " +
            "INNER JOIN teachers t ON a.teacher_id = t.teacher_id " +
            "WHERE sa.student_id = #{studentId} " +
            "ORDER BY sa.received_at DESC")
    List<Map<String, Object>> getStudentAssignmentsWithDetails(Integer studentId);
}
