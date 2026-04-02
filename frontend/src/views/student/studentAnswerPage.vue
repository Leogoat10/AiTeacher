<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
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
  
  text = text.replace(/\\\[([\s\S]*?)\\\]/g, (_match, formula) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}DISPLAY${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode: true })
    counter++
    return placeholder
  })
  
  text = text.replace(/\\\(([\s\S]*?)\\\)/g, (_match, formula) => {
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
}

const assignment = ref<Assignment | null>(null)
const loading = ref(false)
const studentAnswer = ref('')
const submitting = ref(false)

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

// 提交答案
const submitAnswer = async () => {
  if (!studentAnswer.value.trim()) {
    ElMessage.warning('请输入答案')
    return
  }

  if (!assignment.value) {
    ElMessage.error('未找到题目信息')
    return
  }

  try {
    await ElMessageBox.confirm(
      '提交后将无法修改，确认提交答案吗？',
      '确认提交',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
  } catch {
    return
  }

  submitting.value = true
  try {
    const res = await apiClient.post('/student/submitAnswer', {
      assignmentId: assignment.value.assignment_id,
      answer: studentAnswer.value
    })

    if (res.data.success) {
      ElMessage.success('答案提交成功！')
      // 跳转到题目列表页
      router.push({
        path: '/studentAssignments',
        query: { 
          submitted: 'true',
          score: res.data.score,
          analysis: res.data.analysis
        }
      })
    } else {
      ElMessage.error(res.data.message || '提交失败')
    }
  } catch (err: any) {
    console.error('提交答案失败:', err)
    if (err.response?.status === 401) {
      ElMessage.error('未登录或会话已过期，请先登录')
      router.push('/studentLogin')
    } else {
      ElMessage.error(err.response?.data?.message || err.response?.data || '提交答案失败')
    }
  } finally {
    submitting.value = false
  }
}

// 返回题目列表
const goBack = () => {
  router.push('/studentAssignments')
}

// 计算题目内容
const questionContent = computed(() => {
  if (!assignment.value) return ''
  return splitContentAndAnswer(assignment.value.content).question
})

onMounted(() => {
  loadAssignment()
})
</script>

<template>
  <div class="answer-page-container">
    <div v-loading="loading" class="answer-page-content">
      <div v-if="assignment" class="answer-wrapper">
        <!-- 顶部导航栏 -->
        <div class="page-header">
          <el-button :icon="ArrowLeft" @click="goBack">返回题目列表</el-button>
          <h2 class="page-title">{{ assignment.title }}</h2>
          <div class="assignment-meta">
            <el-tag>{{ assignment.course_name }}</el-tag>
            <el-tag type="info">{{ assignment.teacher_name }}</el-tag>
          </div>
        </div>

        <el-divider />

        <!-- 题目内容区域 -->
        <div class="question-section">
          <div class="section-header">
            <h3>📝 题目内容</h3>
          </div>
          <div class="question-content markdown-body" v-html="renderMarkdown(questionContent)"></div>
        </div>

        <el-divider />

        <!-- 答题区域 -->
        <div class="answer-section">
          <div class="section-header">
            <h3>✍️ 你的答案</h3>
          </div>
          <el-input
            v-model="studentAnswer"
            type="textarea"
            :rows="15"
            placeholder="请输入你的答案..."
            maxlength="5000"
            show-word-limit
            class="answer-input"
          />
        </div>

        <!-- 提示信息 -->
        <div class="tips-section">
          <el-alert
            title="答题提示"
            type="info"
            :closable="false"
            show-icon
          >
            <template #default>
              <p>• 请认真作答，提交后将无法修改</p>
              <p>• AI会自动评分并给出详细的分析和建议</p>
              <p>• 建议先在本地编辑器写好答案再粘贴提交</p>
            </template>
          </el-alert>
        </div>

        <!-- 底部操作栏 -->
        <div class="action-bar">
          <el-button size="large" @click="goBack">取消</el-button>
          <el-button
            type="primary"
            size="large"
            @click="submitAnswer"
            :loading="submitting"
            :disabled="!studentAnswer.trim()"
          >
            {{ submitting ? '提交中...' : '提交答案' }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.answer-page-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #fafafa 0%, #f6f6f6 100%);
  padding: 20px;
}

.answer-page-content {
  max-width: 100%;
  margin: 0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
  min-height: auto;
  padding: 0;
}

.answer-wrapper {
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

.assignment-meta {
  display: flex;
  gap: 10px;
}

.question-section,
.answer-section {
  margin: 30px 0;
}

.section-header {
  margin-bottom: 15px;
}

.section-header h3 {
  margin: 0;
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}

.question-content {
  background-color: #f5f7fa;
  border-radius: 8px;
  padding: 20px;
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
}

.answer-input {
  width: 100%;
}

.answer-input :deep(textarea) {
  font-size: 15px;
  line-height: 1.8;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.tips-section {
  margin: 30px 0;
}

.tips-section :deep(.el-alert__description) p {
  margin: 5px 0;
  font-size: 14px;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  padding-top: 20px;
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
  .answer-page-container {
    padding: 10px;
  }

  .answer-wrapper {
    padding: 15px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .page-title {
    font-size: 20px;
  }

  .question-content {
    max-height: 300px;
  }

  .answer-input :deep(textarea) {
    font-size: 14px;
  }

  .action-bar {
    flex-direction: column-reverse;
  }

  .action-bar .el-button {
    width: 100%;
  }
}
</style>
