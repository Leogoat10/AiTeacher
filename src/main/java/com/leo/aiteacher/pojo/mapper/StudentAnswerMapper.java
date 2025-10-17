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
    
    @Select("SELECT sa.id, sa.assignment_id, sa.student_id, sa.student_answer, sa.ai_score, sa.ai_analysis, sa.submitted_at, " +
            "s.student_name, a.title as assignment_title, a.content as assignment_content " +
            "FROM student_answers sa " +
            "INNER JOIN students s ON sa.student_id = s.student_id " +
            "INNER JOIN assignments a ON sa.assignment_id = a.id " +
            "WHERE a.course_code = #{courseCode} " +
            "ORDER BY sa.submitted_at DESC")
    List<Map<String, Object>> getCourseStudentAnswers(String courseCode);
    
    @Select("SELECT s.student_id as studentId, s.student_name as studentName, " +
            "CAST(s.student_id AS CHAR) as studentNumber, " +
            "COUNT(CASE WHEN a.course_code = #{courseCode} THEN sa.id END) as answerCount " +
            "FROM students s " +
            "INNER JOIN course_students cs ON s.student_id = cs.student_id " +
            "LEFT JOIN student_answers sa ON s.student_id = sa.student_id " +
            "LEFT JOIN assignments a ON sa.assignment_id = a.id " +
            "WHERE cs.course_code = #{courseCode} " +
            "GROUP BY s.student_id, s.student_name " +
            "ORDER BY s.student_name")
    List<Map<String, Object>> getCourseStudents(String courseCode);
    
    @Select("SELECT sa.id, sa.assignment_id as assignmentId, sa.student_answer as studentAnswer, " +
            "sa.ai_score as aiScore, sa.ai_analysis as aiAnalysis, sa.submitted_at as submittedAt, " +
            "a.title as assignmentTitle, a.content as assignmentContent " +
            "FROM student_answers sa " +
            "INNER JOIN assignments a ON sa.assignment_id = a.id " +
            "WHERE sa.student_id = #{studentId} AND a.course_code = #{courseCode} " +
            "ORDER BY sa.submitted_at DESC")
    List<Map<String, Object>> getStudentAnswerHistory(Integer studentId, String courseCode);
    
    @Select("SELECT sa.id, sa.assignment_id, sa.student_id, sa.student_answer, sa.ai_score, sa.ai_analysis, sa.submitted_at, " +
            "s.student_name " +
            "FROM student_answers sa " +
            "INNER JOIN students s ON sa.student_id = s.student_id " +
            "WHERE sa.assignment_id = #{assignmentId} " +
            "ORDER BY sa.submitted_at DESC")
    List<Map<String, Object>> getAssignmentStudentAnswers(Integer assignmentId);
}
