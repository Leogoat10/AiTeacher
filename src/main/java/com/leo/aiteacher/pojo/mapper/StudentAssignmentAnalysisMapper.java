package com.leo.aiteacher.pojo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.aiteacher.pojo.dto.StudentAssignmentAnalysisDto;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StudentAssignmentAnalysisMapper extends BaseMapper<StudentAssignmentAnalysisDto> {

    @Insert("INSERT INTO student_assignment_analyses " +
            "(teacher_id, course_code, assignment_id, student_id, answer_count, avg_score, preparedness_score, mastery_level, recommendation, analysis_json) " +
            "VALUES (#{teacherId}, #{courseCode}, #{assignmentId}, #{studentId}, #{answerCount}, #{avgScore}, #{preparednessScore}, #{masteryLevel}, #{recommendation}, #{analysisJson}) " +
            "ON DUPLICATE KEY UPDATE " +
            "teacher_id = VALUES(teacher_id), " +
            "course_code = VALUES(course_code), " +
            "answer_count = VALUES(answer_count), " +
            "avg_score = VALUES(avg_score), " +
            "preparedness_score = VALUES(preparedness_score), " +
            "mastery_level = VALUES(mastery_level), " +
            "recommendation = VALUES(recommendation), " +
            "analysis_json = VALUES(analysis_json), " +
            "updated_at = CURRENT_TIMESTAMP")
    int upsert(StudentAssignmentAnalysisDto dto);

    @Select("SELECT * FROM student_assignment_analyses WHERE assignment_id = #{assignmentId} AND student_id = #{studentId} LIMIT 1")
    StudentAssignmentAnalysisDto getByAssignmentAndStudent(@Param("assignmentId") Integer assignmentId,
                                                           @Param("studentId") Integer studentId);

    @Select("SELECT * FROM student_assignment_analyses " +
            "WHERE teacher_id = #{teacherId} AND course_code = #{courseCode} AND assignment_id = #{assignmentId} " +
            "ORDER BY preparedness_score ASC, updated_at DESC")
    List<StudentAssignmentAnalysisDto> listByCourseAndAssignment(@Param("teacherId") Integer teacherId,
                                                                 @Param("courseCode") String courseCode,
                                                                 @Param("assignmentId") Integer assignmentId);
}
