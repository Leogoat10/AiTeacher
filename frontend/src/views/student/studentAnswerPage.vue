<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadProps, UploadUserFile } from 'element-plus'
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

const autoWrapPlainMathExpressions = (text: string, registerInlineFormula: (formula: string) => string): string => {
  if (!text) return text
  const exprRegex = /([A-Za-zΑ-Ωα-ωσθμλπΔΣΩ][A-Za-z0-9Α-Ωα-ωσθμλπΔΣΩ_]*(?:\([^()\n]{1,40}\))?\s*=\s*[^,，。；;\n]{1,120})/g
  return text.split('\n').map((line) => {
    if (
      !line ||
      line.includes(LATEX_PLACEHOLDER_PREFIX) ||
      line.includes('\\(') ||
      line.includes('\\[') ||
      line.includes('`') ||
      /^\s*([#>*-]|\d+\.)\s+/.test(line)
    ) {
      return line
    }

    return line.replace(exprRegex, (match) => {
      const normalized = match.trim()
      const hasMathToken = /[+\-*/^_()]|[Α-Ωα-ωσθμλπΔΣΩ]|e\^\{?[-+]?[A-Za-z0-9]/.test(normalized)
      if (!hasMathToken) return match
      return registerInlineFormula(normalized)
    })
  }).join('\n')
}

// 提取并保护 LaTeX 公式和下划线，替换为占位符
const protectLatexFormulas = (text: string): string => {
  latexFormulaStore.clear()
  underscoreStore.clear()
  let counter = 0
  let underscoreCounter = 0
  const registerFormula = (formula: string, displayMode: boolean) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}${displayMode ? 'DISPLAY' : 'INLINE'}${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode })
    counter++
    return placeholder
  }
  const registerInlineFormula = (formula: string) => registerFormula(formula, false)
  
  // 处理块级公式 \[...\]
  text = text.replace(/\\\[([\s\S]*?)\\\]/g, (_match, formula) => {
    return registerFormula(formula, true)
  })
  
  // 处理行内公式 \(...\)
  text = text.replace(/\\\(([\s\S]*?)\\\)/g, (_match, formula) => {
    return registerInlineFormula(formula)
  })

  // 处理双美元符号块级公式 $$...$$
  text = text.replace(/\$\$([\s\S]*?)\$\$/g, (_match, formula) => {
    return registerFormula(formula, true)
  })

  // 处理单美元符号行内公式 $...$（避免误匹配货币符号）
  text = text.replace(/\$([^\$\n]+?)\$/g, (match, formula) => {
    // 检查是否包含数学字符，避免误判
    if (/[A-Za-z\\{}\^_=+\-*/()]/.test(formula)) {
      return registerInlineFormula(formula)
    }
    return match
  })

  text = autoWrapPlainMathExpressions(text, registerInlineFormula)
  
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
  total_score?: number
  course_name: string
  teacher_name: string
  assignment_created_at: string
}

const assignment = ref<Assignment | null>(null)
const loading = ref(false)
const studentAnswer = ref('')
const imageFileList = ref<UploadUserFile[]>([])
const uploadedImageDataUrl = ref('')
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
  // 配置 DOMPurify 允许 KaTeX 生成的标签和属性
  return DOMPurify.sanitize(withLatex, {
    ADD_TAGS: ['math', 'semantics', 'mrow', 'mi', 'mo', 'mn', 'msup', 'msub', 'mfrac', 'msqrt', 'mroot', 'mtext', 'annotation', 'munderover', 'mtable', 'mtr', 'mtd'],
    ADD_ATTR: ['xmlns', 'aria-hidden', 'focusable']
  })
}

const answerMarkerRegex = /答案与解析|答案和解析|参考答案|答案[：:]|解析[：:]|【答案】|【解析】/
const questionStartRegex = /^\s*\d+[\.\)]\s+/

// 提取仅题目部分，过滤每题的答案/解析段落，保留后续题目
const extractQuestionContent = (content: string): string => {
  if (!content) return ''

  const normalized = content.replace(/\r\n/g, '\n')
  const lines = normalized.split('\n')
  const filtered: string[] = []
  let inAnswerBlock = false

  for (const line of lines) {
    if (questionStartRegex.test(line)) {
      inAnswerBlock = false
      filtered.push(line)
      continue
    }

    if (answerMarkerRegex.test(line)) {
      inAnswerBlock = true
      continue
    }

    if (!inAnswerBlock) {
      filtered.push(line)
    }
  }

  return filtered.join('\n').trim()
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
  if (!studentAnswer.value.trim() && !uploadedImageDataUrl.value) {
    ElMessage.warning('请输入答案或上传答题图片')
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
      answer: studentAnswer.value,
      imageDataUrl: uploadedImageDataUrl.value || null
    })

    if (res.data.success) {
      ElMessage.success(res.data.message || '答案提交成功，AI正在批改')
      // 跳转到题目列表页
      router.push({
        path: '/studentAssignments',
        query: { 
          submitted: 'true'
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

const validateImageFile = (file: File) => {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('仅支持上传图片文件')
    return false
  }
  const isLt5M = file.size / 1024 / 1024 <= 5
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

const beforeImageUpload: UploadProps['beforeUpload'] = (rawFile) => {
  return validateImageFile(rawFile)
}

const readFileAsDataUrl = (file: File) => {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('读取图片失败'))
    reader.readAsDataURL(file)
  })
}

const handleImageChange: UploadProps['onChange'] = async (uploadFile, uploadFiles) => {
  imageFileList.value = uploadFiles.slice(-1).map((file) => ({
    name: file.name,
    url: file.url
  }))
  if (!uploadFile.raw) {
    uploadedImageDataUrl.value = ''
    return
  }
  if (!validateImageFile(uploadFile.raw)) {
    imageFileList.value = []
    uploadedImageDataUrl.value = ''
    return
  }
  try {
    uploadedImageDataUrl.value = await readFileAsDataUrl(uploadFile.raw)
  } catch (_error) {
    uploadedImageDataUrl.value = ''
    ElMessage.error('图片读取失败，请重新选择')
  }
}

const handleImageRemove: UploadProps['onRemove'] = () => {
  imageFileList.value = []
  uploadedImageDataUrl.value = ''
}

// 返回题目列表
const goBack = () => {
  router.push('/studentAssignments')
}

// 计算题目内容
const questionContent = computed(() => {
  if (!assignment.value) return ''
  return extractQuestionContent(assignment.value.content)
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
          <div v-if="assignment.total_score" style="margin-bottom: 12px; color: #606266;">本次作业总分：{{ assignment.total_score }} 分</div>
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
          <div class="answer-image-upload">
            <el-upload
              v-model:file-list="imageFileList"
              accept="image/*"
              :auto-upload="false"
              :limit="1"
              :before-upload="beforeImageUpload"
              :on-change="handleImageChange"
              :on-remove="handleImageRemove"
            >
              <el-button type="primary" plain>上传答题图片（可选）</el-button>
            </el-upload>
            <div class="upload-hint">支持 jpg/png/webp，单张不超过 5MB。提交时将自动识别图片文字参与评分。</div>
          </div>
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
              <p>• 可仅上传答题图片，系统会自动识别文字后判题</p>
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
            :disabled="!studentAnswer.trim() && !uploadedImageDataUrl"
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

.answer-image-upload {
  margin-top: 14px;
}

.upload-hint {
  margin-top: 8px;
  color: #909399;
  font-size: 13px;
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
