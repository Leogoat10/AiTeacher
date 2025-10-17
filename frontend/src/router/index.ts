import { createRouter, createWebHistory } from 'vue-router'
import teacherLogin from "../views/teacher/login.vue";
import teacherPlan from "../views/teacher/plan.vue";
import TeacherQuestion from "../views/teacher/TeacherQuestion.vue";
import welcome from "../views/welcome.vue";
import teacherInfo from "../views/teacher/teacherInfo.vue";
import StuLogin from "../views/student/stuLogin.vue";
import StudentInfo from "../views/student/studentInfo.vue";
import teacherCourses from "../views/teacher/teacherCourses.vue";
import StudentAssignments from "../views/student/studentAssignments.vue";
import AnswerManagement from "../views/teacher/answerManagement.vue";
import StudentAnswerHistory from "../views/teacher/studentAnswerHistory.vue";


const routes = [
    {
        path: '/',
        name: 'welcome',
        component: welcome
    },
    {
        path: '/teacherLogin',
        name: 'teacherLogin',
        component: teacherLogin
    },
    {
        path: '/teacherInfo',
        name: 'teacherInfo',
        component: teacherInfo
    },
    {
        path: '/teacherPlan',
        name: 'teacherPlan',
        component: teacherPlan
    },
    {
        path: '/TeacherQuestion',
        name: 'TeacherQuestion',
        component: TeacherQuestion
    },
    {
        path: '/teacherCourses',
        name: 'teacherCourses',
        component: teacherCourses
    },
    {
        path: '/answerManagement',
        name: 'answerManagement',
        component: AnswerManagement
    },
    {
        path: '/studentAnswerHistory/:studentId/:courseCode',
        name: 'studentAnswerHistory',
        component: StudentAnswerHistory
    },
    {
        path: '/studentLogin',
        name: 'studentLogin',
        component: StuLogin
    },
    {
        path: '/studentInfo',
        name: 'studentInfo',
        component: StudentInfo
    },
    {
        path: '/studentAssignments',
        name: 'studentAssignments',
        component: StudentAssignments
    },

]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router