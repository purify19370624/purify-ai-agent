/**
 * 检查首页文案里有没有混进「只有做过这个项目的人才看得懂」的词。
 *
 * <p>跑法：`npm run check:copy`。
 *
 * <h2>为什么需要它</h2>
 *
 * <p>首页是唯一一页**给还没用过的人看**的界面，所以它的文案有一条别的页面没有的约束：
 * 不出现框架名、端口号、技术栈，也不出现「链路 / 索引 / 向量检索 / MCP / 多步完成」
 * 这类词。这条约束已经**漂回去过两次**：第一版主标语上方那颗标签写的是
 * 「Spring AI 驱动」，卡片小标签写的是「工具调用 · MCP · 循环自检」；
 * 第一轮改完之后，页脚小字里还留着「本地部署 · Spring AI + pgvector」。
 *
 * <p>两次都是**人工review才发现的**——这类词不会让任何东西报错，也不会让页面变丑，
 * 只是让用户在第一屏读到一句跟他无关的话。所以规则做成脚本，让它自己盯着。
 *
 * <h2>它检查什么</h2>
 *
 * <p>只查 `home` 这一块（两种语言都查）里的字符串叶子。**报错时给出键名和那个词**，
 * 因为「首页有一个词不对」这种提示等于没提示。
 *
 * <p>词表是**故意写窄**的：只收那些确定属于「实现细节」的词。像「知识库」这种
 * 用户能看懂、而且确实是功能名的词不在表里；真要放宽或收紧，改下面的数组即可
 * ——但改之前先读一遍 `src/i18n/zh-CN.js` 里 `home` 那块开头的两条规矩。
 */

import zhCN from '../src/i18n/zh-CN.js'
import enUS from '../src/i18n/en-US.js'

/**
 * 不该出现在首页文案里的词。
 *
 * <p>英文那几个用 `\b` 卡住词边界：不卡的话 `port` 会命中 `support`、
 * `index` 会命中 `indexing` 之外的一堆东西，脚本会变成「改个文案就报错」的噪声源。
 * 中文不卡边界（本来就没有词边界），所以中文词要选得足够长、足够独特，
 * 比如用「检索」而不是「检」。
 */
const FORBIDDEN = [
  // 框架 / 产品 / 数据库名
  /Spring\s*AI/i,
  /pgvector/i,
  /\bVue\b/i,
  /\bSpring\b/i,
  /\bJava\b/i,
  /\bPostgres(?:QL)?\b/i,
  /\bMySQL\b/i,
  /\bDocker\b/i,
  // 工程概念
  /\bMCP\b/,
  /\bRAG\b/,
  /\bAPI\b/,
  /\bLLM\b/i,
  /\bprompt\b/i,
  /\bvector\b/i,
  /\bretriev\w*/i,
  /\bindex(?:es|ing)?\b/i,
  /\bmulti-?step\b/i,
  /\bself-hosted\b/i,
  /\bport\b/i,
  /链路/,
  /索引/,
  /向量/,
  /检索/,
  /多步/,
  /自检/,
  /端口/,
  /部署/,
  /前端/,
  /后端/,
  /Prompt/,
]

/** 递归收集一种语言里 `home` 那块的所有字符串叶子。 */
function collectTexts(value, prefix = '', out = []) {
  if (Array.isArray(value)) {
    value.forEach((item, index) => collectTexts(item, `${prefix}[${index}]`, out))
  } else if (value !== null && typeof value === 'object') {
    for (const [name, child] of Object.entries(value)) {
      collectTexts(child, prefix ? `${prefix}.${name}` : name, out)
    }
  } else if (typeof value === 'string') {
    out.push([prefix, value])
  }
  return out
}

const problems = []

for (const [locale, pack] of [
  ['zh-CN', zhCN],
  ['en-US', enUS],
]) {
  if (!pack.home) {
    problems.push(`${locale} 里没有 home 那一块（键被改名或被挪走了？这个脚本按 home 找）`)
    continue
  }
  for (const [key, text] of collectTexts(pack.home)) {
    const hits = new Set()
    for (const pattern of FORBIDDEN) {
      const hit = text.match(pattern)
      // 报命中的**原文**而不是正则：正则里带 \b 和分组，直接打出来没人想读。
      // 用 Set 去重是因为一个词可能同时命中两条规则（「Spring AI」也会命中「Spring」），
      // 同一条文案被列三遍只会让人觉得误报
      if (hit) hits.add(hit[0])
    }
    if (hits.size) {
      problems.push(
        `${locale} 的 home.${key} 里出现了 ${[...hits].map((w) => `「${w}」`).join('、')}：${text}`,
      )
    }
  }
}

if (problems.length) {
  console.error(`首页文案里混进了开发者才看得懂的词，共 ${problems.length} 处：\n`)
  for (const problem of problems) console.error(`  - ${problem}`)
  console.error(
    '\n首页是给还没用过的人看的：把实现细节换成「这东西能帮我做什么」。\n' +
      '判断标准写在 src/i18n/zh-CN.js 里 home 那块的开头。\n' +
      '技术栈真要记一笔，写进仓库文档或代码注释，不要占首页的地方。',
  )
  process.exit(1)
}

console.log('首页文案检查通过：没有框架名、端口号或工程术语。')
