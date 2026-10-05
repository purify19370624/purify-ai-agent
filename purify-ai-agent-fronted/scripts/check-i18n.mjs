/**
 * 检查中英两个语言包的键是否一一对应，以及**代码里用到的键是不是都存在**。
 *
 * <p>跑法：`npm run check:i18n`。
 *
 * <h2>为什么需要它</h2>
 *
 * vue-i18n 找不到键时**不会报错**：它会退回 `fallbackLocale`（这里配的是中文），
 * 只在控制台打一句警告。于是「英文包里漏了一条」的表现是
 * 「界面全英文，某处突然冒出一句中文」，而且只有真的走到那个分支才看得见
 * ——恰恰是错误提示、空状态这类不常出现的分支最容易漏。
 *
 * 后端有 `MessageBundleParityTest` 盯着同一件事，这边用这个脚本对上。
 * 前端没有测试框架（项目里一个都没有），所以不为了这一件事引入一整套 vitest，
 * 一个 node 脚本够用：两个语言包都是纯 ES 模块，`import` 进来直接比即可。
 *
 * <h2>它检查什么</h2>
 *
 * 1. **键集合完全一致**（递归比到叶子）；
 * 2. **数组的长度一致**（示例问题那种：中文写 4 条、英文写 3 条，
 *    键路径全都在，但第 4 条在英文下会是 undefined）；
 * 3. **占位符一致**（`{n}` / `{title}` 这类）。vue-i18n 对缺失的具名参数
 *    不会抛异常，只会把占位符原样留在文案里——用户看到的是「命中 {count} 条」；
 * 4. **代码里调用的键在语言包里存在**（见下面 `usedKeys` 那一块）。
 *    这一条是后来补的：搬页面的时候把 `settings.profile.summary.*` 写成了
 *    `profile.summary.*`，表现是**界面上直接显示出键名**
 *    （`profile.summary.age · profile.summary.height · …`），
 *    而前三条一条都拦不住它——两个语言包自己是对齐的。
 */

import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

import zhCN from '../src/i18n/zh-CN.js'
import enUS from '../src/i18n/en-US.js'

/** 递归收集所有叶子节点的路径，数组按 `路径[下标]` 展开。 */
function collectKeys(value, prefix = '') {
  const keys = []
  if (Array.isArray(value)) {
    keys.push(`${prefix}#length=${value.length}`)
    value.forEach((item, index) => keys.push(...collectKeys(item, `${prefix}[${index}]`)))
  } else if (value !== null && typeof value === 'object') {
    for (const [name, child] of Object.entries(value)) {
      keys.push(...collectKeys(child, prefix ? `${prefix}.${name}` : name))
    }
  } else {
    keys.push(prefix)
  }
  return keys
}

/** 一条文案里的具名占位符，比如 {n} {title}。 */
function placeholdersOf(text) {
  if (typeof text !== 'string') return new Set()
  return new Set([...text.matchAll(/\{([A-Za-z_][A-Za-z0-9_]*)}/g)].map((m) => m[1]))
}

/** 递归拿到「路径 → 文案」，只收字符串叶子。 */
function collectTexts(value, prefix = '', out = new Map()) {
  if (Array.isArray(value)) {
    value.forEach((item, index) => collectTexts(item, `${prefix}[${index}]`, out))
  } else if (value !== null && typeof value === 'object') {
    for (const [name, child] of Object.entries(value)) {
      collectTexts(child, prefix ? `${prefix}.${name}` : name, out)
    }
  } else if (typeof value === 'string') {
    out.set(prefix, value)
  }
  return out
}

const problems = []

const zhKeys = new Set(collectKeys(zhCN))
const enKeys = new Set(collectKeys(enUS))

for (const key of zhKeys) {
  if (!enKeys.has(key)) {
    // 数组长度那一条的报错要说得更直白些，否则读到 `chat.slim.examples#length=4` 会懵
    problems.push(
      key.includes('#length=')
        ? `数组长度不一致：${key.replace('#length=', ' 中文有 ')} 条（英文那边数量对不上）`
        : `英文包里缺键：${key}`,
    )
  }
}
for (const key of enKeys) {
  if (!zhKeys.has(key)) problems.push(`中文包里缺键：${key}`)
}

const zhTexts = collectTexts(zhCN)
const enTexts = collectTexts(enUS)
for (const [key, text] of zhTexts) {
  if (!enTexts.has(key)) continue
  const zhPlaceholders = [...placeholdersOf(text)].sort().join(',')
  const enPlaceholders = [...placeholdersOf(enTexts.get(key))].sort().join(',')
  if (zhPlaceholders !== enPlaceholders) {
    problems.push(`占位符不一致：${key} —— 中文 {${zhPlaceholders}} vs 英文 {${enPlaceholders}}`)
  }
}

/* --------------------------------------------------------------------------
 * 代码里用到的键是不是都存在
 *
 * 只认**字面量**：`t('settings.profile.summary.age')` / `$t('menu.profile')` /
 * `message('profile.loadFailed')`。拼出来的键（`$t(cell.key)`、`t(CONFIG[k])`）
 * 看不见，它们的对应关系在代码里那几张「枚举名 / 常量 → 键」的表上，由人保证——
 * 这也是为什么 REMEMBER_ITEM_KEYS 那种表旁边都写着「加字段时两边要一起改」。
 * ------------------------------------------------------------------------ */

const SRC_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'src')

/** 递归收 src 下的 .js / .vue。 */
function walk(dir, out = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) walk(full, out)
    else if (/\.(js|vue)$/.test(entry.name)) out.push(full)
  }
  return out
}

/**
 * 一段代码里那些「看着像文案键的字面量」。
 *
 * 匹配的是 `t(` / `$t(` 紧跟的字面量，**外加 `message(`** —— 那是
 * `i18n/index.js` 里那个「先存键、渲染时再翻」的描述符（`resolveMessage`
 * 负责把它翻出来）。不认 `message(` 的话，「加载失败」「保存失败」这一大批
 * 文案（它们只从 catch 分支里取，所以最该被检查）全都在检查范围之外。
 * `rawMessage(` 不收：它包的是后端发过来的原文或者已经拼好的字符串，不是键。
 *
 * 负向断言排除掉了 `i18n.global.t(` 和 `format(`、`set(` 这类同样以 t 结尾的
 * 标识符；键的形状（小写开头、至少一个点、只有字母数字下划线）把
 * `t('…')` 里那些不是键的东西挡在外面。
 */
function usedKeysIn(text) {
  const keys = []
  const pattern = /(?<![\w.$])(?:\$?t|message)\(\s*['"]([a-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)['"]/g
  for (const match of text.matchAll(pattern)) keys.push(match[1])
  return keys
}

/** 键 → 用到它的文件（相对 src/，报错时指得出地方）。 */
const usedKeys = new Map()
for (const file of walk(SRC_DIR)) {
  const where = relative(SRC_DIR, file).split(sep).join('/')
  for (const key of usedKeysIn(readFileSync(file, 'utf8'))) {
    if (!usedKeys.has(key)) usedKeys.set(key, new Set())
    usedKeys.get(key).add(where)
  }
}

for (const [key, files] of usedKeys) {
  // 已经是某个键的**前缀**时不算错：`$t('profile.weight')` 和
  // `profile.weight.title` 撞的只是前缀，vue-i18n 按整段查表，
  // 这里报错只会让人去加一个谁也用不上的键
  const isPrefix = [...zhKeys].some((known) => known.startsWith(`${key}.`))
  if (!zhKeys.has(key) && !isPrefix) {
    problems.push(`代码里用了、语言包里没有的键：${key}（${[...files].join('、')}）`)
  }
}

/**
 * 反过来的那一半：语言包里没人用的键。
 *
 * **只提示，不算错**：拼出来的键看不见（见上面），所以「没人用」里混着一批
 * 真的在用的。把它列出来是为了让人一眼扫过去——搬页面时留下的孤儿键，
 * 和形状相似的键写错前缀（`profile.summary` vs `settings.profile.summary`），
 * 都会在这里露出来。
 */
const unused = []
for (const key of zhKeys) {
  if (key.includes('#length=')) continue
  if (!usedKeys.has(key)) unused.push(key)
}

if (problems.length) {
  console.error(`语言包对不上，共 ${problems.length} 处：\n`)
  for (const problem of problems) console.error(`  - ${problem}`)
  console.error('\n两个语言包（src/i18n/zh-CN.js 和 en-US.js）要一起改；')
  console.error('代码里写错前缀的键，改代码或者补语言包都可以，但别放着不管。')
  process.exit(1)
}

console.log(`语言包一致：${zhKeys.size} 条键，中英齐平；代码里用到的 ${usedKeys.size} 个键都在。`)
if (unused.length) {
  // 不报错，只列出来（理由见上面 unused 那段注释）
  console.log(`\n以下 ${unused.length} 条键没有在代码里直接出现（可能是拼出来的键，也可能是搬页面留下的孤儿）：`)
  for (const key of unused) console.log(`  - ${key}`)
}
