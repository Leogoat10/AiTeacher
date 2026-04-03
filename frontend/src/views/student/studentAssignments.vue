<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { useRouter, useRoute } from 'vue-router'
import { Clock, User, School, Edit, Check } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

interface Assignment {
  id: number
  assignment_id: number
  title: string
  content: string
  course_name: string
  teacher_name: string
  assignment_created_at: string
  received_at: string
  is_read: boolean
  student_answer?: string
  ai_score?: string
  ai_analysis?: string
  grading_status?: string
  grading_error?: string
  submitted_at?: string
}

const assignments = ref<Assignment[]>([])
const loading = ref(false)
let statusPollTimer: number | null = null

// 检查是否已答题
const isAnswered = (assignment: Assignment): boolean => {
  return !!assignment.student_answer
}

// 获取状态标签
const getStatusTag = (assignment: Assignment) => {
  if (!isAnswered(assignment)) {
    return { type: 'warning', text: '待答题' }
  }
  if (assignment.grading_status === 'PENDING' || assignment.grading_status === 'RUNNING') {
    return { type: 'info', text: '批改中' }
  }
  if (assignment.grading_status === 'FAILED') {
    return { type: 'danger', text: '批改失败' }
  }
  if (assignment.ai_score) {
    return { type: 'success', text: '已答题' }
  }
  return { type: 'info', text: '已提交' }
}

// 加载题目列表
const loadAssignments = async () => {
  loading.value = true
  try {
    const res = await apiClient.get('/student/assignments')
    if (res.data) {
      assignments.value = res.data
      manageStatusPolling()
    }
  } catch (err: any) {
    console.error('加载题目失败:', err)
    if (err.response?.status === 401) {
      ElMessage.error('未登录或会话已过期，请先登录')
      router.push('/studentLogin')
    } else {
      ElMessage.error('加载题目失败')
    }
  } finally {
    loading.value = false
  }
}

const pollPendingStatuses = async () => {
  const pendingAssignments = assignments.value.filter(
    (a) => a.student_answer && (a.grading_status === 'PENDING' || a.grading_status === 'RUNNING' || !a.grading_status)
  )

  if (pendingAssignments.length === 0) {
    stopStatusPolling()
    return
  }

  for (const item of pendingAssignments) {
    try {
      const res = await apiClient.get(`/student/answer/${item.assignment_id}/status`)
      if (res.data?.success) {
        item.grading_status = res.data.gradingStatus
        item.ai_score = res.data.score
        item.ai_analysis = res.data.analysis
        item.grading_error = res.data.gradingError
      }
    } catch (err) {
      console.error('轮询判题状态失败:', err)
    }
  }
}

const stopStatusPolling = () => {
  if (statusPollTimer !== null) {
    window.clearInterval(statusPollTimer)
    statusPollTimer = null
  }
}

const manageStatusPolling = () => {
  const hasPending = assignments.value.some(
    (a) => a.student_answer && (a.grading_status === 'PENDING' || a.grading_status === 'RUNNING' || !a.grading_status)
  )

  if (!hasPending) {
    stopStatusPolling()
    return
  }

  if (statusPollTimer === null) {
    statusPollTimer = window.setInterval(() => {
      pollPendingStatuses()
    }, 5000)
  }
}

// 查看题目详情
const viewDetail = (assignment: Assignment) => {
  router.push(`/studentAssignmentDetail/${assignment.assignment_id}`)
}

// 开始答题
const startAnswer = (assignment: Assignment) => {
  router.push(`/studentAnswer/${assignment.assignment_id}`)
}



// 格式化日期
const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString('zh-CN')
}

onMounted(() => {
  loadAssignments()
  
  if (route.query.submitted === 'true') {
    ElMessage.success('答案已提交，AI正在批改，可在列表查看状态')
    router.replace({ path: '/studentAssignments' })
  }
})

onUnmounted(() => {
  stopStatusPolling()
})
</script>

<template>
  <div class="student-assignments-container">
    <div class="header">
      <h1>📚 我的题目</h1>
      <p>查看老师发送给你的题目</p>
      <el-button type="primary" @click="loadAssignments" :loading="loading">
        刷新
      </el-button>
    </div>

    <div v-loading="loading" class="assignments-list">
      <el-empty v-if="assignments.length === 0 && !loading" description="暂无题目" />

      <el-card
        v-for="assignment in assignments"
        :key="assignment.id"
        class="assignment-card"
        :class="{ unread: !assignment.is_read, answered: isAnswered(assignment) }"
        shadow="hover"
      >
        <template #header>
          <div class="card-header">
            <span class="title">{{ assignment.title }}</span>
            <div class="tags">
              <el-tag 
                :type="getStatusTag(assignment).type" 
                size="small"
                style="margin-left: 8px"
              >
                {{ getStatusTag(assignment).text }}
              </el-tag>
            </div>
          </div>
        </template>

        <div class="assignment-info">
          <div class="info-item">
            <el-icon><School /></el-icon>
            <span>课程：{{ assignment.course_name }}</span>
          </div>
          <div class="info-item">
            <el-icon><User /></el-icon>
            <span>老师：{{ assignment.teacher_name }}</span>
          </div>
          <div class="info-item">
            <el-icon><Clock /></el-icon>
            <span>发布时间：{{ formatDate(assignment.assignment_created_at) }}</span>
          </div>
          <div v-if="assignment.ai_score" class="info-item score-item">
            <el-icon><Check /></el-icon>
            <span class="score-text">得分：{{ assignment.ai_score }}</span>
          </div>
          <div v-if="assignment.grading_status === 'FAILED' && assignment.grading_error" class="info-item" style="color:#f56c6c;">
            <span>批改失败：{{ assignment.grading_error }}</span>
          </div>
        </div>

        <div class="card-footer">
          <el-button type="primary" size="small" @click="viewDetail(assignment)">
            查看详情
          </el-button>
          <el-button 
            v-if="!isAnswered(assignment)"
            type="success" 
            size="small" 
            :icon="Edit" 
            @click="startAnswer(assignment)"
          >
            开始答题
          </el-button>
          <el-button 
            v-else
            type="info" 
            size="small" 
            disabled
          >
            {{ assignment.grading_status === 'SUCCESS' || assignment.ai_score ? '已完成' : (assignment.grading_status === 'FAILED' ? '批改失败' : '批改中') }}
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.student-assignments-container {
  max-width: 1200px;
  margin: 20px auto;
  padding: 20px;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.header {
  text-align: center;
  margin-bottom: 30px;
}

.header h1 {
  color: #409EFF;
  margin-bottom: 8px;
}

.header p {
  color: #666;
  margin-bottom: 15px;
}

.assignments-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 20px;
  min-height: 300px;
}

.assignment-card {
  transition: all 0.3s;
}

.assignment-card.unread {
  border-left: 4px solid #f56c6c;
}

.assignment-card.answered {
  border-left: 4px solid #67c23a;
}

.assignment-card:hover {
  transform: translateY(-5px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header .tags {
  display: flex;
  align-items: center;
}

.title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.assignment-info {
  margin-bottom: 15px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: #606266;
  font-size: 14px;
}

.info-item .el-icon {
  color: #409EFF;
}

.score-item {
  color: #67c23a;
  font-weight: bold;
}

.score-text {
  color: #67c23a;
  font-weight: bold;
  font-size: 15px;
}

/*.assignment-preview {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  margin-bottom: 15px;
  min-height: 80px;
}*/

.content-preview {
  color: #606266;
  line-height: 1.6;
}

.content-preview :deep(p) {
  margin: 8px 0;
}

.content-preview :deep(code) {
  background-color: #e8e8e8;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.9em;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* Markdown 样式 */
.markdown-body {
  font-family: inherit;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 600;
  color: #303133;
}

.markdown-body :deep(p) {
  margin: 8px 0;
  line-height: 1.8;
}

.markdown-body :deep(code) {
  background-color: rgba(175, 184, 193, 0.2);
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.9em;
  font-family: 'Courier New', monospace;
}

.markdown-body :deep(pre) {
  background-color: #f6f8fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-body :deep(pre code) {
  background-color: transparent;
  padding: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin: 8px 0;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.markdown-body :deep(blockquote) {
  border-left: 4px solid #dfe2e5;
  padding-left: 16px;
  color: #6a737d;
  margin: 12px 0;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}

.markdown-body :deep(table th),
.markdown-body :deep(table td) {
  border: 1px solid #dfe2e5;
  padding: 8px;
}

.markdown-body :deep(table th) {
  background-color: #f6f8fa;
  font-weight: 600;
}

.markdown-body :deep(img) {
  max-width: 100%;
  height: auto;
}

.markdown-body :deep(a) {
  color: #409EFF;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

/* 填空题下划线样式 */
.markdown-body :deep(.fill-blank) {
  display: inline;
  font-weight: normal;
  font-style: normal;
  text-decoration: underline;
  text-decoration-style: solid;
  text-decoration-thickness: 1px;
}

/* 评分结果样式 */
.result-container {
  padding: 10px;
}

.result-content {
  width: 100%;
  text-align: left;
  padding: 20px;
}

.score-section {
  text-align: center;
  padding: 20px;
  background: linear-gradient(135deg, #e353c6 0%, #d7040f 100%);
  border-radius: 12px;
  color: white;
  margin-bottom: 20px;
}

.score-section h3 {
  margin: 0 0 15px 0;
  font-size: 18px;
  font-weight: normal;
  opacity: 0.95;
}

.score-value {
  font-size: 36px;
  font-weight: bold;
  letter-spacing: 2px;
}

.analysis-section h3 {
  color: #303133;
  margin-bottom: 15px;
  font-size: 18px;
}

.analysis-content {
  background-color: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
  border-left: 4px solid #409EFF;
  line-height: 1.8;
  color: #606266;
}

@media (max-width: 768px) {
  .assignments-list {
    grid-template-columns: 1fr;
  }
  
  .el-dialog {
    width: 95% !important;
  }

}
</style>


