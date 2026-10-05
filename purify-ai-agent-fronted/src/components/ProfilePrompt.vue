<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { fetchProfile, updateProfile } from '../api/http.js'
import { message, rawMessage, resolveMessage } from '../i18n/index.js'

/**
 * 进对话页时补一次画像的弹窗。
 *
 * <h2>它解决什么</h2>
 *
 * 新用户进来是空着手聊的：模型不知道他多高、多重、想减还是想增，于是只能给一堆
 * 放之四海皆准的话。而这些恰恰是**用户自己一句话就能说清、模型却猜不出来**的东西。
 * 所以第一次进对话页时先把这几项摆出来，让用户顺手补上。
 *
 * <h2>三条规矩</h2>
 *
 * 1. **弹不弹由「画像里有没有任何一项」决定，不由本地标记决定。**
 *    判据是 `GET /api/profile` 返回的那七个字段有没有一个非空——
 *    而不是 localStorage 里记一个「已经问过了」。理由是画像有三条写入路径
 *    （这个弹窗、`/profile` 那一页、以及对话里模型自己调的 `UserProfileTool`），
 *    本地标记只看得见第一条：用户跟模型说一句「我 175」，本地那个标记还是「没填过」，
 *    下次进对话又弹一遍，而弹出来的是他自己刚说过的东西。以库里那份数据为准，
 *    三条路径自然都算数。
 * 2. **跳过不写任何东西。** 「不填」是一个合法的答案（见 i18n 里 prompt 那几句），
 *    所以跳过就是关掉对话框，库里的画像原样不动——下次进来还会弹，这正是需求要的
 *    「没填就一直弹」。写一个「已跳过」标记反而会让它永远不再出现。
 * 3. **读失败不弹。** 画像读不到（后端没起、网络断了）时安静退场：
 *    这个弹窗是锦上添花，不是进对话的前置条件，为它弹一个「加载失败」更烦人。
 *
 * <h2>为什么检查也在这里，而不是在 ChatRoom</h2>
 *
 * 「什么算填过」这件事同时决定了「弹不弹」和「表单初始值从哪来」，两处必须一致；
 * 拆到父组件里的话，父组件要认识七个字段名，而它本来不需要知道画像长什么样。
 * 所以组件自己查、自己决定渲染不渲染（画像不为空时它渲染出一个空节点）。
 * 代价是每条链路进页面都会多发一次 `GET /api/profile`——尺寸很小，换来的是
 * ChatRoom 里只多一行标签。
 *
 * <h2>和 /profile 那一页的分工</h2>
 *
 * 这里是**一次打扰**，所以只放那七个输入框，不放概览、不放 BMI、不放体重流水，
 * 也不做「先点编辑才能改」那一层。想看得更全的人走 `/profile`（设置抽屉和
 * 用户菜单里都有入口）。
 */
const PROFILE_FIELDS = ['age', 'heightCm', 'weightKg', 'goal', 'activityLevel', 'dietPreference', 'avoidFood']

/**
 * 画像里有没有任何一项真信息。
 *
 * 字符串要单独判空串：后端的 `goal` 这些列可能存着空白（模型往里写过一句空的），
 * 而 `"  "` 是**存在**的，直接按「非 null」算的话，用户会觉得「我什么都没填，
 * 它怎么不弹了」。
 */
function hasAnyField(data) {
  if (!data) return false
  return PROFILE_FIELDS.some((field) => {
    const value = data[field]
    if (value === null || value === undefined) return false
    return typeof value === 'string' ? value.trim() !== '' : true
  })
}

const visible = ref(false)
const saving = ref(false)
/** 存描述符不存句子，见 i18n/index.js 的 message()：存句子的话切语言不会跟着变 */
const saveError = ref(null)
const saveErrorText = computed(() => resolveMessage(saveError.value))

/** 可选项由后端给（跟着活动水平枚举走），前端不写死一份 */
const activityOptions = ref([])

/** 第一个输入框。弹出来就把光标放进去——用户要么直接开打，要么一眼看清是哪些项 */
const firstInput = ref(null)

const form = reactive({
  age: '',
  heightCm: '',
  weightKg: '',
  goal: '',
  activityLevel: '',
  dietPreference: '',
  avoidFood: '',
})

function fillFrom(data) {
  form.age = data?.age ?? ''
  form.heightCm = data?.heightCm ?? ''
  form.weightKg = data?.weightKg ?? ''
  form.goal = data?.goal ?? ''
  form.activityLevel = data?.activityLevel ?? ''
  form.dietPreference = data?.dietPreference ?? ''
  form.avoidFood = data?.avoidFood ?? ''
  activityOptions.value = data?.activityLevelOptions ?? []
}

onMounted(async () => {
  try {
    const data = await fetchProfile()
    // 填过任意一项就不再打扰。这里回的是「什么都不渲染」，不是「渲染一个隐藏的框」——
    // 弹窗里那几个输入框在画像已经填好的情况下没有任何意义
    if (hasAnyField(data)) return

    fillFrom(data)
    visible.value = true
    await nextTick()
    firstInput.value?.focus()
  } catch {
    // 读不到就当没有这回事（理由见文件头第 3 条）
  }
})

/**
 * 关掉。跳过和右上角那颗 × 走的是同一条路：**什么都不写**。
 *
 * 不在这里提示「已跳过」之类的话：用户刚说了「先不填」，再回一句「好的，已跳过」
 * 纯属噪音，而对话框消失本身已经说明了结果。
 */
function dismiss() {
  visible.value = false
}

/**
 * 空串转成 null。
 *
 * 数字输入框空着时 `v-model` 给的是空串，直接发给后端会被 Jackson 判成解析失败
 * （400，而且是用户看不懂的英文）。转成 null 才是「这一项没填」的意思。
 */
function toNumber(value) {
  if (value === '' || value === null || value === undefined) return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

/**
 * 保存。**七个字段一起发**，语义和 `/profile` 那一页完全一样
 * （后端 `PUT /api/profile` 是整体替换，只发改动过的会把其余字段当成清空）。
 *
 * 一项都没填就点保存不会出问题：后端对「七个字段全是 null」按无变化处理
 * （见 `ProfileController#update`），不会把用户可能已有的资料抹掉。
 * 那种情况下这里和跳过是同一个结果——关掉，下次还弹。
 */
async function save() {
  saving.value = true
  saveError.value = null
  try {
    await updateProfile({
      age: toNumber(form.age),
      heightCm: toNumber(form.heightCm),
      weightKg: toNumber(form.weightKg),
      goal: form.goal,
      activityLevel: form.activityLevel,
      dietPreference: form.dietPreference,
      avoidFood: form.avoidFood,
    })
    // 保存成功就关掉：这一刻起画像不再为空，之后不会再弹。
    // 不在这里回填表单：对话框马上就没了，回填没有任何人能看见
    visible.value = false
  } catch (err) {
    // 失败时**不关**：让用户能改一下再试，也免得他以为存进去了
    saveError.value = err?.message ? rawMessage(err.message) : message('profile.saveFailed')
  } finally {
    saving.value = false
  }
}

/** Esc 关掉。和设置抽屉同一个手感，只是它不写任何东西 */
function onKeydown(event) {
  if (event.key === 'Escape' && visible.value) dismiss()
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <!-- 画像不为空时整块不存在。scrim 和卡片都是 fixed 的，所以它在 .shell 那个
       flex 容器里不占位置，也不影响消息列表的排版 -->
  <div v-if="visible" class="prompt-root">
    <!-- 点遮罩等于跳过。和设置抽屉同一个套路：浮层的关闭方式越一致越好 -->
    <div class="scrim" @click="dismiss"></div>

    <div class="card" role="dialog" aria-modal="true" :aria-label="$t('profile.prompt.title')">
      <header class="head">
        <h2>{{ $t('profile.prompt.title') }}</h2>
        <button class="icon-btn" type="button" :title="$t('common.close')" @click="dismiss">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </header>

      <div class="body">
        <p class="desc">{{ $t('profile.prompt.intro') }}</p>

        <div class="grid">
          <label class="field">
            <span class="label">{{ $t('profile.age') }}</span>
            <input
              ref="firstInput"
              v-model="form.age"
              type="number"
              min="1"
              max="150"
              :placeholder="$t('profile.agePlaceholder')"
            />
          </label>
          <label class="field">
            <span class="label">{{ $t('profile.height') }}</span>
            <input
              v-model="form.heightCm"
              type="number"
              min="50"
              max="250"
              step="0.5"
              :placeholder="$t('profile.heightPlaceholder')"
            />
          </label>
          <label class="field">
            <span class="label">{{ $t('profile.weight') }}</span>
            <input
              v-model="form.weightKg"
              type="number"
              min="1"
              max="500"
              step="0.1"
              :placeholder="$t('profile.weightPlaceholder')"
            />
          </label>
        </div>

        <label class="field full">
          <span class="label">{{ $t('profile.goal') }}</span>
          <input v-model="form.goal" type="text" :placeholder="$t('profile.goalPlaceholder')" />
        </label>

        <label class="field full">
          <span class="label">{{ $t('profile.activity') }}</span>
          <!-- 选项由后端返回（跟着枚举走），**label / hint 也不翻译**：
               后端拿 label 当匹配依据（ActivityLevel.parse），翻了画像就回写不进库。
               理由和 ProfileView 上那份完全一样 -->
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

        <p v-if="saveErrorText" class="err">{{ saveErrorText }}</p>
      </div>

      <footer class="foot">
        <!-- 「填一项就不会再弹」这句话放在按钮旁边，而不是塞进上面那段说明里：
             它是用户决定「填还是跳过」时唯一需要知道的后果 -->
        <p class="note">{{ $t('profile.prompt.note') }}</p>
        <div class="actions">
          <button class="ghost" type="button" @click="dismiss">
            {{ $t('profile.prompt.skip') }}
          </button>
          <button class="primary" type="button" :disabled="saving" @click="save">
            {{ saving ? $t('common.saving') : $t('common.save') }}
          </button>
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
/*
 * 颜色全部走 ChatRoom 挂在 `.shell` 上的那批 `--c-*` 语义 token：
 * 这个组件在对话页里渲染，而对话页有深色主题，写死颜色会让深色下的
 * 「深底深字」重演一次（那批 token 的由来见 ChatRoom 样式块开头）。
 *
 * z-index 50：**比两个抽屉（60/61）低**。弹窗是自己冒出来的，抽屉是用户点的，
 * 用户主动打开的东西不该被一个自动弹窗挡住。
 */
.prompt-root {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  /* 窄屏或矮屏时卡片不能贴边，也不能顶到屏幕外 */
  padding: 20px;
}

.scrim {
  position: absolute;
  inset: 0;
  background: var(--c-scrim);
}

.card {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 560px;
  max-height: min(86vh, 720px);
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--c-surface);
  color: var(--c-text-1);
  box-shadow: 0 24px 60px -24px var(--c-shadow-drawer);
  overflow: hidden;
}

.head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: none;
  padding: 14px 14px 14px 20px;
  border-bottom: 1px solid var(--line);
}
.head h2 {
  font-size: 15px;
  font-weight: 600;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-left: auto;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--c-text-4);
  cursor: pointer;
}
.icon-btn:hover {
  background: var(--c-subtle);
  color: var(--c-text-1);
}
.icon-btn svg {
  width: 16px;
  height: 16px;
}

/* 正文自己滚。卡片是 flex 列，头尾固定、中间伸缩，矮屏上按钮才不会被挤出去 */
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 20px 4px;
}

.desc {
  color: var(--c-text-4);
  font-size: 13px;
  line-height: 1.7;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.field {
  display: block;
}
.field.full {
  margin-top: 12px;
}

.label {
  display: block;
  margin-bottom: 5px;
  color: var(--c-text-4);
  font-size: 12.5px;
}

.field input,
.field select {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--c-border-input);
  border-radius: 9px;
  background: var(--c-surface);
  color: inherit;
  font: inherit;
  font-size: 13.5px;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.field input:focus,
.field select:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

/* 关掉数字输入框的上下箭头：它们很窄、容易误点，而这几项都是填一次就完了 */
.field input[type='number']::-webkit-outer-spin-button,
.field input[type='number']::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
.field input[type='number'] {
  -moz-appearance: textfield;
  appearance: textfield;
}

.err {
  margin: 12px 0 8px;
  color: var(--c-danger);
  font-size: 13px;
  line-height: 1.7;
}

.foot {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: none;
  padding: 12px 20px 16px;
  border-top: 1px solid var(--line);
}

.note {
  flex: 1;
  color: var(--c-text-5);
  font-size: 11.5px;
  line-height: 1.55;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.ghost {
  padding: 8px 16px;
  border: 1px solid var(--c-border-input);
  border-radius: 9px;
  background: transparent;
  color: var(--c-text-2);
  font: inherit;
  font-size: 13.5px;
  cursor: pointer;
}
.ghost:hover {
  border-color: var(--c-text-4);
  color: var(--c-text-1);
}

.primary {
  padding: 8px 22px;
  border: 0;
  border-radius: 9px;
  background: var(--accent);
  /* 强调色上的字不能跟着深色主题翻（见 ChatRoom 里 --c-on-accent 那段） */
  color: var(--c-on-accent);
  font: inherit;
  font-size: 13.5px;
  cursor: pointer;
}
.primary:hover:not(:disabled) {
  filter: brightness(0.94);
}
.primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .prompt-root {
    padding: 12px;
  }
  .grid {
    grid-template-columns: minmax(0, 1fr);
  }
  /* 窄屏上那句说明和两个按钮挤不进一行：说明在上、按钮铺满整行 */
  .foot {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  .actions {
    justify-content: flex-end;
  }
}
</style>
