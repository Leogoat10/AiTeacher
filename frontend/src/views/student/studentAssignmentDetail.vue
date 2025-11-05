<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft, Edit, Clock, User, School, Check } from '@element-plus/icons-vue'
import { marked } from 'marked'
import katex from 'katex'
import DOMPurify from 'dompurify'
import 'katex/dist/katex.min.css'

const router = useRouter()
const route = useRoute()

const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

// 占位符前缀，用于保护 LaTeX 公式和下划线
const LATEX_PLACEHOLDER_PREFIX = 'LATEXFORMULA'
const UNDERSCORE_PLACEHOLDER_PREFIX = 'UNDERSCOREBLANK'
const latexFormulaStore: Map<string, { formula: string; displayMode: boolean }> = new Map()
const underscoreStore: Map<string, string> = new Map()

// 提取并保护 LaTeX 公式和下划线，替换为占位符
const protectLatexFormulas = (text: string): string => {
  latexFormulaStore.clear()
  underscoreStore.clear()
  let counter = 0
  let underscoreCounter = 0
  
  text = text.replace(/\\\[([\s\S]*?)\\\]/g, (match, formula) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}DISPLAY${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode: true })
    counter++
    return placeholder
  })
  
  text = text.replace(/\\\(([\s\S]*?)\\\)/g, (match, formula) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}INLINE${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode: false })
    counter++
    return placeholder
  })
  
  text = text.replace(/_{2,}/g, (match) => {
    const placeholder = `${UNDERSCORE_PLACEHOLDER_PREFIX}${underscoreCounter}ENDUNDERSCORE`
    underscoreStore.set(placeholder, match)
    underscoreCounter++
    return placeholder
  })
  
  return text
}

// 渲染被保护的 LaTeX 公式和下划线
const renderProtectedLatex = (html: string): string => {
  latexFormulaStore.forEach((data, placeholder) => {
    try {
      const rendered = katex.renderToString(data.formula, {
        displayMode: data.displayMode,
        throwOnError: false,
        output: 'html'
      })
      html = html.replace(new RegExp(placeholder, 'g'), rendered)
    } catch (e) {
      console.error('KaTeX render error:', e, 'Formula:', data.formula)
    }
  })
  
  underscoreStore.forEach((underscores, placeholder) => {
    const styledUnderscores = `<span class="fill-blank">${underscores}</span>`
    html = html.replace(new RegExp(placeholder, 'g'), styledUnderscores)
  })
  
  return html
}

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
  submitted_at?: string
}

const assignment = ref<Assignment | null>(null)
const loading = ref(false)

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true
})

// 渲染 Markdown
const renderMarkdown = (content: string): string => {
  if (!content) return ''
  const protectedText = protectLatexFormulas(content)
  const html = marked(protectedText) as string
  const withLatex = renderProtectedLatex(html)
  return DOMPurify.sanitize(withLatex)
}

// 分离题目内容和答案解析
const splitContentAndAnswer = (content: string): { question: string, answer: string } => {
  if (!content) return { question: '', answer: '' }
  
  const separators = [
    '答案与解析',
    '答案和解析', 
    '参考答案',
    '答案：',
    '答案:',
    '解析：',
    '解析:',
    '【答案】',
    '【解析】'
  ]
  
  let splitIndex = -1
  
  for (const sep of separators) {
    const index = content.indexOf(sep)
    if (index !== -1 && (splitIndex === -1 || index < splitIndex)) {
      splitIndex = index
    }
  }
  
  if (splitIndex !== -1) {
    return {
      question: content.substring(0, splitIndex).trim(),
      answer: content.substring(splitIndex).trim()
    }
  }
  
  return { question: content, answer: '' }
}

// 检查是否已答题
const isAnswered = computed(() => {
  return !!(assignment.value?.student_answer || assignment.value?.ai_score)
})

// 获取状态标签
const getStatusTag = computed(() => {
  if (isAnswered.value) {
    return { type: 'success', text: '已答题' }
  }
  return { type: 'warning', text: '待答题' }
})

// 加载题目详情
const loadAssignment = async () => {
  const assignmentId = route.params.assignmentId
  if (!assignmentId) {
    ElMessage.error('题目ID不存在')
    router.push('/studentAssignments')
    return
  }

  loading.value = true
  try {
    const res = await apiClient.get('/student/assignments')
    if (res.data) {
      const found = res.data.find((a: Assignment) => a.assignment_id === Number(assignmentId))
      if (found) {
        assignment.value = found
      } else {
        ElMessage.error('题目不存在')
        router.push('/studentAssignments')
      }
    }
  } catch (err: any) {
    console.error('加载题目失败:', err)
    if (err.response?.status === 401) {
      ElMessage.error('未登录或会话已过期，请先登录')
      router.push('/studentLogin')
    } else {
      ElMessage.error('加载题目失败')
      router.push('/studentAssignments')
    }
  } finally {
    loading.value = false
  }
}

// 返回题目列表
const goBack = () => {
  router.push('/studentAssignments')
}

// 开始答题
const startAnswer = () => {
  if (assignment.value) {
    router.push(`/studentAnswer/${assignment.value.assignment_id}`)
  }
}

// 格式化日期
const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString('zh-CN')
}

onMounted(() => {
  loadAssignment()
})
</script>

<template>
  <div class="detail-page-container">
    <div v-loading="loading" class="detail-page-content">
      <div v-if="assignment" class="detail-wrapper">
        <!-- 顶部导航栏 -->
        <div class="page-header">
          <el-button :icon="ArrowLeft" @click="goBack">返回题目列表</el-button>
          <h2 class="page-title">{{ assignment.title }}</h2>
          <div class="header-actions">
            <el-tag :type="getStatusTag.type" size="large">
              {{ getStatusTag.text }}
            </el-tag>
            <el-button 
              v-if="!isAnswered"
              type="success" 
              size="large"
              :icon="Edit" 
              @click="startAnswer"
            >
              开始答题
            </el-button>
          </div>
        </div>

        <el-divider />

        <!-- 题目信息卡片 -->
        <el-card class="info-card" shadow="never">
          <template #header>
            <div class="card-title">📋 题目信息</div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item>
              <template #label>
                <el-icon><School /></el-icon>
                <span style="margin-left: 5px;">课程</span>
              </template>
              {{ assignment.course_name }}
            </el-descriptions-item>
            <el-descriptions-item>
              <template #label>
                <el-icon><User /></el-icon>
                <span style="margin-left: 5px;">老师</span>
              </template>
              {{ assignment.teacher_name }}
            </el-descriptions-item>
            <el-descriptions-item>
              <template #label>
                <el-icon><Clock /></el-icon>
                <span style="margin-left: 5px;">发布时间</span>
              </template>
              {{ formatDate(assignment.assignment_created_at) }}
            </el-descriptions-item>
            <el-descriptions-item>
              <template #label>
                <el-icon><Clock /></el-icon>
                <span style="margin-left: 5px;">接收时间</span>
              </template>
              {{ formatDate(assignment.received_at) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="assignment.submitted_at" :span="2">
              <template #label>
                <el-icon><Check /></el-icon>
                <span style="margin-left: 5px;">提交时间</span>
              </template>
              {{ formatDate(assignment.submitted_at) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 题目内容卡片 -->
        <el-card class="content-card" shadow="never">
          <template #header>
            <div class="card-title">📝 题目内容</div>
          </template>
          <!-- 未答题时只显示题目部分，已答题后显示完整内容 -->
          <div v-if="!isAnswered" class="content-html markdown-body" v-html="renderMarkdown(splitContentAndAnswer(assignment.content).question)"></div>
          <div v-else class="content-html markdown-body" v-html="renderMarkdown(assignment.content)"></div>
        </el-card>

        <!-- 答案与解析提示（未提交时显示） -->
        <el-card v-if="!isAnswered && splitContentAndAnswer(assignment.content).answer" class="tip-card" shadow="never">
          <el-alert
            title="提示"
            type="warning"
            :closable="false"
            show-icon
          >
            <p>📌 此题目包含答案与解析，提交答案后即可查看</p>
          </el-alert>
        </el-card>

        <!-- 已答题内容展示 -->
        <div v-if="isAnswered">
          <!-- 我的答案卡片 -->
          <el-card class="answer-card" shadow="never">
            <template #header>
              <div class="card-title">✍️ 我的答案</div>
            </template>
            <div class="my-answer-content">
              {{ assignment.student_answer }}
            </div>
          </el-card>

          <!-- 评分与分析卡片 -->
          <el-card class="score-card" shadow="never">
            <template #header>
              <div class="card-title">📊 评分与分析</div>
            </template>
            <div class="score-analysis-section">
              <div class="score-display">
                <h4>得分</h4>
                <div class="score-badge">{{ assignment.ai_score }}</div>
              </div>

              <div class="analysis-display">
                <h4>💡 AI 分析</h4>
                <div class="analysis-text markdown-body" v-html="renderMarkdown(assignment.ai_analysis || '')"></div>
              </div>
            </div>
          </el-card>
        </div>

        <!-- 底部操作栏 -->
        <div v-if="!isAnswered" class="action-bar">
          <el-button size="large" @click="goBack">返回</el-button>
          <el-button
            type="success"
            size="large"
            :icon="Edit"
            @click="startAnswer"
          >
            开始答题
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail-page-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #ffffff 0%, #ffffff 100%);
  padding: 20px;
}

.detail-page-content {
  max-width: 100%;
  margin: 0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
  min-height: auto;
  padding: 0;
}

.detail-wrapper {
  padding: 30px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.page-title {
  flex: 1;
  margin: 0;
  color: #303133;
  font-size: 24px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 15px;
  align-items: center;
}

.info-card,
.content-card,
.answer-card,
.score-card,
.tip-card {
  margin: 20px 0;
  background: transparent;
  border: none;
  box-shadow: none;
}

.info-card :deep(.el-card__header),
.content-card :deep(.el-card__header),
.answer-card :deep(.el-card__header),
.score-card :deep(.el-card__header),
.tip-card :deep(.el-card__header) {
  border-bottom: none;
  background: transparent;
  padding: 0;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.content-html {
  background-color: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
  line-height: 1.8;
  max-height: 600px;
  overflow-y: auto;
}

.my-answer-content {
  background-color: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
  border-left: 4px solid #909399;
  white-space: pre-wrap;
  line-height: 1.8;
  color: #606266;
  min-height: 100px;
}

.score-analysis-section {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 30px;
}

.score-display {
  text-align: center;
}

.score-display h4 {
  color: #303133;
  margin-bottom: 20px;
  font-size: 16px;
}

.score-badge {
  background: linear-gradient(135deg, #e353c6 0%, #d7040f 100%);
  color: white;
  font-size: 36px;
  font-weight: bold;
  padding: 40px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.analysis-display h4 {
  color: #303133;
  margin-bottom: 15px;
  font-size: 16px;
}

.analysis-text {
  background-color: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
  border-left: 4px solid #409EFF;
  line-height: 1.8;
  color: #606266;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  padding-top: 30px;
  border-top: 1px solid #e4e7ed;
  margin-top: 30px;
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

.markdown-body :deep(.fill-blank) {
  display: inline;
  font-weight: normal;
  font-style: normal;
  text-decoration: underline;
  text-decoration-style: solid;
  text-decoration-thickness: 1px;
}

@media (max-width: 768px) {
  .detail-page-container {
    padding: 10px;
  }

  .detail-wrapper {
    padding: 15px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .page-title {
    font-size: 20px;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }

  .score-analysis-section {
    grid-template-columns: 1fr;
  }

  .score-badge {
    padding: 30px;
    font-size: 32px;
  }

  .action-bar {
    flex-direction: column-reverse;
  }

  .action-bar .el-button {
    width: 100%;
  }
}
</style>
