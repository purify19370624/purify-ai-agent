<script setup>
import { LOCALE_OPTIONS, localeRef, setLocale } from '../i18n/index.js'

/**
 * 顶栏上的中英文切换 —— 仿 DeepSeek 那颗分段胶囊：灰底、白色滑块、短标签。
 *
 * **为什么和设置抽屉里那组按钮不共用一个组件**：两处的形态差别不是「大小」，
 * 而是取舍不同。设置抽屉里地方宽裕，写得全（「简体中文」/「English」），
 * 是一个正经的设置项；顶栏这一颗要和品牌名、用户菜单挤在一行里，
 * 只能用「中文 / EN」这种短标签。硬合并的话，生成出来的那个组件会同时带
 * 两套布局和两套标签，比分开写更长。
 *
 * **但取值和落盘只有一份**：两个组件都从 `i18n/index.js` 拿 `LOCALE_OPTIONS`，
 * 都调 `setLocale`。语言是全局状态，切换动作只写一遍（那个函数里同时改了状态、
 * localStorage 和 `<html lang>`，抄第二份必然漏掉其中一样）。
 *
 * 标签不翻译：语言名用它自己的语言写（`中文` / `EN`），
 * 用户看不懂当前界面语言时，这两个词仍然认得出来 —— 这也是各家设置页的通行做法。
 */
</script>

<template>
  <!-- role=group + aria-pressed：读屏软件能念出「切换语言，中文，已选中」。
       没做成 radiogroup 是因为它不需要方向键那套交互，两个按钮各自可点更简单 -->
  <div class="switch" role="group" :aria-label="$t('home.localeSwitch')">
    <button
      v-for="option in LOCALE_OPTIONS"
      :key="option.value"
      type="button"
      class="seg"
      :class="{ on: localeRef === option.value }"
      :aria-pressed="localeRef === option.value"
      @click="setLocale(option.value)"
    >
      {{ option.short }}
    </button>
  </div>
</template>

<style scoped>
.switch {
  display: inline-flex;
  align-items: center;
  /* gap 只留 2px：滑块和底槽之间那条缝就是这个间距，再大就不像一体了 */
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  /* 底槽比页面底色深一档，白色滑块才有「浮在上面」的层次 */
  background: #eef0f5;
}

.seg {
  /* 上下 4px：加上 2px 的底槽内边距和 1px 描边，整颗约 31px 高，
     和右边的用户菜单（也是 6px 内边距那个量级）站在一起不显矮 */
  padding: 4px 11px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font: inherit;
  font-size: 12.5px;
  line-height: 1.5;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease;
}
.seg:hover {
  color: var(--ink);
}
.seg.on {
  background: #fff;
  color: var(--ink);
  /* 浅投影而不是描边：DeepSeek 那颗滑块是没有边框的，靠投影分开 */
  box-shadow: 0 1px 2px rgba(10, 15, 30, 0.1);
}
.seg:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 1px;
}
</style>
