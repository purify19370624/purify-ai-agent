<script setup>
/**
 * 体重变化曲线。
 *
 * 纯展示组件：一个 `points` prop，没有状态、没有副作用、不发请求。
 * 两个地方用它——设置抽屉里那一小块预览，和体重变化页上的正图。
 * 尺寸靠 viewBox 等比缩放，所以同一个组件在 380px 的抽屉和 900px 的页面里都成立。
 *
 * 不引图表库：整个项目没有第二个图表，为一条折线多装一个依赖、多打一份包不划算，
 * 和 `AppIcon.vue` 不引图标库是同一个理由。几何用 computed 算，不用 watch——
 * 这个项目里一处 watch 都没有。
 *
 * <h2>这一版修掉的三处「看不出来」</h2>
 *
 * 1. **纵轴没有刻度**：原来只能从「较最早减少 1.7kg」那一行文字反推，图本身
 *    不告诉你现在是 71 还是 91。现在左侧有上/中/下三条刻度和网格线。
 * 2. **横轴按序号等距**：那是「只有 10 个点的滚动窗口」时代的权宜之计——
 *    按序号画，隔了一个月和隔了一天看起来一样宽。数据源改成完整流水之后
 *    （见 `ProfileService#history`），按真实时间画才有意义：**没称的那几天会留白**，
 *    而那正是用户想知道的事。跨度不足一天时退回等距，否则分母是 0。
 * 3. **每个点读不出数**：现在每个圆点带一个 `<title>`，鼠标停上去显示日期和体重；
 *    触屏和读屏拿不到 tooltip，所以具体数字在页面上的记录列表里也列了一遍。
 */
import { computed } from 'vue'
import { isChatDark } from '../theme.js'
import { t } from '../i18n/index.js'
import { formatDay, formatKg, parseRecordedAt } from '../weightData.js'

const props = defineProps({
  /** 后端给的体重流水，**新的在前**（`ORDER BY recorded_at DESC`）。 */
  points: { type: Array, default: () => [] },
})

/*
 * 画布尺寸。
 *
 * 左边留 46 是给纵轴刻度的：那三个数字要写在图外面，写进去会压住折线。
 * 固定 viewBox 再等比缩放，不用 preserveAspectRatio="none"——
 * 非等比缩放会把线宽横向拉粗，抽屉和页面宽度不同会得到两种观感。
 */
const W = 720
const H = 260
const PAD_LEFT = 46
const PAD_RIGHT = 12
const PAD_TOP = 14
const PAD_BOTTOM = 26

/**
 * 可画的点，**老的在前**（画出来才是从左到右）。
 *
 * 后端给的是新的在前，所以这里 reverse 一次。顺手滤掉拿不到体重的项：
 * null 经过 Number() 会变成 0，把整条曲线的纵轴范围一把拉到 0 附近，
 * 看起来像「掉下去了」——那是编出来的趋势，比不画更糟。
 */
const series = computed(() =>
  (props.points ?? []).filter((p) => Number.isFinite(p?.weightKg)).reverse(),
)

const earliest = computed(() => series.value[0])
const latest = computed(() => series.value[series.value.length - 1])

/**
 * 纵轴范围。
 *
 * 跨度为 0（只有一个点，或者几次一样重）时上下各留 1kg：否则 hi-lo 是 0，
 * 下面那个除法算出 NaN，polyline 的 points 会变成一串 NaN——浏览器不报错，
 * 只是一个点都不画，看起来像「图坏了」，没有任何线索。
 *
 * 正常时上下各留 8% 余量：不留的话最重/最轻那一点正好压在画布边缘上，
 * 描边被裁掉一半。纵轴**不从 0 开始**是有意的——体重曲线的可读性靠相对变化；
 * 代价是 0.2kg 的波动看起来也很陡，所以上/下两个刻度标的是真实值，
 * 让数字而不是斜率来说话。
 */
const range = computed(() => {
  const ys = series.value.map((p) => p.weightKg)
  if (!ys.length) return null
  const min = Math.min(...ys)
  const max = Math.max(...ys)
  if (max === min) return { lo: min - 1, hi: max + 1 }
  const pad = (max - min) * 0.08
  return { lo: min - pad, hi: max + pad }
})

/** 三条刻度：上、中、下。中间那条只是让人估得出位置，不额外标注什么。 */
const gridLines = computed(() => {
  if (!range.value) return []
  const { lo, hi } = range.value
  return [hi, (hi + lo) / 2, lo].map((value) => ({
    value,
    label: formatKg(value),
    y: yOf(value),
  }))
})

/**
 * 横轴：**按真实时间**。
 *
 * 跨度不足一天（或者时间解析不出来）时退回等距——那多半是同一天记的几次，
 * 按时间画会全部挤在同一个 x 上，看起来像一个点。
 */
const timeSpan = computed(() => {
  if (series.value.length < 2) return 0
  const first = parseRecordedAt(earliest.value?.recordedAt)
  const last = parseRecordedAt(latest.value?.recordedAt)
  if (!first || !last) return 0
  return last.getTime() - first.getTime()
})

const xOf = (index) => {
  const plotWidth = W - PAD_LEFT - PAD_RIGHT
  if (series.value.length === 1) return PAD_LEFT + plotWidth / 2
  if (timeSpan.value <= 0) {
    return PAD_LEFT + (index * plotWidth) / (series.value.length - 1)
  }
  const first = parseRecordedAt(earliest.value.recordedAt).getTime()
  const at = parseRecordedAt(series.value[index]?.recordedAt)
  if (!at) return PAD_LEFT
  return PAD_LEFT + ((at.getTime() - first) / timeSpan.value) * plotWidth
}

function yOf(kg) {
  const { lo, hi } = range.value
  return PAD_TOP + (1 - (kg - lo) / (hi - lo)) * (H - PAD_TOP - PAD_BOTTOM)
}

/** 少于两个点时是空串：polyline 一个点也画不出来，交给下面那个圆点表示「就一次记录」。 */
const linePoints = computed(() =>
  series.value.length < 2
    ? ''
    : series.value.map((p, i) => `${xOf(i).toFixed(1)},${yOf(p.weightKg).toFixed(1)}`).join(' '),
)

/** 折线下方的填充。首尾各补一个落在基线（画布底）上的点，围成一个闭合区域。 */
const areaPath = computed(() => {
  if (series.value.length < 2) return ''
  const base = H - PAD_BOTTOM
  const last = series.value.length - 1
  const top = series.value
    .map((p, i) => `${xOf(i).toFixed(1)},${yOf(p.weightKg).toFixed(1)}`)
    .join(' L ')
  return `M ${xOf(0).toFixed(1)} ${base} L ${top} L ${xOf(last).toFixed(1)} ${base} Z`
})

/** 每个点带上悬浮提示：SVG 的 <title> 是浏览器原生的 tooltip，不用写一行 JS。 */
const dots = computed(() =>
  series.value.map((p, i) => ({
    x: xOf(i),
    y: yOf(p.weightKg),
    key: p.id ?? p.recordedAt ?? i,
    tip: `${formatDay(p.recordedAt)} ${formatKg(p.weightKg)}kg`,
  })),
)

/** 与最早一次相比的变化量。少于两个点没有可比的对象，返回 0 由 deltaText 挡掉。 */
const delta = computed(() => {
  if (series.value.length < 2) return 0
  return latest.value.weightKg - earliest.value.weightKg
})

/**
 * 「较最早减少 1.7kg」这一行。少于两个点时不显示。
 *
 * 0.05 以下算持平：数据库是 {@code DECIMAL(5,1)}，最小真实步长是 0.1，
 * 浮点误差不该被说成「涨了 0.0000001kg」。
 */
const deltaText = computed(() => {
  if (series.value.length < 2) return ''
  const diff = delta.value
  if (Math.abs(diff) < 0.05) return t('weight.chart.flat')
  const key = diff > 0 ? 'weight.chart.up' : 'weight.chart.down'
  return t(key, { value: formatKg(Math.abs(diff)) })
})
</script>

<template>
  <div class="wc" :class="{ dark: isChatDark }">
    <p class="wc-title">{{ $t('weight.chart.title') }}</p>

    <p v-if="!series.length" class="wc-empty">{{ $t('weight.chart.empty') }}</p>

    <template v-else>
      <div class="wc-head">
        <span class="wc-latest">
          {{ $t('weight.chart.latest', { value: formatKg(latest.weightKg) }) }}
        </span>
        <span v-if="deltaText" class="wc-delta">{{ deltaText }}</span>
      </div>

      <!-- 纯图形。数值信息在上面那行、纵轴刻度和页面上的记录列表里，读屏软件读它们就够，
           没必要让 SVG 里的路径变成一串没有意义的朗读内容 -->
      <svg class="wc-plot" :viewBox="`0 0 ${W} ${H}`" aria-hidden="true">
        <!-- 网格线和刻度值。画在折线之前，免得压住线 -->
        <g v-for="line in gridLines" :key="`g${line.label}`">
          <line class="wc-grid" :x1="PAD_LEFT" :x2="W - PAD_RIGHT" :y1="line.y" :y2="line.y" />
          <text class="wc-tick" :x="PAD_LEFT - 8" :y="line.y + 3.5">{{ line.label }}</text>
        </g>

        <path v-if="areaPath" class="wc-area" :d="areaPath" />
        <polyline v-if="linePoints" class="wc-line" :points="linePoints" />
        <circle v-for="d in dots" :key="d.key" class="wc-dot" :cx="d.x" :cy="d.y" r="3.4">
          <title>{{ d.tip }}</title>
        </circle>
      </svg>

      <!-- 只有一次记录时首末是同一个日期，写了等于写两遍 -->
      <div v-if="series.length > 1" class="wc-foot">
        <span>{{ formatDay(earliest.recordedAt) }}</span>
        <span v-if="timeSpan <= 0" class="wc-sameday">{{ $t('weight.chart.sameDay') }}</span>
        <span>{{ formatDay(latest.recordedAt) }}</span>
      </div>

      <!-- 说明横轴口径。只在真有图的时候出现——空态下它描述的是一张不存在的图 -->
      <p class="wc-note">{{ $t('weight.chart.note') }}</p>
    </template>
  </div>
</template>

<style scoped>
/*
 * 自带那两个颜色变量，而不是共用 SettingsDrawer 的 --d-*：那些定义在 .drawer-root 上，
 * 而这个组件还挂在体重变化页上（那里没有抽屉的变量）。用 isChatDark 判深浅，
 * 和 SettingsDrawer 是同一个写法。
 *
 * 折线用 --brand / --brand-soft：它们在 base.css 的 :root 里，**深色下不被覆盖**，
 * 两种模式都能用。只有说明文字需要一个浅/深对。
 */
.wc {
  --wc-text: var(--muted);
}

.wc.dark {
  --wc-text: #8b93a3;
}

.wc-title {
  margin: 0 0 10px;
  color: var(--wc-text);
  font-size: 13px;
  font-weight: 600;
}

.wc-empty,
.wc-note {
  margin: 0;
  color: var(--wc-text);
  font-size: 12.5px;
  line-height: 1.6;
}

.wc-note {
  margin-top: 10px;
}

.wc-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.wc-latest {
  color: var(--brand);
  font-size: 15px;
  font-weight: 600;
}

/* 刻意**不给涨跌上色**。理由有两条，第二条更重要：
   一是 base.css 里没有语义化的 success 色，唯一现成的绿是 --slim，而那个文件明说
   那两条链路色是「各条链路自己的身份色，不要拿它们做整站配色」；
   二是这个产品会把「催吐 / 暴食 / 厌食」这类词拦下来转成关怀话术，
   把「掉秤」染成绿色等于在视觉上强化「越轻越好」，和那条安全边界是拧着的。
   方向由文字说清楚就够了（「较最早减少 1.7kg」）。 */
.wc-delta {
  color: var(--wc-text);
  font-size: 12.5px;
}

.wc-plot {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}

/* 网格线要足够淡：它是参考，不是内容。深色下靠 opacity 压不出来，
   所以用 currentColor + 透明度，两种主题都是「比底色重一点点」 */
.wc-grid {
  stroke: currentColor;
  stroke-width: 1;
  opacity: 0.14;
}

.wc-tick {
  fill: var(--wc-text);
  font-size: 11px;
  text-anchor: end;
  font-variant-numeric: tabular-nums;
}

.wc-line {
  fill: none;
  stroke: var(--brand);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.wc-area {
  fill: var(--brand-soft);
  stroke: none;
}

.wc-dot {
  fill: var(--brand);
  /* 白心：点密的时候彼此不会糊成一条粗线 */
  stroke: #fff;
  stroke-width: 1.2;
}

/* 两个日期正好落在首末点的下方：首末点的 x 就是 PAD_LEFT 和 W - PAD_RIGHT，
   和这条 justify-content 的两端基本对齐 */
.wc-foot {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  margin-top: 4px;
  color: var(--wc-text);
  font-size: 11.5px;
}

.wc-sameday {
  font-size: 11px;
  opacity: 0.85;
}
</style>
