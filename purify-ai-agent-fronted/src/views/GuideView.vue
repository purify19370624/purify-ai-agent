<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import * as auth from '../auth.js'
import { SLIM, MANUS } from '../chatConfig.js'

/**
 * 使用说明页。
 *
 * <h2>它为什么存在</h2>
 *
 * 首页只有一屏，只能说「这东西能帮你做什么」；而「怎么用才顺手」这件事一屏放不下，
 * 硬塞进去会把首页变成说明书。所以首页页脚那一栏「关于」指到这一页，
 * 想了解的人自己点进来。**它是公开页**：还没注册的人也该能先看清楚这是什么。
 *
 * <h2>这一页的三段结构</h2>
 *
 * 1. **平台有什么** —— 三个入口各管一件事，各自带一个「进入」。读到这里的人
 *    通常还不知道该点哪个，所以这一段的职责是分流，不是罗列功能；
 * 2. **怎么用最省事** —— 整页的重点，按「先做哪件」排。每一条都是**一个能立刻照做的动作**
 *    （填一次画像、直接提问、把任务说具体……），不是「本平台支持以下特性」；
 * 3. **它不做什么** —— 留着不是免责，是预期管理：用户知道边界在哪，
 *    才不会因为一次「它不肯给减肥药建议」而觉得它没用。
 *
 * 最后是「三步上手」，给已经读完、准备开始的人一个落点。
 *
 * <h2>两条内容上的约束</h2>
 *
 * 一、**文案全在语言包里**（`guide.*`），这一页只负责排版和分流。
 * 二、**不许把话说大**：页面上每一条都能在代码里找到依据，
 * 具体是哪几处写在 `i18n/zh-CN.js` 的 `guide` 块开头，加内容前先读那一段。
 */
const { t, tm, rt } = useI18n()

const isAdmin = computed(() => auth.isAdmin())

/**
 * 三个入口。
 *
 * **名称不在语言包里重写一遍**：`nameKey` 直接指到现成的那两个键
 * （对话页标题、知识库页标题）。抄一份到 `guide.parts.*` 里的话，
 * 哪天改了链路名，这一页会留在旧名字上，而那种不一致没人会去核对。
 */
const PARTS = [
  { key: 'slim', to: '/slim', nameKey: 'chat.slim.title', accent: SLIM.accent },
  { key: 'manus', to: '/manus', nameKey: 'chat.manus.title', accent: MANUS.accent },
  { key: 'knowledge', to: '/knowledge', nameKey: 'knowledge.title', accent: SLIM.accent, adminOnly: true },
]

/**
 * 「怎么用最省事」的条目顺序。
 *
 * **键名和语言包里 `guide.tips.*` 一一对应，加一条要两处一起改。** 这里的键是拼出来的
 * （`guide.tips.${key}.title`），check-i18n 只认字面量，看不见它们——
 * 拼错一个的表现是那一格的标题位置上直接显示出键名，而脚本不会报错。
 * 这和 ChatRoom 里 REMEMBER_ITEM_KEYS 那张表是同一个约定。
 */
const TIPS = [
  'fillProfile',
  'justAsk',
  'sayIt',
  'beSpecific',
  'oneQuestion',
  'library',
  'aborted',
  'knowledge',
  'weight',
]

/**
 * 只给超级用户看的两处：知识库那张卡和那一条提示。
 *
 * 理由和首页那张卡完全一样（见 HomeView 的 `adminOnly`）：知识库页要管理权限，
 * 普通用户点进去先被守卫弹回首页，登录完发现还是没有权限，白跑一趟。
 * 这一页的描述也一样——跟一个进不去的人讲「传资料时要选分类」纯属噪音。
 *
 * 标记放在这里而不是语言包里：「给谁看」是逻辑，不是文案，
 * 而文案要跟着语言走、逻辑不该跟着走。
 */
const ADMIN_ONLY_TIPS = new Set(['knowledge'])

const visibleParts = computed(() => PARTS.filter((part) => !part.adminOnly || isAdmin.value))
const visibleTips = computed(() => TIPS.filter((key) => !ADMIN_ONLY_TIPS.has(key) || isAdmin.value))

/**
 * 语言包给的是**数组**，要用 tm + rt 取（理由同 ChatRoom 的 examples：
 * `t('...steps[0]')` 那种下标写法能work但不保证）。rt 在「消息已被编译成函数」
 * 的那套构建下才是必需的，在另一套下是恒等函数，两边都写一遍就不用关心用的是哪种。
 *
 * <p><b>rt 只能作用在字符串或消息函数上，不能整个丢一个对象给它。</b>
 * `steps` 的每一项是 `{ title, body }`，所以这里**逐个字段**解，不能写成
 * `.map((step) => rt(step))` —— 那样第一次渲染就抛 `Invalid arguments`，
 * 表现是整页空白。这个错**构建期发现不了**（模板编译是过的），
 * 所以下面 limits 那种「数组里是纯字符串」的可以直接 map，这一处不行。
 */
const steps = computed(() =>
  tm('guide.start.steps').map((step) => ({
    title: rt(step.title),
    body: rt(step.body),
  })),
)

/** `limits.items` 是纯字符串数组，可以整项解（和上面的区别见 steps 的注释） */
const limitItems = computed(() => tm('guide.limits.items').map((item) => rt(item)))
</script>

<template>
  <div class="page">
    <header class="bar">
      <RouterLink to="/" class="back" :title="$t('guide.backHome')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M15 18l-6-6 6-6" />
        </svg>
      </RouterLink>
      <h1>{{ $t('guide.title') }}</h1>
    </header>

    <main class="body">
      <!-- ============================ 这是什么 ============================
           一句话定位。这一块刻意做成一张带淡蓝光晕的卡片而不是几行裸文字：
           它是整页的「封面」，往下那些分块才是正文 -->
      <section class="hero">
        <p class="eyebrow">{{ $t('guide.hero.eyebrow') }}</p>
        <h2 class="slogan">{{ $t('guide.hero.slogan') }}</h2>
        <p class="lede">{{ $t('guide.hero.lede') }}</p>
      </section>

      <!-- ========================== 平台有什么 ==========================
           列数跟着实际卡片数走：普通用户只有两张（知识库那张被过滤掉了），
           写死三列会在右边留一个空洞 -->
      <section class="block">
        <h2 class="block-title">{{ $t('guide.parts.title') }}</h2>
        <p class="block-intro">{{ $t('guide.parts.intro') }}</p>

        <div class="cards" :class="`cols-${visibleParts.length}`">
          <!-- 整张卡是 RouterLink（渲染成 <a>），键盘 Enter 天然可用。
               每个入口只在自己图标底上用那条链路的主题色，和首页卡片同一套做法 -->
          <RouterLink
            v-for="part in visibleParts"
            :key="part.key"
            class="entry"
            :to="part.to"
            :style="{ '--ca': part.accent, '--ca-soft': part.accent + '1f' }"
          >
            <h3 class="entry-name">{{ $t(part.nameKey) }}</h3>
            <p class="entry-what">{{ $t(`guide.parts.${part.key}.what`) }}</p>
            <p class="entry-desc">{{ $t(`guide.parts.${part.key}.desc`) }}</p>
            <span class="entry-open">{{ $t('guide.parts.open') }} →</span>
          </RouterLink>
        </div>
      </section>

      <!-- ========================= 怎么用最省事 =========================
           整页的重点。ol 而不是 ul：这一节的「顺序」本身是信息（先做哪件） -->
      <section class="block">
        <h2 class="block-title">{{ $t('guide.tips.title') }}</h2>
        <p class="block-intro">{{ $t('guide.tips.intro') }}</p>

        <ol class="tips">
          <li v-for="(key, index) in visibleTips" :key="key" class="tip">
            <!-- 序号是我们画出来的装饰，不再是 list-item 的 marker，所以对读屏隐藏 -->
            <span class="tip-no" aria-hidden="true">{{ index + 1 }}</span>
            <div class="tip-text">
              <h3 class="tip-title">{{ $t(`guide.tips.${key}.title`) }}</h3>
              <p class="tip-body">{{ $t(`guide.tips.${key}.body`) }}</p>
            </div>
          </li>
        </ol>
      </section>

      <!-- ========================== 它不做什么 ==========================
           底色比上面几块暖一档：这一节是边界，视觉上该和「功能介绍」区分开。
           但只用一层很淡的米色 —— 做成红底黄框就等于在喊「有风险」，
           而这几条其实是「它很克制」的证据 -->
      <section class="block limits">
        <h2 class="block-title">{{ $t('guide.limits.title') }}</h2>
        <p class="block-intro">{{ $t('guide.limits.intro') }}</p>

        <ul class="limit-list">
          <li v-for="(item, index) in limitItems" :key="index">{{ item }}</li>
        </ul>

        <p class="footnote">{{ $t('guide.limits.footnote') }}</p>
      </section>

      <!-- =========================== 三步上手 ===========================
           读完整页、准备开始的人的落点。按钮指向轻语：两条链路里它最好上手 -->
      <section class="block">
        <h2 class="block-title">{{ $t('guide.start.title') }}</h2>

        <ol class="steps">
          <li v-for="(step, index) in steps" :key="index" class="step">
            <span class="step-no" aria-hidden="true">{{ index + 1 }}</span>
            <h3 class="step-title">{{ step.title }}</h3>
            <p class="step-body">{{ step.body }}</p>
          </li>
        </ol>

        <div class="start-cta">
          <RouterLink class="cta" to="/slim">
            {{ $t('guide.start.cta') }}
            <span aria-hidden="true">→</span>
          </RouterLink>
          <!-- 「不用先填资料」这一句是必要的：不写的话，读到这里的人会以为
               得先把上面那九条都照做一遍才能开始 -->
          <p class="cta-note">{{ $t('guide.start.ctaNote') }}</p>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
/*
 * 配色一律走 base.css 的全局变量（--page / --ink / --line / --brand …）：
 * 这一页和首页、登录页一样是**永远浅色**的，不参与对话页那套深色主题
 * （深色只在对话页生效，见 base.css 里 html.theme-dark 那一段）。
 * 所以这里不需要 --c-* 那批 token，也就不该去引 ChatRoom 的样式。
 */
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

.body {
  max-width: 880px;
  margin: 0 auto;
  padding: 26px 24px 64px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ---------------------------------------------------------------- 封面 */

.hero {
  padding: 26px 26px 28px;
  border: 1px solid var(--line);
  border-radius: 16px;
  /* 一层很淡的径向蓝，和首页顶部那层光是同一支颜色。
     纯色 + 一道光晕，不做渐变标题（理由见 base.css 里 --brand 那段） */
  background:
    radial-gradient(ellipse 72% 130% at 0% 0%, rgba(77, 107, 254, 0.09), transparent 68%),
    #fff;
}

.eyebrow {
  color: var(--brand);
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.slogan {
  margin-top: 10px;
  font-size: clamp(20px, 2.6vw, 27px);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.3;
  text-wrap: balance;
}

.lede {
  margin-top: 12px;
  max-width: 46em;
  color: var(--muted);
  font-size: 14.5px;
  line-height: 1.85;
}

/* ---------------------------------------------------------------- 分块 */

.block {
  padding: 22px 24px 24px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
}

.block-title {
  font-size: 16px;
  font-weight: 600;
}

.block-intro {
  margin-top: 8px;
  color: var(--muted);
  font-size: 13.5px;
  line-height: 1.8;
}

/* ------------------------------------------------------------ 三个入口 */

.cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

/* 普通用户只有两张卡（知识库那张被过滤掉了）。写死三列的话，
   第二张右边会空出一块，看起来像加载失败 */
.cards.cols-2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.cards.cols-1 {
  grid-template-columns: minmax(0, 1fr);
}

.entry {
  display: flex;
  flex-direction: column;
  padding: 16px 16px 14px;
  border: 1px solid var(--line);
  border-radius: 13px;
  background: #fcfcfe;
  color: inherit;
  text-decoration: none;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}
.entry:hover,
.entry:focus-visible {
  border-color: var(--ca);
  transform: translateY(-2px);
  /* 位移压到 2px：卡片几乎不动，靠描边变色给反馈（和首页卡片同一个手感） */
  box-shadow: 0 14px 30px -20px rgba(10, 15, 30, 0.35);
}

.entry-name {
  font-size: 15px;
  font-weight: 600;
}

.entry-what {
  margin-top: 4px;
  color: var(--ca);
  font-size: 12px;
}

.entry-desc {
  margin-top: 10px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.78;
}

/* margin-top: auto 把它压到卡片底部：两张卡文字长短不一时，「进入」也对齐 */
.entry-open {
  margin-top: auto;
  padding-top: 14px;
  color: var(--ca);
  font-size: 12.5px;
}

/* ---------------------------------------------------------- 怎么用最省事 */

.tips {
  display: flex;
  flex-direction: column;
  gap: 15px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}

.tip {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.tip-no {
  display: grid;
  place-items: center;
  flex: none;
  width: 24px;
  height: 24px;
  /* 跟标题那一行对齐，而不是跟整块居中 */
  margin-top: 1px;
  border-radius: 8px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.tip-title {
  font-size: 14px;
  font-weight: 600;
}

.tip-body {
  margin-top: 5px;
  color: var(--muted);
  font-size: 13.5px;
  line-height: 1.82;
}

/* ------------------------------------------------------------ 它不做什么 */

.limits {
  background: #fffdf8;
  border-color: #f0e7d8;
}

.limit-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin: 15px 0 0;
  padding-left: 18px;
  color: #4a5468;
  font-size: 13.5px;
  line-height: 1.82;
}
.limit-list li::marker {
  color: #c9a86a;
}

.footnote {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed #ece2d0;
  color: #8b93a3;
  font-size: 12.5px;
  line-height: 1.75;
}

/* ------------------------------------------------------------ 三步上手 */

.steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}

.step {
  padding: 15px 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fbfcfe;
}

.step-no {
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.step-title {
  margin-top: 10px;
  font-size: 14px;
  font-weight: 600;
}

.step-body {
  margin-top: 5px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.78;
}

.start-cta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 11px;
  margin-top: 22px;
}

.cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 11px 30px;
  border-radius: 11px;
  background: var(--brand);
  color: #fff;
  font-size: 14.5px;
  font-weight: 500;
  text-decoration: none;
  /* 同色系投影而不是黑色（理由见 base.css 的 .btn-primary） */
  box-shadow: 0 10px 24px -14px rgba(77, 107, 254, 0.9);
  transition: background 0.18s ease, transform 0.18s ease;
}
.cta:hover,
.cta:focus-visible {
  background: var(--brand-deep);
  transform: translateY(-1px);
}

.cta-note {
  color: #8b93a6;
  font-size: 12.5px;
}

/* ---------------------------------------------------------------- 响应式 */

@media (max-width: 720px) {
  .body {
    padding: 16px 12px 48px;
  }
  /* 三种列数一起归到单列：分开写会在 cols-2 那档漏掉一个（漏掉的表现是
     卡片被挤成两栏，窄屏上每栏只剩一百多像素） */
  .cards,
  .cards.cols-2,
  .cards.cols-3,
  .steps {
    grid-template-columns: minmax(0, 1fr);
  }
  .hero,
  .block {
    padding: 18px 16px 20px;
  }
}
</style>
