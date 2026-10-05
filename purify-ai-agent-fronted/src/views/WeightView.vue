<script setup>
import { computed, onMounted, ref } from 'vue'
import WeightChart from '../components/WeightChart.vue'
import { deleteWeightRecord, fetchProfile, recordWeight } from '../api/http.js'
import { isEnglish, message, rawMessage, resolveMessage, t } from '../i18n/index.js'
import {
  changeOverDays,
  formatDay,
  formatKg,
  formatTime,
  latestPoint,
  stepDiffs,
} from '../weightData.js'

/**
 * 体重变化页。
 *
 * **这个功能原来只长在设置抽屉里**，是画像表单下面的一个小尾巴。搬出来单独成页的理由：
 * 它是一个会被反复打开的东西（称一次就想看一眼），而设置抽屉是「改资料」的地方，
 * 进去要两步、还要滚过一整张表单；更重要的是抽屉里放不下它真正需要的东西——
 * 一块能读数的图、几个时间窗口的变化、和一份可以核对的记录列表。
 *
 * <h2>这一页修掉的几处「功能做了但没用」</h2>
 *
 * 1. **图只有最近 10 个点**。那个 10 是喂模型的 token 预算（见
 *    `ProfileService#recentHistory`），而页面的数据源现在走的是完整流水
 *    （`ProfileService#history`）。一周称两次的话，10 个点只有 5 周，看不出月度趋势。
 * 2. **没法单独记一次体重**。原来只能去聊天里说一句，或者改画像表单（要动七个字段）。
 *    现在这里有一行输入框，走的是只动体重的 `POST /api/profile/weight`。
 * 3. **中间的记录读不出数值**。现在下面有完整列表：日期、体重、和上一次的差，
 *    还能删掉记错的那一条。
 *
 * 数据只有一个来源：`GET /api/profile` 返回的 `weightHistory`（新的在前）。
 * 落地时读一次，之后每次「记一笔」「删一条」都用写接口的返回值刷新——
 * 它们返回的是整份画像，所以不用再补一次 GET（和设置页保存后的做法一致）。
 */
const points = ref([])
const profile = ref(null)
const loading = ref(true)
const loadError = ref(null)
/** 输入框里的字符串。用字符串而不是 number：空着的时候 v-model 给的是空串，转数字会变 0。 */
const input = ref('')
const saving = ref(false)
const recordError = ref(null)
const recordDone = ref(null)
const listError = ref(null)
/** 正在删的那一条 id。删除期间把那一行的按钮禁掉，避免连点 */
const deletingId = ref(null)

const loadErrorText = computed(() => resolveMessage(loadError.value))
const recordErrorText = computed(() => resolveMessage(recordError.value))
const recordDoneText = computed(() => resolveMessage(recordDone.value))
const listErrorText = computed(() => resolveMessage(listError.value))

const latest = computed(() => latestPoint(points.value))
const week = computed(() => changeOverDays(points.value, 7))
const month = computed(() => changeOverDays(points.value, 30))
const diffs = computed(() => stepDiffs(points.value))

/** 累计变化 = 最新一条减去最早一条。少于两条时是 null（没法比）。 */
const total = computed(() => {
  const list = points.value.filter((p) => Number.isFinite(p?.weightKg))
  if (list.length < 2) return null
  const newest = list[0].weightKg
  const oldest = list[list.length - 1].weightKg
  const diff = Math.round((newest - oldest) * 10) / 10
  return { diff, from: list[list.length - 1] }
})

/**
 * 一个差值怎么显示。
 *
 * `null` 有两种含义，这里分开说：**没有可比的数据**（记录不够 N 天）和
 * **一条记录都没有**。混成一句「—」的话，新用户会以为功能坏了。
 *
 * @returns {{dir: 'up'|'down'|'flat'|'none', text: string, note: object|null}}
 *   {@code note} 是「为什么没有数字」的那句解释，有数字时为 null
 */
function describeChange(change, days) {
  if (!change) {
    return { dir: 'none', text: '', note: message('weight.stats.notEnough', { days }) }
  }
  if (Math.abs(change.diff) < 0.05) {
    return { dir: 'flat', text: '', note: message('weight.stats.flat') }
  }
  return {
    dir: change.diff > 0 ? 'up' : 'down',
    text: `${change.diff > 0 ? '+' : '−'}${formatKg(Math.abs(change.diff))} kg`,
    note: null,
  }
}

/**
 * 上面那排格子。
 *
 * 做成 computed 而不是在模板里调函数：模板里的函数调用每次重渲染都会重跑，
 * 而这里要遍历整份流水（最多 500 条）。这些数字只跟 `points` 有关。
 */
const stats = computed(() => {
  const cells = []

  cells.push({
    key: 'weight.stats.current',
    value: latest.value ? `${formatKg(latest.value.weightKg)} kg` : '—',
    note: null,
    sub: latest.value
      ? `${formatDay(latest.value.recordedAt)} ${formatTime(latest.value.recordedAt)}`
      : '',
  })

  for (const [key, change, days] of [
    ['weight.stats.week', week.value, 7],
    ['weight.stats.month', month.value, 30],
  ]) {
    const described = describeChange(change, days)
    cells.push({
      key,
      value: described.text || '—',
      note: described.note,
      sub: '',
    })
  }

  cells.push({
    key: 'weight.stats.total',
    value: total.value
      ? `${total.value.diff > 0 ? '+' : '−'}${formatKg(Math.abs(total.value.diff))} kg`
      : '—',
    // 只有一条记录时比不了，说清楚；有两条以上就把基准日期标出来，数字才有出处
    note: total.value ? null : message('weight.stats.needTwo'),
    sub: total.value ? t('weight.stats.since', { date: formatDay(total.value.from.recordedAt) }) : '',
  })

  cells.push({
    key: 'weight.stats.count',
    value: t('weight.stats.times', { n: points.value.length }),
    note: null,
    sub: '',
  })

  return cells
})

/**
 * 表单标题里那个日期。
 *
 * 做成 computed 而不是 setup 里算一次的常量：它跟着语言走，
 * 而在 setup 里求值的话，用户切了语言这里还是旧语言（日期格式也不同）。
 */
const todayText = computed(() => {
  const today = new Date()
  return isEnglish()
    ? today.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    : `${today.getMonth() + 1} 月 ${today.getDate()} 日`
})

function applyProfile(data) {
  profile.value = data
  points.value = data?.weightHistory ?? []
}

async function load() {
  loading.value = true
  try {
    applyProfile(await fetchProfile())
    loadError.value = null
  } catch (err) {
    loadError.value = err?.message ? rawMessage(err.message) : message('weight.loadFailed')
  } finally {
    loading.value = false
  }
}

/** 记一笔。 */
async function submit() {
  const value = Number(input.value)
  if (input.value === '' || !Number.isFinite(value)) {
    recordError.value = message('weight.record.needValue')
    return
  }

  saving.value = true
  recordError.value = null
  recordDone.value = null
  try {
    const updated = await recordWeight(value)
    applyProfile(updated)
    recordDone.value = message('weight.record.done', { value: formatKg(value) })
    input.value = ''
  } catch (err) {
    // 后端会把范围校验的结果翻成人话（「体重 720kg 看起来不对：请填 20 到 400 之间的数字」），
    // 原样显示比前端再判一次范围更准——规则只有那一份
    recordError.value = err?.message ? rawMessage(err.message) : message('weight.record.failed')
  } finally {
    saving.value = false
  }
}

/** 删掉记错的那一条。删完用返回的整份画像刷新，列表和统计一起变。 */
async function remove(point) {
  const when = `${formatDay(point.recordedAt)} ${formatTime(point.recordedAt)}`
  if (!window.confirm(t('weight.list.deleteConfirm', { when, value: formatKg(point.weightKg) }))) {
    return
  }
  deletingId.value = point.id
  listError.value = null
  try {
    applyProfile(await deleteWeightRecord(point.id))
  } catch (err) {
    listError.value = err?.message ? rawMessage(err.message) : message('weight.list.deleteFailed')
  } finally {
    deletingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="bar">
      <RouterLink to="/" class="back" :title="$t('weight.backHome')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M15 18l-6-6 6-6" />
        </svg>
      </RouterLink>
      <h1>{{ $t('weight.title') }}</h1>
      <button class="ghost" type="button" :disabled="loading" @click="load">
        {{ $t('common.refresh') }}
      </button>
    </header>

    <main class="body">
      <p v-if="loadErrorText" class="err">{{ loadErrorText }}</p>
      <p v-else-if="loading" class="desc">{{ $t('common.loading') }}</p>

      <template v-else>
        <!-- ======================= 记一笔 ======================= -->
        <section class="card record">
          <div class="record-main">
            <label class="record-label" for="weight-input">
              {{ $t('weight.record.label', { date: todayText }) }}
            </label>
            <div class="record-row">
              <input
                id="weight-input"
                v-model="input"
                type="number"
                inputmode="decimal"
                step="0.1"
                min="20"
                max="400"
                :placeholder="$t('weight.record.placeholder')"
                @keyup.enter="submit"
              />
              <span class="unit">kg</span>
              <button class="primary" type="button" :disabled="saving" @click="submit">
                {{ saving ? $t('weight.record.saving') : $t('weight.record.submit') }}
              </button>
            </div>
            <p class="hint">{{ $t('weight.record.hint') }}</p>
          </div>

          <p v-if="recordDoneText" class="ok">{{ recordDoneText }}</p>
          <p v-if="recordErrorText" class="err">{{ recordErrorText }}</p>
        </section>

        <!-- ======================= 概览 ======================= -->
        <section class="card">
          <div class="stats">
            <div v-for="cell in stats" :key="cell.key" class="stat">
              <span class="stat-label">{{ $t(cell.key) }}</span>
              <span class="stat-value">{{ cell.value }}</span>
              <!-- note 是「为什么这一格没有数字」，sub 是数字的出处（基准日期）。
                   两者不会同时出现：有数字才有出处，没数字才需要解释 -->
              <span v-if="cell.note" class="stat-sub">{{ resolveMessage(cell.note) }}</span>
              <span v-else-if="cell.sub" class="stat-sub">{{ cell.sub }}</span>
            </div>
            <!-- BMI 只在身高体重都有时才算得出来（后端算的），没有就整格不显示，
                 而不是显示一个「—」——那一格的存在本身会让人以为要填什么 -->
            <div v-if="profile?.bmi" class="stat">
              <span class="stat-label">{{ $t('weight.stats.bmi') }}</span>
              <span class="stat-value">{{ profile.bmi.toFixed(1) }}</span>
            </div>
          </div>
        </section>

        <!-- ======================= 曲线 ======================= -->
        <section class="card">
          <WeightChart :points="points" />
        </section>

        <!-- ======================= 记录列表 ======================= -->
        <section class="card">
          <div class="card-head">
            <h2>{{ $t('weight.list.title', { n: points.length }) }}</h2>
          </div>

          <p v-if="listErrorText" class="err">{{ listErrorText }}</p>
          <p v-if="!points.length" class="desc">{{ $t('weight.list.empty') }}</p>

          <table v-else class="records">
            <thead>
              <tr>
                <th>{{ $t('weight.list.when') }}</th>
                <th class="num-col">{{ $t('weight.list.value') }}</th>
                <th class="num-col">{{ $t('weight.list.change') }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(point, index) in points" :key="point.id ?? point.recordedAt">
                <td class="when">
                  {{ formatDay(point.recordedAt) }}
                  <span class="time">{{ formatTime(point.recordedAt) }}</span>
                </td>
                <td class="num-col strong">{{ formatKg(point.weightKg) }}</td>
                <td class="num-col">
                  <!-- 第一条（最早那条）没有「上一次」可比 -->
                  <template v-if="diffs[index] === null">
                    <span class="muted">{{ $t('weight.list.first') }}</span>
                  </template>
                  <template v-else-if="Math.abs(diffs[index]) < 0.05">
                    <span class="muted">{{ $t('weight.list.same') }}</span>
                  </template>
                  <template v-else>
                    {{ diffs[index] > 0 ? '+' : '−' }}{{ formatKg(Math.abs(diffs[index])) }}
                  </template>
                </td>
                <td class="del-col">
                  <button
                    type="button"
                    class="del"
                    :disabled="deletingId === point.id"
                    :title="$t('weight.list.delete')"
                    @click="remove(point)"
                  >
                    {{ $t('weight.list.delete') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100dvh;
  background: var(--page);
}

.bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 24px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 5;
}
.bar h1 {
  font-size: 15px;
  font-weight: 600;
}
.back {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-left: -6px;
  border-radius: 9px;
  color: #5b6472;
}
.back:hover {
  background: #eef0f4;
  color: var(--ink);
}
.back svg {
  width: 18px;
  height: 18px;
}

.ghost {
  margin-left: auto;
  padding: 5px 11px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  font-size: 12.5px;
  color: #46505f;
}
.ghost:hover:not(:disabled) {
  border-color: var(--brand);
  color: var(--brand);
}
.ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.body {
  max-width: 820px;
  margin: 0 auto;
  padding: 22px 24px 48px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.card {
  padding: 18px 20px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
}
.card h2 {
  font-size: 14.5px;
  font-weight: 600;
  margin-bottom: 10px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.card-head h2 {
  margin-bottom: 0;
}

.desc {
  color: #6b7381;
  font-size: 13px;
  line-height: 1.7;
}

/* ---------------------------------------------------------- 记一笔 */

.record-label {
  display: block;
  margin-bottom: 9px;
  font-size: 14.5px;
  font-weight: 600;
}

.record-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.record-row input {
  width: 120px;
  padding: 9px 12px;
  border: 1px solid var(--line-strong);
  border-radius: 9px;
  font: inherit;
  font-size: 15px;
  outline: none;
}
.record-row input:focus {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.unit {
  color: #6b7381;
  font-size: 13px;
}

.primary {
  padding: 9px 20px;
  border: 0;
  border-radius: 9px;
  background: var(--brand);
  color: #fff;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.primary:hover:not(:disabled) {
  background: var(--brand-deep);
}
.primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.hint {
  margin: 10px 0 0;
  color: #8b93a3;
  font-size: 12.5px;
  line-height: 1.6;
}

/* ------------------------------------------------------------ 概览 */

.stats {
  display: grid;
  /* auto-fit：格子数量跟着数据走（BMI 有时没有），写死列数会在缺一格时留个洞 */
  grid-template-columns: repeat(auto-fit, minmax(118px, 1fr));
  gap: 14px 18px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.stat-label {
  color: #8b94a3;
  font-size: 12px;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.stat-sub {
  color: #8b94a3;
  font-size: 11.5px;
  line-height: 1.5;
}

/* -------------------------------------------------------- 记录列表 */

.records {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.records th,
.records td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--line);
  text-align: left;
}
.records th {
  color: #8b94a3;
  font-weight: 500;
  font-size: 12px;
}
.records .num-col {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.records .strong {
  font-weight: 600;
}
.records .when {
  white-space: nowrap;
}
.records .time {
  margin-left: 6px;
  color: #8b94a3;
  font-size: 12px;
}
.records .muted {
  color: #8b94a3;
}
.records .del-col {
  width: 1%;
  text-align: right;
}

.del {
  padding: 3px 9px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: #fff;
  color: #8b94a3;
  font-size: 12px;
  cursor: pointer;
}
.del:hover:not(:disabled) {
  border-color: #b0322f;
  color: #b0322f;
}
.del:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ok {
  margin-top: 12px;
  color: #0a7d55;
  font-size: 13px;
}
.err {
  margin-top: 12px;
  color: #b0322f;
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 640px) {
  .body {
    padding: 16px 12px 40px;
  }
  .records .time {
    display: none;
  }
}
</style>
