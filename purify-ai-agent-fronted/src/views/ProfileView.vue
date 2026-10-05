<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchProfile, updateProfile } from '../api/http.js'
import { message, rawMessage, resolveMessage } from '../i18n/index.js'
import { formatDay, formatKg, formatTime, latestPoint } from '../weightData.js'

/**
 * 我的情况（用户画像）页。
 *
 * **这一块原来只长在设置抽屉里**，是抽屉里最长的那一段。单开一页的理由和体重变化页
 * 同源，但更硬一些：
 *
 * 1. **画像不是「设置」，是一份会被反复查看的资料。** 用户想知道的是「它现在以为我
 *    是什么情况」——身高体重、目标、忌口。抽屉是「改设置」的地方：进去要两步，
 *    滚过整张表单，而且看到的第一眼永远是输入框，不是一个能读的答案。
 * 2. **模型一直在往里写。** 对话里说的每一句「我 175」「海鲜过敏」都会经
 *    `UserProfileTool` 落进这份数据，气泡下面那行「已记住」还带一个查看入口——
 *    点进来落到一个表单上，看到的是七个空框还是填好的值，全靠抽屉那次 GET 的运气。
 *    现在它落在一页专门讲这件事的页面上。
 * 3. **抽屉放不下真正有用的东西**：BMI 算出来是多少、哪些项还空着、
 *    体重那部分现在归到 /weight 一页去了（见 SettingsDrawer 里的入口）。
 *
 * <h2>这一页的形状</h2>
 *
 * 上半是**只读的概览**（它是这一页存在的理由），下半是可编辑的表单。表单不做
 * 「先点编辑才能改」那一层锁——那会让「顺手补一个忌口」变成三次点击；
 * 但**保存必须显式点**，不跟着输入框变：后端 `PUT /api/profile` 是**整体替换**，
 * 每敲一个字符就发一次的话，用户清空身高的一瞬间库里就已经被删掉了（见
 * `ProfileService#replace` 的说明）。
 *
 * 数据只有两个来源，不额外发请求：进页面时 `GET /api/profile` 一次；
 * 保存后用 `PUT` 的返回值回填——它返回的是改完之后完整的画像，和设置页原先的做法一致。
 */
const profile = ref(null)
const form = reactive({
  age: '',
  heightCm: '',
  weightKg: '',
  goal: '',
  activityLevel: '',
  dietPreference: '',
  avoidFood: '',
})

/** 可选项由后端给（跟着活动水平枚举走），前端不写死一份 */
const activityOptions = ref([])

const loading = ref(true)
const saving = ref(false)
const loadError = ref(null)
const saveError = ref(null)
const saved = ref(false)

const loadErrorText = computed(() => resolveMessage(loadError.value))
const saveErrorText = computed(() => resolveMessage(saveError.value))

function fillFrom(data) {
  profile.value = data
  form.age = data?.age ?? ''
  form.heightCm = data?.heightCm ?? ''
  form.weightKg = data?.weightKg ?? ''
  form.goal = data?.goal ?? ''
  form.activityLevel = data?.activityLevel ?? ''
  form.dietPreference = data?.dietPreference ?? ''
  form.avoidFood = data?.avoidFood ?? ''
  activityOptions.value = data?.activityLevelOptions ?? []
}

async function load() {
  loading.value = true
  try {
    fillFrom(await fetchProfile())
    loadError.value = null
  } catch (err) {
    loadError.value = err?.message ? rawMessage(err.message) : message('profile.loadFailed')
  } finally {
    loading.value = false
  }
}

/**
 * 空串转成 null。
 *
 * 数字输入框空着时 `v-model` 给的是空串，直接发给后端会被 Jackson 判成解析失败
 * （400，而且错误信息是用户看不懂的英文）。转成 null 才是「这一项没填」的意思。
 */
function toNumber(value) {
  if (value === '' || value === null || value === undefined) return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

/**
 * 保存。**七个字段一起发**，不是只发改动过的：后端的语义是整体替换，
 * 只发改动过的那些会把其余字段当成「清空」。这也正是用户能删掉某个字段的原因——
 * 表单上是什么，库里就是什么。
 */
async function save() {
  saving.value = true
  saveError.value = null
  saved.value = false
  try {
    // 用后端返回的那份回填，而不是拿本地表单：后端可能会把空白折成 null，
    // 也可能会因为体重变化追加一条流水。以后端为准，界面才不会和库里不一致
    fillFrom(
      await updateProfile({
        age: toNumber(form.age),
        heightCm: toNumber(form.heightCm),
        weightKg: toNumber(form.weightKg),
        goal: form.goal,
        activityLevel: form.activityLevel,
        dietPreference: form.dietPreference,
        avoidFood: form.avoidFood,
      }),
    )
    saved.value = true
  } catch (err) {
    saveError.value = err?.message ? rawMessage(err.message) : message('profile.saveFailed')
  } finally {
    saving.value = false
  }
}

const bmi = computed(() => profile.value?.bmi ?? null)

/**
 * BMI 落在哪一档。
 *
 * 分档用的是通行的成人区间（18.5 / 24 是中国人群体常用的那两条线），措辞**刻意中性**：
 * 说「偏轻 / 正常范围 / 偏重」而不是「太瘦 / 超标」，理由和体重页不给涨跌上色一样——
 * 这一页不该替用户下判断。BMI 本身是后端算的，会在明显不合理的数据上返回 null
 * （比如身高填成 17），那时整格不显示，而不是显示一个错数。
 */
const bmiBand = computed(() => {
  const value = bmi.value
  if (!Number.isFinite(value)) return ''
  if (value < 18.5) return 'profile.band.under'
  if (value < 24) return 'profile.band.normal'
  if (value < 28) return 'profile.band.over'
  return 'profile.band.obese'
})

/** 最新一条体重流水，给「体重」那格显示称重时间用；没有流水就是 null。 */
const latestWeight = computed(() => latestPoint(profile.value?.weightHistory ?? []))

/** 概览里的格子。做成 computed：它要跟着语言和上面那份画像一起变。 */
const overview = computed(() => {
  const data = profile.value
  const cells = [
    {
      key: 'profile.overview.age',
      value: Number.isFinite(data?.age) ? String(data.age) : '',
      note: 'profile.overview.ageEmpty',
    },
    {
      key: 'profile.overview.height',
      value: Number.isFinite(data?.heightCm) ? `${formatKg(data.heightCm)} cm` : '',
      note: 'profile.overview.heightEmpty',
    },
    {
      key: 'profile.overview.weight',
      value: Number.isFinite(data?.weightKg) ? `${formatKg(data.weightKg)} kg` : '',
      note: 'profile.overview.weightEmpty',
      // sub 是「这个数字是哪来的」：最后一次称重的时间。没有流水时留空，
      // 那时格子里已经有「还没填」那句话了，再补一句时间是多余的
      sub: latestWeight.value
        ? `${formatDay(latestWeight.value.recordedAt)} ${formatTime(latestWeight.value.recordedAt)}`
        : '',
    },
  ]

  if (bmi.value) {
    cells.push({
      key: 'profile.overview.bmi',
      value: bmi.value.toFixed(1),
      note: null,
      // 档位那句话是**文案键**，模板里再翻（和 cell.key 一样），这样切语言它跟着变
      subKey: bmiBand.value,
    })
  }

  const activity = activityOptions.value.find((option) => option.value === data?.activityLevel)
  cells.push({
    key: 'profile.overview.activity',
    // 选项 label 是后端一起发过来的、而且**没翻**（后端拿 label 当匹配依据，
    // 翻了画像就回写不进库），所以这里显示的是未翻译的原文——和表单里那个下拉框一致。
    // 认不出的值退回活动水平枚举名，宁可显示 SEDENTARY 也不要显示「已填写」
    value: activity?.label ?? data?.activityLevel ?? '',
    note: 'profile.overview.activityEmpty',
    // hint 那一串（「久坐办公，几乎不运动」）只在表单的下拉框里当选项说明用；
    // 概览里一格里塞两行解释字，会让这一排数字读起来很吵
    sub: '',
    subKey: '',
  })

  return cells
})

/** 概览里有没有任何一条真信息。全空时整块换成一句引导，而不是七个「还没填」。 */
const overviewEmpty = computed(() => overview.value.every((cell) => !cell.value))

/**
 * 这份画像什么时候写进去的。
 *
 * 只在**确实写过**的时候显示：`updatedAt` 为 null 表示库里没有这一行，
 * 那和「刚才更新过」是两件事。做成 computed 而不是 setup 里算一次，
 * 是为了让它跟着语言走（日期格式两种语言不一样）。
 */
const updatedText = computed(() => {
  const stamp = profile.value?.updatedAt
  if (typeof stamp !== 'string' || stamp.length < 16) return ''
  return `${formatDay(stamp)} ${formatTime(stamp)}`
})

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="bar">
      <RouterLink to="/" class="back" :title="$t('profile.backHome')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M15 18l-6-6 6-6" />
        </svg>
      </RouterLink>
      <h1>{{ $t('profile.title') }}</h1>
      <span v-if="updatedText" class="updated">{{ $t('profile.updated', { when: updatedText }) }}</span>
      <button class="ghost" type="button" :disabled="loading" @click="load">
        {{ $t('common.refresh') }}
      </button>
    </header>

    <main class="body">
      <p v-if="loadErrorText" class="err">{{ loadErrorText }}</p>
      <p v-else-if="loading" class="desc">{{ $t('common.loading') }}</p>

      <template v-else>
        <!-- ======================= 概览（只读） =======================
             这一块是这一页存在的理由：一眼看出「它现在以为我是什么情况」，
             而不是一进来就面对七个输入框 -->
        <section class="card">
          <h2>{{ $t('profile.overview.title') }}</h2>
          <p v-if="overviewEmpty" class="desc">{{ $t('profile.overview.empty') }}</p>

          <div v-else class="stats">
            <div v-for="cell in overview" :key="cell.key" class="stat">
              <span class="stat-label">{{ $t(cell.key) }}</span>
              <span v-if="cell.value" class="stat-value">{{ cell.value }}</span>
              <!-- 没填的项不显示成空白格：一句话说明「还没填」，否则空着看起来像加载失败 -->
              <span v-else class="stat-empty">{{ cell.note ? $t(cell.note) : '' }}</span>
              <!-- note 是「为什么这一格是空的」，sub 是「数字是哪来的 / 落在哪一档」。
                   两格分开放：sub 里的日期是**已经拼好的字符串**（不翻），
                   subKey 才是**文案键**（要翻）—— 混成一个字段的话，
                   日期会被当成键去查翻译表，表现是日期不见了加一条控制台警告 -->
              <span v-if="cell.sub" class="stat-sub">{{ cell.sub }}</span>
              <span v-else-if="cell.subKey" class="stat-sub">{{ $t(cell.subKey) }}</span>
            </div>
          </div>
        </section>

        <!-- ======================= 表单 ======================= -->
        <section class="card">
          <h2>{{ $t('profile.form.title') }}</h2>
          <p class="desc">{{ $t('profile.form.intro') }}</p>

          <div class="grid">
            <label class="field">
              <span class="label">{{ $t('profile.age') }}</span>
              <input v-model="form.age" type="number" min="1" max="150" :placeholder="$t('profile.agePlaceholder')" />
            </label>
            <label class="field">
              <span class="label">{{ $t('profile.height') }}</span>
              <input v-model="form.heightCm" type="number" min="50" max="250" step="0.5" :placeholder="$t('profile.heightPlaceholder')" />
            </label>
            <label class="field">
              <span class="label">{{ $t('profile.weight') }}</span>
              <input v-model="form.weightKg" type="number" min="1" max="500" step="0.1" :placeholder="$t('profile.weightPlaceholder')" />
            </label>
            <label class="field">
              <span class="label">{{ $t('profile.bmi') }}</span>
              <!-- 只读，由后端算。它会在明显不合理的数据上返回空，
                   那时显示「—」比显示一个错数好 -->
              <input :value="bmi ? bmi.toFixed(1) : '—'" type="text" readonly tabindex="-1" />
            </label>
          </div>

          <label class="field full">
            <span class="label">{{ $t('profile.goal') }}</span>
            <input v-model="form.goal" type="text" :placeholder="$t('profile.goalPlaceholder')" />
          </label>

          <label class="field full">
            <span class="label">{{ $t('profile.activity') }}</span>
            <!-- 选项由后端返回（跟着枚举走），不在这儿写死一份。
                 **label / hint 也不翻译**：后端拿 label 当匹配依据（ActivityLevel.parse），
                 翻了用户画像就回写不进库了。见后端的 messages.properties 说明 -->
            <select v-model="form.activityLevel">
              <option value="">{{ $t('profile.activityEmpty') }}</option>
              <option v-for="option in activityOptions" :key="option.value" :value="option.value">
                {{ option.label }} · {{ option.hint }}
              </option>
            </select>
          </label>

          <label class="field full">
            <span class="label">{{ $t('profile.diet') }}</span>
            <input v-model="form.dietPreference" type="text" :placeholder="$t('profile.dietPlaceholder')" />
          </label>

          <label class="field full">
            <span class="label">{{ $t('profile.avoid') }}</span>
            <input v-model="form.avoidFood" type="text" :placeholder="$t('profile.avoidPlaceholder')" />
          </label>

          <p class="note">{{ $t('profile.clearNote') }}</p>

          <div class="actions">
            <button class="primary" type="button" :disabled="saving" @click="save">
              {{ saving ? $t('common.saving') : $t('common.save') }}
            </button>
            <span v-if="saved" class="ok">{{ $t('common.saved') }}</span>
          </div>
          <p v-if="saveErrorText" class="err">{{ saveErrorText }}</p>
        </section>

        <!-- ======================= 体重 =======================
             图、时间窗口的变化和记录列表都在 /weight 那一页（抽屉里放不下，
             这里同样放不下也不必放）。这一页只负责「现在多少」和把路指过去 -->
        <section class="card weight-card">
          <div class="weight-line">
            <div>
              <h2>{{ $t('profile.weight.title') }}</h2>
              <p v-if="Number.isFinite(profile?.weightKg)" class="weight-value">
                {{ $t('profile.weight.summary', { value: formatKg(profile.weightKg) }) }}
              </p>
              <p v-else class="desc">{{ $t('profile.weight.empty') }}</p>
            </div>
            <RouterLink class="link" to="/weight">{{ $t('profile.weight.link') }} →</RouterLink>
          </div>
        </section>

        <p class="footnote">{{ $t('profile.footnote') }}</p>
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

/* 「上次更新 …」：小一号、推到右边，和刷新按钮并排 */
.updated {
  margin-left: auto;
  color: #8b94a3;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
.updated + .ghost {
  /* 两个都写了 margin-left:auto，后一个会把前一个推开的空档再吃掉；
     这里只让「更新于」负责推开，按钮紧跟其后 */
  margin-left: 0;
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
  max-width: 860px;
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

.desc {
  color: #6b7381;
  font-size: 13px;
  line-height: 1.7;
}

/* ---------------------------------------------------------------- 概览 */

.stats {
  display: grid;
  /* auto-fit：格子数量跟着数据走（BMI 有时没有），写死列数会在缺一格时留个洞 */
  grid-template-columns: repeat(auto-fit, minmax(124px, 1fr));
  gap: 16px 18px;
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

.stat-empty {
  color: #a7aebc;
  font-size: 13px;
}

.stat-sub {
  color: #8b94a3;
  font-size: 11.5px;
  line-height: 1.5;
}

/* ---------------------------------------------------------------- 表单 */

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.field {
  display: block;
  margin-top: 12px;
}
.grid .field {
  margin-top: 0;
}

.label {
  display: block;
  margin-bottom: 5px;
  color: #6b7488;
  font-size: 12.5px;
}

.field input,
.field select {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--line-strong);
  border-radius: 9px;
  background: #fff;
  color: var(--ink);
  font: inherit;
  font-size: 13.5px;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.field input:focus,
.field select:focus {
  outline: none;
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.field input[readonly] {
  color: #6b7488;
  background: #f6f7f9;
  cursor: default;
}

/* 关掉数字输入框的上下箭头：它们很窄，容易误点，而这几项都是偶尔填一次 */
.field input[type='number']::-webkit-outer-spin-button,
.field input[type='number']::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
.field input[type='number'] {
  -moz-appearance: textfield;
  appearance: textfield;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
}

.primary {
  padding: 9px 22px;
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
  opacity: 0.55;
  cursor: not-allowed;
}

.note {
  margin-top: 14px;
  color: #8b93a3;
  font-size: 12px;
  line-height: 1.6;
}

.ok {
  color: var(--brand);
  font-size: 13px;
}

.err {
  margin-top: 12px;
  color: #b0322f;
  font-size: 13px;
  line-height: 1.7;
}

/* ---------------------------------------------------------------- 体重 */

.weight-card {
  padding: 16px 20px;
}
.weight-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}
.weight-line h2 {
  margin-bottom: 2px;
}
.weight-value {
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.link {
  flex: none;
  padding: 6px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: #46505f;
  font-size: 12.5px;
  text-decoration: none;
  white-space: nowrap;
  transition: border-color 0.18s ease, color 0.18s ease;
}
.link:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.footnote {
  color: #8b93a3;
  font-size: 12px;
  line-height: 1.7;
  text-align: center;
}

@media (max-width: 640px) {
  .body {
    padding: 16px 12px 40px;
  }
  .grid {
    grid-template-columns: minmax(0, 1fr);
  }
  /* 窄屏放不下「更新于」，它只是锦上添花的信息 */
  .updated {
    display: none;
  }
  .weight-line {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
