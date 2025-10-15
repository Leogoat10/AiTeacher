<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { Clock, User, School } from '@element-plus/icons-vue'

const router = useRouter()

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
}

const assignments = ref<Assignment[]>([])
const loading = ref(false)
const selectedAssignment = ref<Assignment | null>(null)
const showDetailDialog = ref(false)

// 加载题目列表
const loadAssignments = async () => {
  loading.value = true
  try {
    const res = await apiClient.get('/student/assignments')
    if (res.data) {
      assignments.value = res.data
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

// 查看题目详情
const viewDetail = (assignment: Assignment) => {
  selectedAssignment.value = assignment
  showDetailDialog.value = true
}

// 格式化日期
const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString('zh-CN')
}

onMounted(() => {
  loadAssignments()
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
        :class="{ unread: !assignment.is_read }"
        shadow="hover"
      >
        <template #header>
          <div class="card-header">
            <span class="title">{{ assignment.title }}</span>
            <el-tag v-if="!assignment.is_read" type="danger" size="small">
              未读
            </el-tag>
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
        </div>

        <div class="assignment-preview">
          <div class="content-preview">
            {{ assignment.content.substring(0, 100) }}{{ assignment.content.length > 100 ? '...' : '' }}
          </div>
        </div>

        <div class="card-footer">
          <el-button type="primary" size="small" @click="viewDetail(assignment)">
            查看详情
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- 题目详情对话框 -->
    <el-dialog
      v-model="showDetailDialog"
      :title="selectedAssignment?.title"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="selectedAssignment" class="assignment-detail">
        <div class="detail-info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="课程">
              {{ selectedAssignment.course_name }}
            </el-descriptions-item>
            <el-descriptions-item label="老师">
              {{ selectedAssignment.teacher_name }}
            </el-descriptions-item>
            <el-descriptions-item label="发布时间">
              {{ formatDate(selectedAssignment.assignment_created_at) }}
            </el-descriptions-item>
            <el-descriptions-item label="接收时间">
              {{ formatDate(selectedAssignment.received_at) }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-content">
          <h4>题目内容：</h4>
          <pre class="content-text">{{ selectedAssignment.content }}</pre>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showDetailDialog = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
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

.assignment-card:hover {
  transform: translateY(-5px);
}

.card-header {
  display: flex;
  justify-content: space-between;
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

.assignment-preview {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  margin-bottom: 15px;
  min-height: 80px;
}

.content-preview {
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
}

.assignment-detail {
  padding: 10px 0;
}

.detail-info {
  margin-bottom: 20px;
}

.detail-content {
  margin-top: 20px;
}

.detail-content h4 {
  margin-bottom: 10px;
  color: #303133;
}

.content-text {
  white-space: pre-wrap;
  word-wrap: break-word;
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  line-height: 1.8;
  font-family: inherit;
  margin: 0;
  max-height: 500px;
  overflow-y: auto;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .assignments-list {
    grid-template-columns: 1fr;
  }
}
</style>
