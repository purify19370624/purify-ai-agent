/**
 * 体重数据的几个纯函数 —— 体重变化页和曲线组件共用一份。
 *
 * 抽出来的理由和 `knowledgeCategory.js` 一样：**同一条规则一旦两边各写一份，
 * 漂移的表现是「列表说减了 1.7kg、图上看着像涨」**，而那种不一致不会报错，
 * 只会让人怀疑数据本身。这里放的都是没有状态、没有副作用的计算。
 *
 * 后端的 `recordedAt` 是**不带时区的** `LocalDateTime` 字符串
 * （`"2026-09-27T10:30:00"`）。解析它时只在**这一份数据内部**互相比较、
 * 或者算「距今多少天」，所以浏览器按本地时区解释它是一致的；
 * 真正要显示给人看的日期，见 `formatMoment()` 里的截字符串做法。
 */

/** 68 → "68"，68.5 → "68.5"。压掉浮点噪声，也去掉 DECIMAL(5,1) 读出来那个多余的 .0。 */
export function formatKg(value) {
  if (!Number.isFinite(value)) return '—'
  return String(Number(value.toFixed(1)))
}

/**
 * 解析 `recordedAt`。解析不了返回 `null`（而不是 Invalid Date 往下传——
 * 一个 NaN 时间会让整张图的横轴变成 NaN，浏览器不报错，只是什么都不画）。
 */
export function parseRecordedAt(text) {
  if (typeof text !== 'string' || !text) return null
  // 不带时区的日期时间按本地时间解释，这是 ES2016 之后各浏览器的统一行为
  const parsed = new Date(text)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

/** 只到「月-日」。跨年的老记录带上年份，否则 1 月和 12 月看起来像同一年。 */
export function formatDay(text) {
  if (typeof text !== 'string' || text.length < 10) return ''
  const day = text.slice(5, 10)
  const year = text.slice(0, 4)
  return year === String(new Date().getFullYear()) ? day : `${year}-${day}`
}

/** 时分。列表里要能看出「早上称的还是晚上称的」。 */
export function formatTime(text) {
  return typeof text === 'string' && text.length >= 16 ? text.slice(11, 16) : ''
}

/**
 * 最新一条记录。后端保证新的在前，但这里仍然按时间找一遍：
 * 页面上的「当前体重」拿错一条就会整页数字都跟着错。
 *
 * @param {Array} points 后端的 `weightHistory`，新的在前
 */
export function latestPoint(points) {
  return (points ?? []).reduce((newest, point) => {
    if (!Number.isFinite(point?.weightKg)) return newest
    if (!newest) return point
    const a = parseRecordedAt(point.recordedAt)
    const b = parseRecordedAt(newest.recordedAt)
    if (!a) return newest
    return !b || a > b ? point : newest
  }, null)
}

/**
 * 「距今 N 天变化了多少」。
 *
 * 基准取**最新一条之前、且离 N 天这个界线最近的那一条**（也就是那条时间 <= 界线的最新记录）。
 * 不取「N 天前那天正好有的那一条」：没人会天天称，找精确的那一天必然是空。
 *
 * **数据不够 N 天时返回 `null`**，由调用方显示「记录还不够 7 天」——
 * 拿最早那条硬算会得到一个「较 7 天前 -0.3」，而用户 7 天前压根没记录过。
 *
 * @returns {{from: object, to: object, diff: number}|null}
 */
export function changeOverDays(points, days) {
  const usable = (points ?? []).filter((point) => Number.isFinite(point?.weightKg))
  const newest = latestPoint(usable)
  const newestAt = parseRecordedAt(newest?.recordedAt)
  if (!newest || !newestAt) return null

  const cutoff = newestAt.getTime() - days * 24 * 60 * 60 * 1000
  let baseline = null
  for (const point of usable) {
    const at = parseRecordedAt(point.recordedAt)
    // <= cutoff：这条记录就已经在界线之外了，拿它当基准
    if (at && at.getTime() <= cutoff && (!baseline || at > parseRecordedAt(baseline.recordedAt))) {
      baseline = point
    }
  }
  // 最早的那条都还在窗口里 → 这份数据还不够 N 天
  if (!baseline) return null
  return {
    from: baseline,
    to: newest,
    diff: Math.round((newest.weightKg - baseline.weightKg) * 10) / 10,
  }
}

/**
 * 相邻两次的差值，给列表里那一列用。
 *
 * @param {Array} points 新的在前
 * @returns {Array<number|null>} 与 points 一一对应；最后一条（最早那条）是 null
 */
export function stepDiffs(points) {
  const list = points ?? []
  return list.map((point, index) => {
    const previous = list[index + 1]
    if (!previous || !Number.isFinite(point?.weightKg) || !Number.isFinite(previous?.weightKg)) {
      return null
    }
    return Math.round((point.weightKg - previous.weightKg) * 10) / 10
  })
}
