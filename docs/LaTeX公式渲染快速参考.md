# LaTeX 公式渲染快速参考

## 核心原理

**占位符保护法**: 在 Markdown 解析前提取公式，避免特殊字符被转义。

## 四步渲染流程

```typescript
// 步骤 1: 提取并保护 LaTeX 公式
const protectedText = protectLatexFormulas(markdownContent)

// 步骤 2: 安全解析 Markdown
const html = await marked.parse(protectedText)

// 步骤 3: 渲染被保护的 LaTeX 公式
const withLatex = renderProtectedLatex(html)

// 步骤 4: 清理 HTML
const cleanHtml = DOMPurify.sanitize(withLatex)
```

## 核心代码

### 1. 提取公式

```typescript
const LATEX_PLACEHOLDER_PREFIX = 'LATEXFORMULA'
const latexFormulaStore: Map<string, { formula: string; displayMode: boolean }> = new Map()

const protectLatexFormulas = (text: string): string => {
  latexFormulaStore.clear()
  let counter = 0
  
  // 块级公式 \[...\]
  text = text.replace(/\\\[([\s\S]*?)\\\]/g, (match, formula) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}DISPLAY${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode: true })
    counter++
    return placeholder
  })
  
  // 行内公式 \(...\)
  text = text.replace(/\\\(([\s\S]*?)\\\)/g, (match, formula) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}INLINE${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode: false })
    counter++
    return placeholder
  })
  
  return text
}
```

### 2. 渲染公式

```typescript
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
  return html
}
```

## 使用示例

```vue
<script setup lang="ts">
import { marked } from 'marked'
import katex from 'katex'
import DOMPurify from 'dompurify'
import 'katex/dist/katex.min.css'

// ... 复制上面的核心代码 ...

// 处理 AI 响应
const handleAIResponse = async (content: string) => {
  const protectedText = protectLatexFormulas(content)
  const html = await marked.parse(protectedText)
  const withLatex = renderProtectedLatex(html)
  const cleanHtml = DOMPurify.sanitize(withLatex)
  
  chatHistory.value.push({
    role: 'ai',
    content: cleanHtml,
    rawContent: content,
    timestamp: new Date()
  })
}
</script>
```

## 依赖安装

```bash
npm install katex marked dompurify
npm install --save-dev @types/katex @types/marked
```

## 问题对比

### 之前的问题

```
输入: \( f'(x) = \cos(x) \)
输出: f&#39;(x) = \cos(x)  ❌
```

### 现在的效果

```
输入: \( f'(x) = \cos(x) \)
输出: [完美渲染的数学公式]  ✅
```

## 关键点

1. ✅ 占位符不包含下划线（避免被 Markdown 解析）
2. ✅ 先处理块级后处理行内（避免嵌套冲突）
3. ✅ 使用 Map 存储（高效查找）
4. ✅ trim() 清理空白（保持公式整洁）
5. ✅ throwOnError: false（单个失败不影响整体）

## 测试验证

```javascript
// 测试脚本
const test = async () => {
  const input = `\\[ f'(x) = \\cos(2x + \\frac{\\pi}{3}) \\]`
  
  const protectedText = protectLatexFormulas(input)
  const html = await marked.parse(protectedText)
  const result = renderProtectedLatex(html)
  
  console.log('包含 KaTeX:', result.includes('class="katex"'))
  console.log('无转义问题:', !result.includes('&#39;'))
  console.log('无未渲染:', !result.includes('\\cos'))
}
```

## 常见问题

**Q: 占位符会冲突吗？**  
A: `LATEXFORMULAINLINE0ENDLATEX` 格式极不可能出现在正常文本中。

**Q: 为什么先处理块级？**  
A: 避免块级公式内的行内公式被错误识别。

**Q: 渲染失败怎么办？**  
A: `throwOnError: false` 确保单个公式失败不影响其他公式。

## 完整文档

详细文档请查看: `docs/LaTeX数学公式渲染实现文档.md`

---

**版本**: v2.0  
**日期**: 2025-10-17
