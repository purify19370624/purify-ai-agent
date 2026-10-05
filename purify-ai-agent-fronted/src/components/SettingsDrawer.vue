<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as auth from '../auth.js'
import { uploadAvatar } from '../api/auth.js'
import { fetchProfile } from '../api/http.js'
import { isChatDark, setTheme, theme } from '../theme.js'
import { closeSettings } from '../panels.js'
import { LOCALE_OPTIONS, localeRef, setLocale } from '../i18n/index.js'
import { message, rawMessage, resolveMessage, t } from '../i18n/index.js'

/**
 * 设置抽屉：头像、外观、语言，以及「我的情况」和「体重变化」两个入口。
 *
 * **画像表单已经不在这里了**（搬去 `/profile` 单独一页）。理由和体重变化当初
 * 搬出去一样：画像是一份会被反复查看的资料，不是一组设置。而且模型在对话里
 * 一直在往里写，气泡下面那行「已记住」的查看入口原先点开的是抽屉里的表单——
 * 用户要的是「它记住了什么」，落到的却是一个要滚过整张表单才看得全的输入框。
 * 抽屉现在只留一行摘要加一个入口，和体重那块一个形状。
 *
 * 和 `UserMenu` 一样，这个组件在对话页（可能深色）和首页（永远浅色）都会出现，
 * 所以配色一律跟着 `isChatDark` 走，不读 `theme`——用户在首页把偏好设成深色时，
 * 首页上的抽屉仍然是浅色的，因为首页本身没有深色样式。
 * 颜色不共用 ChatRoom 的 `--c-*` token：那些定义在 `.shell` 上，首页的抽屉拿不到。
 * 这里自带一套 `--d-*`，见样式块。
 */

/**
 * 用户选的是不是深色。
 *
 * 和 `isChatDark` 不是一回事：那个是「当前页面实际渲染成什么样」，
 * 在首页上永远是 false；这个是「偏好是什么」。白天/黑夜两个按钮的选中态
 * 要看偏好——看 isChatDark 的话，用户在首页选深色会看不到任何按钮被点亮。
 */
const darkSelected = computed(() => theme.value === 'dark')

/* ------------------------------------------------------------------ 头像 */

const avatarUrl = computed(() => auth.user()?.avatar || '')
const initial = computed(() => {
  const name = auth.user()?.nickname || auth.user()?.username || ''
  return name.trim().charAt(0).toUpperCase() || '?'
})

const fileInput = ref(null)
const avatarBusy = ref(false)
/** 存描述符不是句子，见 i18n/index.js 的 message()：存句子的话切语言不会跟着变 */
const avatarError = ref(null)

const avatarErrorText = computed(() => resolveMessage(avatarError.value))

function pickAvatar() {
  fileInput.value?.click()
}

async function onAvatarPicked(event) {
  const file = event.target.files?.[0]
  // 选完立刻把 input 清空：不清的话，用户选同一个文件第二次不会触发 change，
  // 看起来就像「点了没反应」
  event.target.value = ''
  if (!file) return

  avatarBusy.value = true
  avatarError.value = null
  try {
    // 成功后 api 层已经把新的用户信息写回本地，头像会自动刷新
    await uploadAvatar(file)
  } catch (err) {
    avatarError.value = err?.message ? rawMessage(err.message) : message('settings.avatar.failed')
  } finally {
    avatarBusy.value = false
  }
}

/* ------------------------------------------------------------------ 画像 */

/**
 * 这一块只显示两份摘要，不显示表单：真正的编辑在 `/profile` 那一页。
 *
 * 两个 `/api/profile` 的请求（读和写）都跟着表单搬走了，抽屉现在只读一次。
 * 它是 `v-if` 挂载的，每次打开都会重新走一遍 onMounted，所以摘要不会过期。
 */
const profile = ref(null)
/** 体重流水（新的在前，后端保证），摘要里那个「最近 71.5kg」用它 */
const weightHistory = ref([])
const loading = ref(true)
const loadError = ref(null)
/** 「未登录」和「加载失败」是两种状态，得分开：前者不该报错，后者不该显示成空的 */
const loggedIn = computed(() => auth.isLoggedIn())

const loadErrorText = computed(() => resolveMessage(loadError.value))

/**
 * 基本情况的摘要：「30 岁 · 170cm · 71.5kg」。
 *
 * 年龄、身高、体重三项——它们是最常被问到、也最常变的三项，一行放得下。
 * 目标、饮食偏好、忌口不在这里列：它们通常是一整句话（「三个月减到 65 公斤」），
 * 拼进来这一行就折成两三行了，而抽屉里这个块的本意是「一眼看过、点进去看全部」。
 *
 * 取整和删掉多余的 .0 走 `String(Number(...))`：后端那两列是 DECIMAL，
 * 68 读出来是 68.0，直接拼进去会变成「170.0cm」。
 */
const profileSummary = computed(() => {
  const data = profile.value ?? {}
  const parts = []
  // 键的前缀是 settings.profile.*，**和全局的 profile.* 不是一组**：这一行是抽屉
  // 自己的东西（页面那边有它的完整版本）。写错前缀的表现不是报错，而是界面上
  // 直接显示出 `profile.summary.age` 这种键名——见 check-i18n 里那条「代码里用了
  // 但语言包里没有」的检查，那是为了不再靠肉眼发现这类错
  if (Number.isFinite(data.age)) parts.push(t('settings.profile.summary.age', { value: data.age }))
  if (Number.isFinite(data.heightCm)) {
    parts.push(t('settings.profile.summary.height', { value: String(Number(data.heightCm)) }))
  }
  if (Number.isFinite(data.weightKg)) {
    parts.push(t('settings.profile.summary.weight', { value: String(Number(data.weightKg)) }))
  }
  return parts.join(' · ')
})

/**
 * 摘要里那个「最近 71.5kg」。
 *
 * 取第一条而不是自己按时间排一遍：顺序是后端 `ORDER BY recorded_at DESC` 定的，
 * 前端再排一次只是多一个可能和后端不一致的地方。
 */
const latestWeight = computed(() => {
  const newest = weightHistory.value.find((point) => Number.isFinite(point?.weightKg))
  return newest ? String(Number(newest.weightKg.toFixed(1))) : ''
})

async function loadProfile() {
  try {
    const data = await fetchProfile()
    profile.value = data
    // 同一个 GET /api/profile 已经在返回流水了，不额外发请求
    weightHistory.value = data?.weightHistory ?? []
  } catch (err) {
    loadError.value = err?.message ? rawMessage(err.message) : message('profile.loadFailed')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!loggedIn.value) {
    loading.value = false
    return
  }
  loadProfile()
})

/* ------------------------------------------------------------------ 开合 */

function onKeydown(event) {
  if (event.key === 'Escape') closeSettings()
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="drawer-root" :class="{ dark: isChatDark }">
    <!-- 遮罩：点它就关。和窄屏侧边栏那个 .scrim 是同一个套路 -->
    <div class="scrim" @click="closeSettings"></div>

    <aside class="drawer" role="dialog" aria-modal="true" :aria-label="$t('settings.title')">
      <header class="head">
        <h2>{{ $t('settings.title') }}</h2>
        <button class="icon-btn" type="button" :title="$t('common.close')" @click="closeSettings">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </header>

      <div class="body">
        <!-- ============================ 头像 ============================ -->
        <section class="block">
          <h3>{{ $t('settings.avatar.title') }}</h3>
          <div class="avatar-row">
            <span class="avatar">
              <img v-if="avatarUrl" :src="avatarUrl" :alt="initial" />
              <template v-else>{{ initial }}</template>
            </span>
            <div class="avatar-actions">
              <button class="btn" type="button" :disabled="avatarBusy" @click="pickAvatar">
                {{ avatarBusy ? $t('settings.avatar.uploading') : $t('settings.avatar.upload') }}
              </button>
              <p class="note">{{ $t('settings.avatar.note') }}</p>
            </div>
            <!-- 真正的 file input 藏起来：原生的那个按钮样式改不动，
                 而且各浏览器长得都不一样。label 包一层的话又要处理键盘焦点 -->
            <input
              ref="fileInput"
              class="file-input"
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif"
              @change="onAvatarPicked"
            />
          </div>
          <p v-if="avatarErrorText" class="error">{{ avatarErrorText }}</p>
        </section>

        <!-- ============================ 外观 ============================ -->
        <section class="block">
          <h3>{{ $t('settings.appearance.title') }}</h3>
          <!-- 选中态看的是 theme（用户的偏好），不是 isChatDark（当前页面实际渲染成什么样）：
               在首页上把偏好切成深色时，首页仍然是浅色的，但这两个按钮必须正确反映
               「你选的是深色」——否则用户会以为没点中，反复点 -->
          <div class="segment">
            <button
              class="seg"
              type="button"
              :class="{ on: !darkSelected }"
              @click="setTheme('light')"
            >
              {{ $t('settings.appearance.light') }}
            </button>
            <button
              class="seg"
              type="button"
              :class="{ on: darkSelected }"
              @click="setTheme('dark')"
            >
              {{ $t('settings.appearance.dark') }}
            </button>
          </div>
          <p class="note">
            {{ $t('settings.appearance.note') }}
          </p>
        </section>

        <!-- ============================ 语言 ============================ -->
        <section class="block">
          <h3>{{ $t('settings.language.title') }}</h3>
          <!-- 语言是**全局**的，和主题不一样：没有「首页中文、对话页英文」这种状态，
               所以在哪一页切都立刻全站生效。选项标签用它自己的语言写，见 i18n/index.js -->
          <div class="segment">
            <button
              v-for="option in LOCALE_OPTIONS"
              :key="option.value"
              class="seg"
              type="button"
              :class="{ on: localeRef === option.value }"
              @click="setLocale(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
          <p class="note">{{ $t('settings.language.note') }}</p>
        </section>

        <!-- ========================== 我的情况 ==========================
             这里只留摘要和入口，表单在 /profile 那一页。理由和下面体重那块同源：
             画像是一份会被反复查看的资料，一行摘要够用；编辑该落在一页放得下
             概览 + 表单的页面上 -->
        <section class="block">
          <h3>{{ $t('profile.title') }}</h3>

          <p v-if="!loggedIn" class="note">{{ $t('profile.needLogin') }}</p>
          <p v-else-if="loading" class="note">{{ $t('common.loading') }}</p>
          <p v-else-if="loadErrorText" class="error">{{ loadErrorText }}</p>

          <template v-else>
            <div class="entry">
              <p class="entry-line">
                <span v-if="profileSummary" class="entry-value">{{ profileSummary }}</span>
                <!-- 一项都没填过时给的是「去填一次」，而不是列出几个空值 -->
                <span v-else class="entry-empty">{{ $t('settings.profile.empty') }}</span>
              </p>
              <RouterLink class="entry-link" to="/profile" @click="closeSettings">
                {{ $t('settings.profile.link') }} →
              </RouterLink>
            </div>

            <!-- 体重变化**不在这里画图了**，只留一句摘要和一个入口。
                 搬走的原因：抽屉里放不下它真正需要的东西——一张能读出刻度的图、
                 几个时间窗口的变化、以及一份可以核对和删除的记录列表；
                 而这一段要滚过整张表单才看得到，进去还要两步。
                 数据还是同一次 fetchProfile，不额外发请求 -->
            <div class="entry">
              <p class="entry-line">
                <span class="entry-title">{{ $t('settings.profile.weightTitle') }}</span>
                <span v-if="latestWeight" class="entry-value">
                  {{ $t('settings.profile.weightSummary', { value: latestWeight }) }}
                </span>
                <span v-else class="entry-empty">{{ $t('settings.profile.weightEmpty') }}</span>
              </p>
              <RouterLink class="entry-link" to="/weight" @click="closeSettings">
                {{ $t('settings.profile.weightLink') }} →
              </RouterLink>
            </div>
          </template>
        </section>
      </div>
    </aside>
  </div>
</template>

<style scoped>
/*
 * 自带一套颜色变量，而不是共用 ChatRoom 的 --c-*：那些定义在 `.shell` 上，
 * 而这个抽屉挂在 App.vue 上——首页打开它时根本不是 `.shell` 的后代，拿不到。
 * 两套变量各管各的，代价是颜色值重复了一遍，换来的是抽屉在哪儿都能正确渲染。
 */
.drawer-root {
  --d-bg: #fff;
  --d-head: #fbfbfc;
  --d-line: var(--line);
  --d-text: var(--ink);
  --d-muted: var(--muted);
  --d-input-bg: #fff;
  --d-input-line: var(--line-strong);
  --d-hover: #f3f5fa;
  --d-danger: var(--danger);
}

.drawer-root.dark {
  --d-bg: #1e2026;
  --d-head: #14161b;
  --d-line: #2e323a;
  --d-text: #e6e8ee;
  --d-muted: #8b93a3;
  --d-input-bg: #262a31;
  --d-input-line: #33373f;
  --d-hover: #262a31;
  --d-danger: #f08a86;
}

.scrim {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(16, 20, 30, 0.32);
}

.drawer-root.dark .scrim {
  background: rgba(0, 0, 0, 0.55);
}

.drawer {
  position: fixed;
  top: 0;
  bottom: 0;
  /* 从左边出来：入口在左下角的用户菜单里，从那一侧展开最顺 */
  left: 0;
  z-index: 61;
  display: flex;
  flex-direction: column;
  width: min(380px, 92vw);
  border-right: 1px solid var(--d-line);
  background: var(--d-bg);
  color: var(--d-text);
  box-shadow: 0 0 40px rgba(16, 20, 30, 0.18);
  /* v-if 挂载出来的，没有退出过渡，所以只用入场动画 */
  animation: drawer-in 0.22s cubic-bezier(0.16, 0.84, 0.44, 1);
}

@keyframes drawer-in {
  from {
    transform: translateX(-100%);
  }
}

.head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
  padding: 14px 14px 12px 18px;
  border-bottom: 1px solid var(--d-line);
  background: var(--d-head);
}

.head h2 {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 9px;
  background: none;
  color: var(--d-muted);
}
.icon-btn:hover {
  background: var(--d-hover);
  color: var(--d-text);
}
.icon-btn svg {
  width: 16px;
  height: 16px;
}

.body {
  flex: 1;
  overflow-y: auto;
  padding: 4px 18px 28px;
}

.block {
  padding: 18px 0;
  border-bottom: 1px solid var(--d-line);
}
.block:last-child {
  border-bottom: none;
}

.block h3 {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--d-text);
}

/* ---------------------------------------------------------------- 头像 */

.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  overflow: hidden;
}

.avatar img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-actions {
  flex: 1;
  min-width: 0;
}

/* 原生的 file input 各浏览器长得都不一样，而且改不动样式，
   所以藏起来、用按钮去触发它 */
.file-input {
  display: none;
}

/* ------------------------------------------------------------ 分段控件 */

.segment {
  display: flex;
  gap: 6px;
}

.seg {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--d-input-line);
  border-radius: 9px;
  background: var(--d-input-bg);
  color: var(--d-text);
  font-size: 13px;
  transition: border-color 0.16s ease, color 0.16s ease;
}
.seg:hover {
  border-color: var(--brand);
  color: var(--brand);
}
.seg.on {
  border-color: var(--brand);
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 500;
}

/* ------------------------------------------------------------ 两个入口 */

/*
 * 「我的情况」和「体重变化」都搬去了各自的页面（/profile 和 /weight），
 * 抽屉里只剩摘要 + 入口。两块用同一个形状：左边一行摘要，右边一个链接块。
 * 两块之间用虚线分开——读起来像「以下是另一件事」，和 .block 之间那条实线区分开。
 */
.entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--d-line);
}
/* 第一块紧跟在标题下面，不需要自己那条分隔线 */
.entry:first-of-type {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}

.entry-line {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin: 0;
  min-width: 0;
}

.entry-title {
  color: var(--d-muted);
  font-size: 12.5px;
}

.entry-value {
  color: var(--d-text);
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.entry-empty {
  color: var(--d-muted);
  font-size: 13px;
}

/* 入口做成一个明确的链接块：它现在是把人送去那一页的唯一线索 */
.entry-link {
  flex: none;
  padding: 6px 12px;
  border: 1px solid var(--d-line);
  border-radius: 8px;
  color: var(--d-text);
  font-size: 12.5px;
  text-decoration: none;
  white-space: nowrap;
  transition: border-color 0.18s ease, color 0.18s ease;
}
.entry-link:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.btn {
  padding: 9px 16px;
  border: 1px solid var(--d-input-line);
  border-radius: 9px;
  background: var(--d-input-bg);
  color: var(--d-text);
  font-size: 13.5px;
  transition: border-color 0.16s ease, color 0.16s ease, opacity 0.16s ease;
}
.btn:hover:not(:disabled) {
  border-color: var(--brand);
  color: var(--brand);
}
.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn.primary {
  border-color: transparent;
  background: var(--brand);
  color: #fff;
}
.btn.primary:hover:not(:disabled) {
  background: var(--brand-deep);
  color: #fff;
}

.note {
  margin-top: 10px;
  color: var(--d-muted);
  font-size: 12px;
  line-height: 1.6;
}
.avatar-actions .note {
  margin-top: 6px;
}

.error {
  margin-top: 10px;
  color: var(--d-danger);
  font-size: 12.5px;
  line-height: 1.6;
}
</style>
