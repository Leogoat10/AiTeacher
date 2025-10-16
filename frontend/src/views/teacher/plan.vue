<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api'
})

// 表单数据
const subject = ref('')          // 科目/专业
const difficulty = ref('')       // 难易程度
const questionType = ref('')     // 题型
const questionCount = ref('')    // 题目数
const customMessage = ref('')    // 自定义消息（选填）
const chatHistory = ref<Array<{ role: string; content: string; timestamp: Date; id: number }>>([])
const loading = ref(false)
const selectedMessages = ref<Set<number>>(new Set()) // 用于存储选中的消息索引
let messageIdCounter = 0 // 用于生成唯一ID

const send = async () => {
  // 验证必填字段
  if (!subject.value.trim()) {
    ElMessage.warning('请输入科目/专业')
    return
  }

  if (!difficulty.value) {
    ElMessage.warning('请选择难易程度')
    return
  }

  if (!questionType.value) {
    ElMessage.warning('请选择题型')
    return
  }

  // 验证题目数（如果填写的话）
  if (questionCount.value && isNaN(Number(questionCount.value))) {
    ElMessage.warning('题目数必须为数字')
    return
  }

  // 构造显示给用户的消息
  let userDisplayMessage = `${subject.value} ${difficulty.value} ${questionType.value}`
  if (questionCount.value) {
    userDisplayMessage += ` (${questionCount.value}题)`
  }
  if (customMessage.value.trim()) {
    userDisplayMessage += ` - ${customMessage.value}`
  }

  chatHistory.value.push({
    role: 'user',
    content: userDisplayMessage,
    timestamp: new Date(),
    id: messageIdCounter++
  })

  loading.value = true

  try {
    // 发送表单数据到后端
    const res = await apiClient.post('/teacher/plan', {
      subject: subject.value,
      difficulty: difficulty.value,
      questionType: questionType.value,
      questionCount: questionCount.value || null,
      customMessage: customMessage.value || null
    })

    if (res.data.success) {
      chatHistory.value.push({
        role: 'ai',
        content: res.data.reply,
        timestamp: new Date(),
        id: messageIdCounter++
      })
    } else {
      ElMessage.error('AI服务错误: ' + (res.data.error || '未知错误'))
      chatHistory.value.push({
        role: 'ai',
        content: '抱歉，AI服务暂时不可用，请稍后重试。',
        timestamp: new Date(),
        id: messageIdCounter++
      })
    }
  } catch (err: any) {
    console.error('API调用错误:', err)
    if (err.response) {
      // 服务器返回错误状态码
      ElMessage.error('AI服务错误: ' + (err.response.data.error || '未知错误'))
      chatHistory.value.push({
        role: 'ai',
        content: 'AI服务错误: ' + (err.response.data.error || '未知错误'),
        timestamp: new Date(),
        id: messageIdCounter++
      })
    } else if (err.request) {
      // 网络错误
      ElMessage.error('网络连接失败，请检查服务是否启动')
      chatHistory.value.push({
        role: 'ai',
        content: '网络连接失败，请检查：\n1. 后端服务是否启动\n2. 网络连接是否正常',
        timestamp: new Date(),
        id: messageIdCounter++
      })
    } else {
      // 其他错误
      ElMessage.error('请求配置错误')
    }
  } finally {
    loading.value = false
    // 保留用户输入的内容
  }
}


// 添加清空表单的方法
const clearForm = () => {
  subject.value = ''
  difficulty.value = ''
  questionType.value = ''
  questionCount.value = ''
  customMessage.value = ''
}

// 切换消息选择状态
const toggleMessageSelection = (id: number) => {
  if (selectedMessages.value.has(id)) {
    selectedMessages.value.delete(id)
  } else {
    selectedMessages.value.add(id)
  }
  selectedMessages.value = new Set(selectedMessages.value) // 触发响应式更新
}

// 全选/取消全选
const toggleSelectAll = () => {
  const aiMessages = chatHistory.value.filter(msg => msg.role === 'ai')
  const allSelected = aiMessages.every(msg => selectedMessages.value.has(msg.id))

  if (allSelected) {
    // 取消全选
    aiMessages.forEach(msg => selectedMessages.value.delete(msg.id))
  } else {
    // 全选
    aiMessages.forEach(msg => selectedMessages.value.add(msg.id))
  }
  selectedMessages.value = new Set(selectedMessages.value) // 触发响应式更新
}

// 检查是否所有AI消息都被选中
const isAllSelected = computed(() => {
  const aiMessages = chatHistory.value.filter(msg => msg.role === 'ai')
  return aiMessages.length > 0 && aiMessages.every(msg => selectedMessages.value.has(msg.id))
})

// 检查是否有选中的消息
const hasSelectedMessages = computed(() => {
  return selectedMessages.value.size > 0
})

// 导出为Word文档
const exportToWord = () => {
  if (chatHistory.value.length === 0) {
    ElMessage.warning('暂无内容可导出')
    return
  }

  // 如果没有选中任何消息，则导出所有AI生成的内容
  let contentsToExport: string[]

  if (selectedMessages.value.size === 0) {
    contentsToExport = chatHistory.value
      .filter(msg => msg.role === 'ai')
      .map(msg => msg.content)
  } else {
    // 只导出选中的内容
    contentsToExport = chatHistory.value
      .filter(msg => msg.role === 'ai' && selectedMessages.value.has(msg.id))
      .map(msg => msg.content)
  }

  if (contentsToExport.length === 0) {
    ElMessage.warning('没有AI生成的内容可导出')
    return
  }

  const content = contentsToExport.join('\n\n')
  createAndDownloadWord(content)
}

// 创建并下载Word文档
const createAndDownloadWord = (content: string) => {
  // 使用简单的文本文件导出
  const blob = new Blob([content], { type: 'application/msword;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `AI教师助手_${new Date().toISOString().slice(0, 10)}.doc`
  link.click()

  ElMessage.success('导出成功')
}

// 测试服务连接
const testConnection = async () => {
  try {
    const res = await apiClient.get('/teacher/health')
    if (res.status === 200) {
      ElMessage.success('服务连接正常')
    }
  } catch (err) {
    ElMessage.error('服务连接失败，请检查后端是否启动')
  }
}
</script>

<template>
  <div class="ai-teacher-container">
    <div class="header">
      <h1>👩‍🏫 AI 教师助手</h1>
      <p>向 AI 提问，获取教学方案建议</p>
      <div class="header-buttons">
        <el-button size="small" @click="testConnection" type="info">
          测试服务连接
        </el-button>
        <el-button
          size="small"
          @click="toggleSelectAll"
          :type="isAllSelected ? 'danger' : 'primary'"
          :disabled="chatHistory.filter(msg => msg.role === 'ai').length === 0"
        >
          {{ isAllSelected ? '取消全选' : '全选' }}
        </el-button>
        <el-button
          size="small"
          @click="exportToWord"
          type="success"
          :disabled="!hasSelectedMessages && chatHistory.filter(msg => msg.role === 'ai').length === 0"
        >
          导出为Word
        </el-button>
        <el-button
          size="small"
          @click="clearForm"
          type="warning"
        >
          清空表单
        </el-button>
      </div>
    </div>

    <div class="main-container">
      <!-- 左侧表单区域 -->
      <div class="form-container">
        <div class="form-title">题目生成设置</div>
        <el-form label-position="top">
          <el-form-item label="科目/专业">
            <el-input
              v-model="subject"
              placeholder="请输入科目/专业"
              :disabled="loading"
            />
          </el-form-item>

          <el-form-item label="难易程度">
            <el-select
              v-model="difficulty"
              placeholder="请选择难易程度"
              :disabled="loading"
              style="width: 100%"
            >
              <el-option label="简单" value="简单" />
              <el-option label="中等" value="中等" />
              <el-option label="困难" value="困难" />
            </el-select>
          </el-form-item>

          <el-form-item label="题型">
            <el-select
              v-model="questionType"
              placeholder="请选择题型"
              :disabled="loading"
              style="width: 100%"
            >
              <el-option label="选择题" value="选择题" />
              <el-option label="填空题" value="填空题" />
              <el-option label="判断题" value="判断题" />
              <el-option label="简答题" value="简答题" />
              <el-option label="解答题" value="解答题" />
            </el-select>
          </el-form-item>

          <el-form-item label="题目数">
            <el-input
              v-model="questionCount"
              placeholder="请输入题目数量（选填）"
              :disabled="loading"
              type="number"
              min="1"
            />
          </el-form-item>

          <el-form-item label="具体要求（选填）">
            <el-input
              v-model="customMessage"
              placeholder="请输入具体要求"
              :disabled="loading"
              type="textarea"
              :rows="3"
            />
          </el-form-item>

          <el-button
            type="primary"
            @click="send"
            :loading="loading"
            :disabled="!subject.trim() || !difficulty || !questionType"
            style="width: 100%"
          >
            生成题目
          </el-button>
        </el-form>
      </div>

      <!-- 右侧AI对话区域 -->
      <div class="chat-container">
        <div
          v-for="chat in chatHistory"
          :key="chat.id"
          class="message"
          :class="chat.role"
        >
          <div class="message-header">
            <strong>{{ chat.role === 'user' ? '👤 您' : '🤖 AI助手' }}</strong>
            <div class="message-actions">
              <el-checkbox
                v-if="chat.role === 'ai'"
                :model-value="selectedMessages.has(chat.id)"
                @change="() => toggleMessageSelection(chat.id)"
                size="small"
              />
              <span class="timestamp">{{ chat.timestamp.toLocaleTimeString() }}</span>
            </div>
          </div>
          <el-card
            :shadow="chat.role === 'user' ? 'never' : 'hover'"
            :class="['message-content', chat.role]"
          >
            <pre>{{ chat.content }}</pre>
          </el-card>
        </div>

        <div v-if="loading" class="loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>AI正在思考中...</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-teacher-container {
  max-width: 1400px;
  margin: 20px auto;
  padding: 20px;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
}

.header {
  text-align: center;
  margin-bottom: 20px;
}

.header h1 {
  color: #409EFF;
  margin-bottom: 8px;
}

.header p {
  color: #666;
  margin-bottom: 10px;
}

.header-buttons {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-top: 10px;
}

.main-container {
  display: flex;
  flex: 1;
  gap: 20px;
  height: 0;
}

.form-container {
  width: 350px;
  padding: 20px;
  border: 1px solid #eaeaea;
  border-radius: 8px;
  background: #fafafa;
  display: flex;
  flex-direction: column;
}

.form-title {
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 20px;
  color: #333;
}

.chat-container {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  border: 1px solid #eaeaea;
  border-radius: 8px;
  background: #fafafa;
}

.message {
  margin-bottom: 16px;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  font-size: 14px;
}

.message-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.timestamp {
  color: #999;
  font-size: 12px;
}

.message-content {
  border: none;
}

.message-content pre {
  white-space: pre-wrap;
  word-wrap: break-word;
  margin: 0;
  font-family: inherit;
  line-height: 1.6;
}

.loading {
  text-align: center;
  padding: 20px;
  color: #666;
}

@media (max-width: 768px) {
  .main-container {
    flex-direction: column;
  }

  .form-container {
    width: 100%;
  }
}
</style>
