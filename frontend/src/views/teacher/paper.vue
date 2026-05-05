<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { marked } from 'marked'
import katex from 'katex'
import DOMPurify from 'dompurify'
import { Document, HeadingLevel, Packer, Paragraph, TextRun } from 'docx'
import { ChatDotRound } from '@element-plus/icons-vue'
import 'katex/dist/katex.min.css'

type TaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

interface ChatMessage {
  role: 'user' | 'ai'
  content: string
  timestamp: Date
  id: number
}

interface PresetPrompt {
  id: number
  title: string
  promptContent: string
  systemDefault: boolean
}

interface PaperHistoryItem {
  id: number
  title: string
  subject: string
  grade: string
  examType: string
  createdAt: string
}

const LATEX_PLACEHOLDER_PREFIX = 'LATEXFORMULA'
const UNDERSCORE_PLACEHOLDER_PREFIX = 'UNDERSCOREBLANK'
const latexFormulaStore: Map<string, { formula: string; displayMode: boolean }> = new Map()
const underscoreStore: Map<string, string> = new Map()
const apiClient = axios.create({ baseURL: '/api', withCredentials: true })

marked.setOptions({ breaks: true, gfm: true })

const autoWrapPlainMathExpressions = (text: string, registerInlineFormula: (formula: string) => string): string => {
  if (!text) return text
  const exprRegex = /([A-Za-zΑ-Ωα-ωσθμλπΔΣΩ][A-Za-z0-9Α-Ωα-ωσθμλπΔΣΩ_]*(?:\([^()\n]{1,40}\))?\s*=\s*[^,，。；;\n]{1,120})/g
  return text.split('\n').map((line) => {
    if (!line || line.includes(LATEX_PLACEHOLDER_PREFIX) || line.includes('\\(') || line.includes('\\[') || line.includes('`') || /^\s*([#>*-]|\d+\.)\s+/.test(line)) {
      return line
    }
    return line.replace(exprRegex, (match) => {
      const normalized = match.trim()
      const hasMathToken = /[+\-*/^_()]|[Α-Ωα-ωσθμλπΔΣΩ]|e\^\{?[-+]?[A-Za-z0-9]/.test(normalized)
      return hasMathToken ? registerInlineFormula(normalized) : match
    })
  }).join('\n')
}

const protectLatexFormulas = (text: string): string => {
  latexFormulaStore.clear()
  underscoreStore.clear()
  let counter = 0
  let underscoreCounter = 0
  const registerFormula = (formula: string, displayMode: boolean) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}${displayMode ? 'DISPLAY' : 'INLINE'}${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode })
    counter += 1
    return placeholder
  }
  const registerInlineFormula = (formula: string) => registerFormula(formula, false)
  let replaced = text.replace(/\\\[([\s\S]*?)\\\]/g, (_match, formula) => registerFormula(formula, true))
  replaced = replaced.replace(/\\\(([\s\S]*?)\\\)/g, (_match, formula) => registerInlineFormula(formula))
  replaced = replaced.replace(/\$\$([\s\S]*?)\$\$/g, (_match, formula) => registerFormula(formula, true))
  replaced = replaced.replace(/\$([^\$\n]+?)\$/g, (match, formula) => (/[A-Za-z\\{}\^_=+\-*/()]/.test(formula) ? registerInlineFormula(formula) : match))
  replaced = autoWrapPlainMathExpressions(replaced, registerInlineFormula)
  replaced = replaced.replace(/_{2,}/g, (match) => {
    const placeholder = `${UNDERSCORE_PLACEHOLDER_PREFIX}${underscoreCounter}ENDUNDERSCORE`
    underscoreStore.set(placeholder, match)
    underscoreCounter += 1
    return placeholder
  })
  return replaced
}

const renderProtectedLatex = (html: string): string => {
  let renderedHtml = html
  latexFormulaStore.forEach((data, placeholder) => {
    try {
      const rendered = katex.renderToString(data.formula, { displayMode: data.displayMode, throwOnError: false, output: 'html' })
      renderedHtml = renderedHtml.replace(new RegExp(placeholder, 'g'), rendered)
    } catch {
      // ignore single formula render failure
    }
  })
  underscoreStore.forEach((underscores, placeholder) => {
    renderedHtml = renderedHtml.replace(new RegExp(placeholder, 'g'), `<span class="fill-blank">${underscores}</span>`)
  })
  return renderedHtml
}

const renderMarkdown = (content: string): string => {
  if (!content) return ''
  const protectedText = protectLatexFormulas(content)
  const html = marked(protectedText) as string
  const withLatex = renderProtectedLatex(html)
  return DOMPurify.sanitize(withLatex, {
    ADD_TAGS: ['math', 'semantics', 'mrow', 'mi', 'mo', 'mn', 'msup', 'msub', 'mfrac', 'msqrt', 'mroot', 'mtext', 'annotation', 'munderover', 'mtable', 'mtr', 'mtd'],
    ADD_ATTR: ['xmlns', 'aria-hidden', 'focusable']
  })
}

const subject = ref('')
const grade = ref<string | string[]>('')
const examType = ref('单元测验')
const durationMinutes = ref(90)
const totalScore = ref(100)
const questionTypeCounts = ref<Record<string, number>>({
  选择题: 4,
  填空题: 2,
  判断题: 2,
  简答题: 1,
  解答题: 1
})
const knowledgePoints = ref('')
const customRequirement = ref('')
const taskStatus = ref<TaskStatus | null>(null)
const taskErrorMessage = ref('')
const loading = ref(false)

const presetPrompts = ref<PresetPrompt[]>([])
const loadingPresetPrompts = ref(false)
const previewPresetPrompt = ref<PresetPrompt | null>(null)
const showPresetPreviewDialog = ref(false)
const showAddPresetDialog = ref(false)
const creatingPresetPrompt = ref(false)
const newPresetTitle = ref('')
const newPresetContent = ref('')

const paperHistory = ref<PaperHistoryItem[]>([])
const loadingPaperHistory = ref(false)
const showHistoryDialog = ref(false)
const currentPaperId = ref<number | null>(null)

const chatHistory = ref<ChatMessage[]>([])
const selectedAiMessageIds = ref<Set<number>>(new Set())
let messageIdCounter = 0

const educationOptions = [
  {
    value: '小学',
    label: '小学',
    children: [
      { value: '一年级', label: '一年级' },
      { value: '二年级', label: '二年级' },
      { value: '三年级', label: '三年级' },
      { value: '四年级', label: '四年级' },
      { value: '五年级', label: '五年级' },
      { value: '六年级', label: '六年级' }
    ]
  },
  {
    value: '初中',
    label: '初中',
    children: [
      { value: '七年级', label: '七年级' },
      { value: '八年级', label: '八年级' },
      { value: '九年级', label: '九年级' }
    ]
  },
  {
    value: '高中',
    label: '高中',
    children: [
      { value: '高一', label: '高一' },
      { value: '高二', label: '高二' },
      { value: '高三', label: '高三' }
    ]
  },
  {
    value: '大学',
    label: '大学',
    children: [
      { value: '大学一年级', label: '大学一年级' },
      { value: '大学二年级', label: '大学二年级' },
      { value: '大学三年级', label: '大学三年级' },
      { value: '大学四年级', label: '大学四年级' }
    ]
  }
]

const normalizedGrade = computed(() => Array.isArray(grade.value) ? grade.value.join('') : grade.value)
const totalQuestionCount = computed(() => Object.values(questionTypeCounts.value).reduce((sum, count) => sum + Number(count || 0), 0))
const aiMessageCount = computed(() => chatHistory.value.filter(item => item.role === 'ai').length)
const userMessageCount = computed(() => chatHistory.value.filter(item => item.role === 'user').length)
const taskStatusType = computed(() => {
  if (taskStatus.value === 'FAILED') return 'danger'
  if (taskStatus.value === 'SUCCESS') return 'success'
  if (taskStatus.value === 'RUNNING') return 'warning'
  return 'info'
})
const taskStatusText = computed(() => {
  if (!taskStatus.value) return '空闲'
  if (taskStatus.value === 'PENDING') return '排队中'
  if (taskStatus.value === 'RUNNING') return '生成中'
  if (taskStatus.value === 'SUCCESS') return '已完成'
  return '失败'
})

const isExportableAiMessage = (message: ChatMessage): boolean => {
  if (message.role !== 'ai') return false
  const content = message.content?.trim() || ''
  if (!content) return false
  return !content.startsWith('生成失败：') && !content.startsWith('任务状态：')
}

const exportableAiMessages = computed(() => chatHistory.value.filter((message) => isExportableAiMessage(message)))
const hasAnyExportableAiMessage = computed(() => exportableAiMessages.value.length > 0)
const hasSelectedAiMessage = computed(() => selectedAiMessageIds.value.size > 0)
const isAllAiMessageSelected = computed(() => {
  const messages = exportableAiMessages.value
  return messages.length > 0 && messages.every((message) => selectedAiMessageIds.value.has(message.id))
})

const toggleAiMessageSelection = (messageId: number) => {
  const selected = new Set(selectedAiMessageIds.value)
  if (selected.has(messageId)) {
    selected.delete(messageId)
  } else {
    selected.add(messageId)
  }
  selectedAiMessageIds.value = selected
}

const toggleSelectAllAiMessages = () => {
  if (isAllAiMessageSelected.value) {
    selectedAiMessageIds.value = new Set()
    return
  }
  selectedAiMessageIds.value = new Set(exportableAiMessages.value.map((message) => message.id))
}

const normalizeFormulaToken = (token: string): string => {
  if (token.startsWith('\\[') && token.endsWith('\\]')) return token.slice(2, -2).trim()
  if (token.startsWith('\\(') && token.endsWith('\\)')) return token.slice(2, -2).trim()
  if (token.startsWith('$$') && token.endsWith('$$')) return token.slice(2, -2).trim()
  if (token.startsWith('$') && token.endsWith('$')) return token.slice(1, -1).trim()
  return token.trim()
}

const parseInlineRuns = (line: string): TextRun[] => {
  if (!line) return [new TextRun('')]
  const runs: TextRun[] = []
  const tokenRegex = /(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$[^\$\n]+?\$|\*\*[^*]+\*\*|`[^`]+`)/g
  let lastIndex = 0
  let match: RegExpExecArray | null = tokenRegex.exec(line)
  while (match) {
    if (match.index > lastIndex) {
      runs.push(new TextRun(line.slice(lastIndex, match.index)))
    }
    const token = match[0]
    if (token.startsWith('**') && token.endsWith('**')) {
      runs.push(new TextRun({ text: token.slice(2, -2), bold: true }))
    } else if (
      (token.startsWith('\\[') && token.endsWith('\\]')) ||
      (token.startsWith('\\(') && token.endsWith('\\)')) ||
      (token.startsWith('$$') && token.endsWith('$$')) ||
      (token.startsWith('$') && token.endsWith('$'))
    ) {
      runs.push(new TextRun({ text: normalizeFormulaToken(token), font: 'Cambria Math' }))
    } else if (token.startsWith('`') && token.endsWith('`')) {
      runs.push(new TextRun({ text: token.slice(1, -1), font: 'Consolas' }))
    } else {
      runs.push(new TextRun(token))
    }
    lastIndex = tokenRegex.lastIndex
    match = tokenRegex.exec(line)
  }
  if (lastIndex < line.length) {
    runs.push(new TextRun(line.slice(lastIndex)))
  }
  return runs.length > 0 ? runs : [new TextRun('')]
}

const markdownToParagraphs = (markdown: string): Paragraph[] => {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n')
  const paragraphs: Paragraph[] = []
  let inCodeBlock = false
  for (const line of lines) {
    const raw = line || ''
    const trimmed = raw.trim()
    if (trimmed.startsWith('```')) {
      inCodeBlock = !inCodeBlock
      continue
    }
    if (inCodeBlock) {
      paragraphs.push(new Paragraph({ children: [new TextRun({ text: raw, font: 'Consolas' })] }))
      continue
    }
    if (!trimmed) {
      paragraphs.push(new Paragraph({}))
      continue
    }
    const headingMatch = trimmed.match(/^(#{1,6})\s+(.+)$/)
    if (headingMatch) {
      const level = headingMatch[1].length
      const text = headingMatch[2]
      const heading =
        level === 1 ? HeadingLevel.HEADING_1 :
          level === 2 ? HeadingLevel.HEADING_2 :
            level === 3 ? HeadingLevel.HEADING_3 :
              level === 4 ? HeadingLevel.HEADING_4 :
                level === 5 ? HeadingLevel.HEADING_5 : HeadingLevel.HEADING_6
      paragraphs.push(new Paragraph({ heading, children: parseInlineRuns(text) }))
      continue
    }
    const bulletMatch = trimmed.match(/^[-*]\s+(.+)$/)
    if (bulletMatch) {
      paragraphs.push(new Paragraph({ bullet: { level: 0 }, children: parseInlineRuns(bulletMatch[1]) }))
      continue
    }
    const numberMatch = trimmed.match(/^(\d+)\.\s+(.+)$/)
    if (numberMatch) {
      paragraphs.push(new Paragraph({ children: parseInlineRuns(`${numberMatch[1]}. ${numberMatch[2]}`) }))
      continue
    }
    paragraphs.push(new Paragraph({ children: parseInlineRuns(trimmed) }))
  }
  return paragraphs
}

const createAndDownloadWord = async (messages: ChatMessage[]) => {
  const children: Paragraph[] = [
    new Paragraph({ heading: HeadingLevel.TITLE, children: [new TextRun('AI试卷导出')] }),
    new Paragraph({})
  ]
  messages.forEach((message, index) => {
    children.push(new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(`第${index + 1}份试卷`)] }))
    children.push(new Paragraph({ children: [new TextRun(`生成时间：${message.timestamp.toLocaleString()}`)] }))
    children.push(...markdownToParagraphs(message.content || ''))
    children.push(new Paragraph({}))
  })
  const wordDocument = new Document({ sections: [{ properties: {}, children }] })
  const blob = await Packer.toBlob(wordDocument)
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `AI试卷_${new Date().toISOString().slice(0, 10)}.docx`
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 1000)
  ElMessage.success('导出成功')
}

const exportSelectedAiPapers = async () => {
  const selectedMessages = exportableAiMessages.value.filter((message) => selectedAiMessageIds.value.has(message.id))
  if (selectedMessages.length === 0) {
    ElMessage.warning('请先选择要导出的AI试卷')
    return
  }
  try {
    await createAndDownloadWord(selectedMessages)
  } catch {
    ElMessage.error('导出失败，请重试')
  }
}

const fetchPresetPrompts = async () => {
  loadingPresetPrompts.value = true
  try {
    const res = await apiClient.get('/teacher/exam-paper/v1/preset-prompts')
    if (res.data.success) {
      presetPrompts.value = Array.isArray(res.data.items) ? res.data.items : []
      return
    }
    ElMessage.error(res.data.error || '获取预设提示词失败')
  } catch (err: any) {
    ElMessage.error(err.response?.data?.error || '获取预设提示词失败')
  } finally {
    loadingPresetPrompts.value = false
  }
}

const applyPresetPrompt = (preset: PresetPrompt) => {
  const next = customRequirement.value.trim()
    ? `${customRequirement.value.trim()}\n${preset.promptContent}`
    : preset.promptContent
  customRequirement.value = next
  ElMessage.success('已填入补充要求')
}

const previewPreset = (preset: PresetPrompt) => {
  previewPresetPrompt.value = preset
  showPresetPreviewDialog.value = true
}

const openAddPresetDialog = () => {
  newPresetTitle.value = ''
  newPresetContent.value = ''
  showAddPresetDialog.value = true
}

const createPresetPrompt = async () => {
  if (!newPresetTitle.value.trim()) {
    ElMessage.warning('请输入预设名称')
    return
  }
  if (!newPresetContent.value.trim()) {
    ElMessage.warning('请输入预设内容')
    return
  }
  creatingPresetPrompt.value = true
  try {
    const res = await apiClient.post('/teacher/exam-paper/v1/preset-prompts', {
      title: newPresetTitle.value.trim(),
      promptContent: newPresetContent.value.trim()
    })
    if (res.data.success) {
      ElMessage.success('预设添加成功')
      showAddPresetDialog.value = false
      await fetchPresetPrompts()
      return
    }
    ElMessage.error(res.data.error || '预设添加失败')
  } catch (err: any) {
    ElMessage.error(err.response?.data?.error || '预设添加失败')
  } finally {
    creatingPresetPrompt.value = false
  }
}

const deletePresetPrompt = async (preset: PresetPrompt) => {
  try {
    await ElMessageBox.confirm(`确认删除预设「${preset.title}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    const res = await apiClient.delete(`/teacher/exam-paper/v1/preset-prompts/${preset.id}`)
    if (res.data.success) {
      ElMessage.success('预设已删除')
      await fetchPresetPrompts()
      return
    }
    ElMessage.error(res.data.error || '删除失败')
  } catch (err: any) {
    ElMessage.error(err.response?.data?.error || '删除失败')
  }
}

const fetchPaperHistory = async () => {
  loadingPaperHistory.value = true
  try {
    const res = await apiClient.get('/teacher/exam-paper/v1/list', { params: { page: 1, pageSize: 20 } })
    if (res.data.success) {
      paperHistory.value = Array.isArray(res.data.items) ? res.data.items : []
      return
    }
    ElMessage.error(res.data.error || '获取历史试卷失败')
  } catch (err: any) {
    ElMessage.error(err.response?.data?.error || '获取历史试卷失败')
  } finally {
    loadingPaperHistory.value = false
  }
}

const openHistoryDialog = async () => {
  showHistoryDialog.value = true
  await fetchPaperHistory()
}

const loadPaperDetail = async (paperId: number) => {
  try {
    const res = await apiClient.get(`/teacher/exam-paper/v1/${paperId}`)
    if (!res.data.success) {
      ElMessage.error(res.data.error || '加载试卷失败')
      return
    }
    chatHistory.value.push({
      role: 'ai',
      content: res.data.markdownContent || '该试卷暂无可展示内容',
      timestamp: new Date(res.data.createdAt || Date.now()),
      id: messageIdCounter++
    })
    currentPaperId.value = paperId
    showHistoryDialog.value = false
    ElMessage.success('已载入历史试卷')
  } catch (err: any) {
    ElMessage.error(err.response?.data?.error || '加载试卷失败')
  }
}

const normalizeTaskContent = (result: any): string => {
  if (!result || typeof result !== 'object') return ''
  return result.markdownContent || ''
}

const pollTaskResult = async (taskId: number) => {
  const maxAttempts = 60
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    await new Promise((resolve) => setTimeout(resolve, 1000))
    const statusRes = await apiClient.get(`/teacher/exam-paper/v1/tasks/${taskId}`)
    if (!statusRes.data.success) {
      throw new Error(statusRes.data.error || '查询任务状态失败')
    }
    const status = statusRes.data.status as TaskStatus
    taskStatus.value = status
    if (status === 'FAILED') {
      throw new Error(statusRes.data.errorMessage || '试卷生成失败')
    }
    if (status === 'SUCCESS') {
      if (statusRes.data.paperId) {
        const detailRes = await apiClient.get(`/teacher/exam-paper/v1/${statusRes.data.paperId}`)
        if (detailRes.data.success) {
          return detailRes.data.markdownContent || ''
        }
      }
      return normalizeTaskContent(statusRes.data.result)
    }
  }
  throw new Error('任务超时，请稍后重试')
}

const send = async () => {
  if (!subject.value.trim()) {
    ElMessage.warning('请输入科目')
    return
  }
  if (!normalizedGrade.value.trim()) {
    ElMessage.warning('请输入年级')
    return
  }
  if (!examType.value.trim()) {
    ElMessage.warning('请输入试卷类型')
    return
  }
  if (totalQuestionCount.value < 1) {
    ElMessage.warning('请至少设置一种题型数量')
    return
  }

  const userMessage = `${subject.value} ${normalizedGrade.value} ${examType.value} (${durationMinutes.value}分钟, ${totalScore.value}分, 共${totalQuestionCount.value}题)`
  chatHistory.value.push({ role: 'user', content: userMessage, timestamp: new Date(), id: messageIdCounter++ })

  loading.value = true
  taskStatus.value = 'PENDING'
  taskErrorMessage.value = ''
  try {
    const payload = {
      subject: subject.value.trim(),
      grade: normalizedGrade.value.trim(),
      examType: examType.value.trim(),
      durationMinutes: durationMinutes.value,
      totalScore: totalScore.value,
      questionCount: totalQuestionCount.value,
      questionTypeCounts: {
        选择题: Number(questionTypeCounts.value.选择题 || 0),
        填空题: Number(questionTypeCounts.value.填空题 || 0),
        判断题: Number(questionTypeCounts.value.判断题 || 0),
        简答题: Number(questionTypeCounts.value.简答题 || 0),
        解答题: Number(questionTypeCounts.value.解答题 || 0)
      },
      knowledgePoints: knowledgePoints.value.trim() || null,
      customRequirement: customRequirement.value.trim() || null
    }
    const taskRes = await apiClient.post('/teacher/exam-paper/v1/tasks', payload)
    if (!taskRes.data.success) {
      throw new Error(taskRes.data.error || '任务创建失败')
    }

    const markdownContent = await pollTaskResult(Number(taskRes.data.taskId))
    chatHistory.value.push({
      role: 'ai',
      content: markdownContent || '试卷生成成功，但无可展示内容',
      timestamp: new Date(),
      id: messageIdCounter++
    })
    await fetchPaperHistory()
    ElMessage.success('试卷生成完成')
  } catch (err: any) {
    taskStatus.value = 'FAILED'
    taskErrorMessage.value = err?.message || '未知错误'
    chatHistory.value.push({ role: 'ai', content: `生成失败：${taskErrorMessage.value}`, timestamp: new Date(), id: messageIdCounter++ })
    ElMessage.error(taskErrorMessage.value)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await fetchPresetPrompts()
})
</script>

<template>
  <div class="ai-plan-container">
    <section class="left-panel">
      <header class="panel-header">
        <div>
          <h2>AI 试卷工作台</h2>
          <p>输入命题需求，一键生成可导出试卷</p>
        </div>
        <div class="panel-actions">
          <el-button size="small" @click="openHistoryDialog">历史</el-button>
        </div>
      </header>

      <div class="status-overview">
        <div class="status-card">
          <span class="status-label">当前状态</span>
          <el-tag :type="taskStatusType" effect="light">{{ taskStatusText }}</el-tag>
        </div>
        <div class="status-card">
          <span class="status-label">对话轮次</span>
          <strong>{{ userMessageCount }} / {{ aiMessageCount }}</strong>
        </div>
      </div>

      <el-form label-position="top" class="plan-form">
        <el-form-item label="科目">
          <el-input v-model="subject" placeholder="例如：数学" :disabled="loading" />
        </el-form-item>
        <el-form-item label="年级">
          <el-cascader
            v-model="grade"
            :options="educationOptions"
            :props="{ expandTrigger: 'hover' }"
            placeholder="请选择年级"
            style="width: 100%"
            :disabled="loading"
          />
        </el-form-item>
        <el-form-item label="试卷类型">
          <el-input v-model="examType" placeholder="例如：期中测试" :disabled="loading" />
        </el-form-item>

        <div class="number-grid">
          <el-form-item label="考试时长(分钟)">
            <el-input-number v-model="durationMinutes" :min="30" :max="180" :disabled="loading" />
          </el-form-item>
          <el-form-item label="总分">
            <el-input-number v-model="totalScore" :min="20" :max="200" :disabled="loading" />
          </el-form-item>
        </div>

        <div class="number-grid">
          <el-form-item label="选择题数量">
            <el-input-number v-model="questionTypeCounts['选择题']" :min="0" :max="60" :disabled="loading" />
          </el-form-item>
          <el-form-item label="填空题数量">
            <el-input-number v-model="questionTypeCounts['填空题']" :min="0" :max="60" :disabled="loading" />
          </el-form-item>
          <el-form-item label="判断题数量">
            <el-input-number v-model="questionTypeCounts['判断题']" :min="0" :max="60" :disabled="loading" />
          </el-form-item>
          <el-form-item label="简答题数量">
            <el-input-number v-model="questionTypeCounts['简答题']" :min="0" :max="60" :disabled="loading" />
          </el-form-item>
          <el-form-item label="解答题数量">
            <el-input-number v-model="questionTypeCounts['解答题']" :min="0" :max="60" :disabled="loading" />
          </el-form-item>
          <el-form-item label="题量合计">
            <el-input :model-value="`${totalQuestionCount} 题`" disabled />
          </el-form-item>
        </div>
        <div class="difficulty-hint">默认难度为「中等」。如需改成简单或较难，请在“补充要求”中说明。</div>

        <el-form-item label="重点知识点（选填）">
          <el-input v-model="knowledgePoints" placeholder="例如：二次函数、方程组、图像应用" :disabled="loading" />
        </el-form-item>

        <el-form-item label="补充要求（选填）">
          <el-input
            v-model="customRequirement"
            type="textarea"
            :rows="4"
            :disabled="loading"
            placeholder="例如：增加情境题，解析强调易错点"
          />
          <div class="preset-prompt-panel">
            <div class="preset-prompt-header">
              <span>预设 Prompt</span>
              <div class="preset-prompt-actions">
                <el-button size="small" text @click="fetchPresetPrompts" :loading="loadingPresetPrompts">刷新</el-button>
                <el-button size="small" text type="primary" @click="openAddPresetDialog">新增</el-button>
              </div>
            </div>
            <el-empty v-if="!loadingPresetPrompts && presetPrompts.length === 0" description="暂无预设Prompt" :image-size="56" />
            <div v-else class="preset-prompt-list">
              <div v-for="preset in presetPrompts" :key="preset.id" class="preset-prompt-item">
                <div class="preset-prompt-main">
                  <div class="preset-prompt-title">
                    <span>{{ preset.title }}</span>
                    <el-tag size="small" :type="preset.systemDefault ? 'success' : 'info'" effect="plain">
                      {{ preset.systemDefault ? '系统默认' : '我的新增' }}
                    </el-tag>
                  </div>
                  <div class="preset-prompt-preview">{{ preset.promptContent }}</div>
                </div>
                <div class="preset-prompt-item-actions">
                  <el-button size="small" text @click="previewPreset(preset)">预览</el-button>
                  <el-button size="small" text type="primary" @click="applyPresetPrompt(preset)">使用</el-button>
                  <el-button v-if="!preset.systemDefault" size="small" text type="danger" @click="deletePresetPrompt(preset)">
                    删除
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </el-form-item>

        <el-button type="primary" class="submit-btn" @click="send" :loading="loading">生成试卷</el-button>
        <div class="task-panel-error" v-if="taskStatus === 'FAILED'">失败原因：{{ taskErrorMessage || '未知错误' }}</div>
      </el-form>
    </section>

    <section class="right-panel">
      <header class="chat-header">
        <h3>试卷输出</h3>
        <div class="chat-actions">
          <span class="chat-sub">AI 输出支持 Markdown / LaTeX 渲染</span>
          <el-button size="small" @click="toggleSelectAllAiMessages" :disabled="!hasAnyExportableAiMessage">
            {{ isAllAiMessageSelected ? '取消全选试卷' : '全选试卷' }}
          </el-button>
          <el-button size="small" type="success" @click="exportSelectedAiPapers" :disabled="!hasSelectedAiMessage">
            导出Word
          </el-button>
        </div>
      </header>

      <div class="chat-body">
        <div
          v-for="message in chatHistory"
          :key="message.id"
          class="message-row"
          :class="message.role"
        >
          <div class="avatar">{{ message.role === 'user' ? '👤' : '🤖' }}</div>
          <div class="message-wrap">
            <div class="message-header">
              <strong>{{ message.role === 'user' ? '您' : 'AI助手' }}</strong>
              <div class="message-meta">
                <el-button
                  v-if="isExportableAiMessage(message)"
                  text
                  type="primary"
                  size="small"
                  @click="toggleAiMessageSelection(message.id)"
                >
                  {{ selectedAiMessageIds.has(message.id) ? '已选择导出' : '选择导出' }}
                </el-button>
                <span class="timestamp">{{ message.timestamp.toLocaleTimeString() }}</span>
              </div>
            </div>
            <el-card :shadow="message.role === 'user' ? 'never' : 'hover'" :class="['message-content', message.role]">
              <template v-if="message.role === 'ai'">
                <div class="markdown-body" v-html="renderMarkdown(message.content)"></div>
              </template>
              <template v-else>
                <div class="user-content">{{ message.content }}</div>
              </template>
            </el-card>
          </div>
        </div>
      </div>
    </section>

    <el-dialog v-model="showHistoryDialog" title="历史试卷" width="720px">
      <div class="history-dialog-content">
        <el-empty v-if="paperHistory.length === 0 && !loadingPaperHistory" description="暂无历史试卷" />
        <div v-else class="history-list">
          <div
            v-for="item in paperHistory"
            :key="item.id"
            class="history-item"
            :class="{ active: item.id === currentPaperId }"
            @click="loadPaperDetail(item.id)"
          >
            <el-icon class="history-icon"><ChatDotRound /></el-icon>
            <div class="history-info">
              <div class="history-id">{{ item.title }}</div>
              <div class="history-time">
                {{ item.subject }} · {{ item.grade }} · {{ item.examType }} · {{ new Date(item.createdAt).toLocaleString() }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="showPresetPreviewDialog" title="预设Prompt预览" width="640px">
      <div class="preset-preview-content">{{ previewPresetPrompt?.promptContent }}</div>
    </el-dialog>

    <el-dialog v-model="showAddPresetDialog" title="新增预设Prompt" width="640px">
      <el-form label-position="top">
        <el-form-item label="预设名称">
          <el-input v-model="newPresetTitle" maxlength="100" placeholder="例如：情境应用加强版" />
        </el-form-item>
        <el-form-item label="预设内容">
          <el-input
            v-model="newPresetContent"
            type="textarea"
            :rows="6"
            maxlength="3000"
            show-word-limit
            placeholder="请输入希望填入“补充要求”的Prompt内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddPresetDialog = false">取消</el-button>
        <el-button type="primary" :loading="creatingPresetPrompt" @click="createPresetPrompt">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ai-plan-container {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 16px;
  width: 100%;
  min-height: calc(100vh - 120px);
  background: linear-gradient(180deg, #f8fbff 0%, #f3f6fb 100%);
  border-radius: 14px;
}

.left-panel,
.right-panel {
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.left-panel {
  padding: 18px;
  border: 1px solid #e8eef8;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.panel-header h2 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.panel-header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.panel-actions {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.status-overview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 14px;
}

.status-card {
  border-radius: 10px;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.status-label {
  color: #64748b;
  font-size: 12px;
}

.plan-form {
  margin-top: 8px;
}

.number-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.difficulty-hint {
  margin-top: -4px;
  margin-bottom: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
  color: #1e3a8a;
  font-size: 12px;
}

.submit-btn {
  width: 100%;
  height: 42px;
  margin-top: 4px;
  border-radius: 10px;
  font-weight: 600;
}

.task-panel-error {
  color: #dc2626;
  margin-top: 10px;
  font-size: 13px;
  background: #fff1f2;
  border: 1px solid #fecdd3;
  padding: 8px 10px;
  border-radius: 8px;
}

.right-panel {
  display: flex;
  flex-direction: column;
  min-height: 720px;
  border: 1px solid #e8eef8;
}

.chat-header {
  padding: 16px 18px;
  border-bottom: 1px solid #edf2f7;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-header h3 {
  margin: 0;
  font-size: 17px;
  color: #111827;
}

.chat-sub {
  color: #94a3b8;
  font-size: 12px;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
}

.message-row {
  display: grid;
  grid-template-columns: 34px 1fr;
  gap: 10px;
  margin-bottom: 14px;
}

.message-row.user {
  grid-template-columns: 1fr 34px;
}

.message-row.user .avatar {
  order: 2;
}

.message-row.user .message-wrap {
  order: 1;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2ff;
  border: 1px solid #dbeafe;
  font-size: 16px;
}

.message-content {
  border-radius: 12px;
}

.timestamp {
  color: #9aa7b9;
  font-size: 12px;
}

.message-content.user {
  background: #eef6ff;
  border: 1px solid #dbeafe;
}

.message-content.ai {
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.user-content {
  white-space: pre-wrap;
  line-height: 1.75;
  color: #1e293b;
}

:deep(.markdown-body) {
  line-height: 1.85;
  color: #334155;
  font-size: 14px;
}

:deep(.markdown-body h1),
:deep(.markdown-body h2),
:deep(.markdown-body h3) {
  margin-top: 16px;
  margin-bottom: 8px;
  color: #0f172a;
}

:deep(.markdown-body p) {
  margin: 7px 0;
}

:deep(.markdown-body ul),
:deep(.markdown-body ol) {
  padding-left: 20px;
  margin: 8px 0;
}

:deep(.fill-blank) {
  letter-spacing: 1.2px;
  color: #111827;
}

.history-dialog-content {
  max-height: 520px;
  overflow-y: auto;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.history-item:hover {
  border-color: #93c5fd;
  background: #f8fbff;
}

.history-item.active {
  border-color: #409eff;
  background: #f5faff;
}

.history-icon {
  color: #409eff;
  margin-top: 2px;
}

.history-id {
  font-weight: 600;
}

.history-time {
  color: #909399;
  font-size: 12px;
}

.preset-prompt-panel {
  margin-top: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px;
  background: #f8fafc;
}

.preset-prompt-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
}

.preset-prompt-actions {
  display: flex;
  gap: 6px;
}

.preset-prompt-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
}

.preset-prompt-item {
  border: 1px solid #dbe5f2;
  border-radius: 8px;
  background: #fff;
  padding: 8px 10px;
}

.preset-prompt-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preset-prompt-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: #0f172a;
  font-weight: 600;
}

.preset-prompt-preview {
  font-size: 12px;
  color: #64748b;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.preset-prompt-item-actions {
  margin-top: 6px;
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}

.preset-preview-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #334155;
  max-height: 420px;
  overflow-y: auto;
}

@media (max-width: 1180px) {
  .ai-plan-container {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .right-panel {
    min-height: 620px;
  }
}
</style>
