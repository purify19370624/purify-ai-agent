/**
 * English copy.
 *
 * 键必须和 `zh-CN.js` 一字不差地对齐 —— 这里缺一个键不会有任何构建期报错，
 * 表现是「切到英文之后某处突然变成中文」（vue-i18n 会回退到默认语言），
 * 而且只在那个页面被打开时才看得见。加键的时候两个文件一起加。
 *
 * 专有名词的处理：品牌名 `Purify AI` / `PurifyManus` 原样保留；
 * 「轻语」音译成 `Qingyu`（对英文用户来说，一个认不出的汉字比一个能读出来的名字糟得多）。
 */
export default {
  common: {
    loading: 'Loading…',
    refresh: 'Refresh',
    close: 'Close',
    save: 'Save',
    saving: 'Saving…',
    saved: 'Saved',
    delete: 'Delete',
    download: 'Download',
    cancel: 'Cancel',
  },

  menu: {
    login: 'Sign in',
    admin: 'Super admin',
    knowledge: 'Knowledge base',
    profile: 'About me',
    weight: 'Weight trend',
    resources: 'Library',
    settings: 'Settings',
    logout: 'Sign out',
  },

  /*
   * Home page copy speaks to the **person using it**, not the person who built it:
   * no framework names, no port numbers, no tech stack, and none of “chain / index /
   * vector search / MCP / multi-step” — words that only mean something if you have
   * read this repo.
   *
   * Write it the way someone would **say** it, not the way a spec sheet states it:
   * “ask about food, exercise or weight” beats “supports Q&A across the diet, exercise
   * and weight-management domains”; “says so when it can’t find it” beats “has
   * uncertainty-expression capability”. If a line doesn’t sound like speech, it isn’t
   * finished.
   *
   * The other rule is **don’t oversell**: “looks things up before answering” is here
   * because retrieval really does run before the answer (when a category keyword hits,
   * see purify.rag.router), “won’t invent a number” is here because the prompt says so,
   * and “not medical advice” is here because all three system prompts refuse to
   * diagnose. Any praise that can’t be traced to that code has no business on this page.
   */
  home: {
    navManus: 'PurifyManus',
    navKnowledge: 'Knowledge base',
    // Screen-reader label for the language pill in the top bar; it's never drawn
    localeSwitch: 'Switch language',
    chip: 'It chats with you, and it gets things done',
    // Same line as the Chinese one: “brand · what this is”. From here on **Qingyu is the
    // platform name** (it used to name only the health-advisor chain), so the lede and
    // cards below have to be careful which sense they mean. This is also the longest
    // line on the page — the headline size cap in HomeView is set so it stays on one line
    slogan: 'Qingyu · AI health advisor & agent platform',
    lede: 'Ask Qingyu about food, exercise or losing weight. Ask PurifyManus to get something done. Both look things up first, and both say so when they come up empty instead of making something up.',
    cta: 'Start chatting',
    ctaNote: 'Chat with Qingyu · just tell it your height, weight and goal',
    entries: 'Other entry points',
    footChat: 'Chat',
    footKnowledge: 'Knowledge base',
    footDocs: 'Upload and manage files',
    footSearch: 'Try a search',
    footAbout: 'About',
    footAboutText: 'It talks health with you, and it gets things done for you.',
    // The footer fine print no longer carries the tech stack — framework names and port
    // numbers mean nothing to a user, and the repo documents them anyway. What goes here
    // is something that actually helps: all three system prompts refuse to diagnose
    footDeploy: 'General guidance only — not medical advice',
    cardSlim: 'Your weight-loss advisor. Ask it about food, exercise or weight — it looks things up before answering, and when it can’t find something it says so instead of inventing a number.',
    cardSlimMeta: 'Chat anytime · ask anything about weight',
    cardManus: 'Hand it a job — research, planning, files — and it works through it for you. When it needs more from you, it stops and asks instead of guessing.',
    cardManusMeta: 'Researches and does the work · asks when unsure',
    cardKnowledge: 'Bring your files in and the chat can look them up. See how each one was split apart, and search a sentence yourself to check what comes back.',
    cardKnowledgeMeta: 'Upload files · try a search',
  },

  /* --------------------------------------------------------- how to use */

  /*
   * The usage guide. Reachable from the “About” column in the home page footer.
   *
   * It inherits both rules from the home copy (see the note at the top of the home block):
   * written for **people using it**, no framework names or infrastructure words — the home
   * checker only scans the home block, so this page holds the same line by hand.
   *
   * The stricter half matters more: **don’t claim more than the code does.** Every line below
   * traces back to something real — the wording of the three system prompts (answer first,
   * ask at most one question per turn, no diagnosis), the tool list ToolConfig actually
   * registers, the knowledge page’s real limit (txt / md only), and the weight page’s
   * “less than 7 days of entries, no comparison” rule.
   *
   * The entry-card names are not repeated here (they would drift from chat.*.title); the
   * component looks those up by nameKey and only the “what it is / what it does” lives here.
   */
  guide: {
    title: 'How to use Purify AI',
    backHome: 'Back to home',

    hero: {
      eyebrow: 'Purify AI',
      slogan: 'An AI assistant platform for losing weight sensibly',
      lede: 'It wears two hats: Qingyu, an advisor you ask questions, and PurifyManus, an agent that goes and does the job. Your details, weigh-ins and goal are stored once and shared by both.',
    },

    parts: {
      title: 'What’s in it',
      intro: 'Three places to start. Chat with Qingyu, hand a job to PurifyManus, or build a knowledge base so the answers come out of your own material.',
      open: 'Open',
      slim: {
        what: 'Advisor · question and answer',
        desc: 'Ask about food, exercise, weight — anything. It looks through the knowledge base before answering, and says so plainly when it finds nothing instead of inventing a number.',
      },
      manus: {
        what: 'Agent · finishes the job',
        desc: 'It can check the weather, search, read web pages, read and write files and generate PDFs, and it works out the next step on its own. It does what it can and hands it over, then asks for the one thing it’s missing.',
      },
      knowledge: {
        what: 'Your own material',
        desc: 'Bring your files in and the chat can look them up. You can see how each one was split apart, and search a sentence yourself to check what comes back.',
      },
    },

    /*
     * “Getting the most out of it” — the heart of the page, ordered by what to do first.
     *
     * The keys (fillProfile / justAsk / …) are referenced in order from the TIPS table in the
     * component, so **adding one means changing both places** — the same deal as
     * REMEMBER_ITEM_KEYS. The knowledge entry is hidden from non-admins (they can’t open the
     * knowledge page); that flag lives in the component, not here, because it’s about who
     * sees it, not about the wording.
     */
    tips: {
      title: 'Getting the most out of it',
      intro: 'Ordered by what to do first — the top two matter most. Come back for the rest when you need them.',
      fillProfile: {
        title: 'Fill in “About me” once',
        body: 'Height, weight, goal, anything to avoid — fill it in once and every conversation can use it. It pops up by itself the first time you open a chat; skipping is fine, and you can add to it later from “About me”.',
      },
      justAsk: {
        title: 'Just ask — you don’t have to hand over your numbers first',
        body: '“What’s a filling breakfast while cutting?” is answerable without knowing your height or weight. When it genuinely needs your numbers to be accurate, it says everything it can first and asks one question at the end — one per turn at most.',
      },
      sayIt: {
        title: 'Say it in passing and it will remember',
        body: '“I’m 175”, “I’m allergic to seafood” — it files that under “About me” so you don’t have to repeat it. When it does, a “Noted:” line shows up under the answer; hit “View” to see what it currently has.',
      },
      beSpecific: {
        title: 'Tell PurifyManus what you actually want',
        body: 'The more specific you are, the less it has to come back and ask. “Check tomorrow’s weather in Hangzhou” is weaker than “Check whether tomorrow is good for a run in Hangzhou — if it is, write the time and place to a file”.',
      },
      oneQuestion: {
        title: 'When it does ask, it asks one thing at a time',
        body: 'Just answer and it carries on with the full context. That’s why you don’t need to lay out all the background up front.',
      },
      library: {
        title: 'Everything it makes lands in your Library',
        body: 'Generated PDFs, downloaded files and written documents are all filed there — still there after you close the chat window or switch devices.',
      },
      aborted: {
        title: 'If a task gets halted, break it into smaller pieces',
        body: 'It stops itself when it’s going in circles — it would rather halt than hand you a padded answer. Split the request into two steps and ask again; that usually clears it.',
      },
      knowledge: {
        title: 'Knowledge base: upload first, name the category when you ask',
        body: 'txt and md only for now. Each file needs a category, and it only searches that category when the category name shows up in your question. Upload, then search a sentence to see what comes back.',
      },
      weight: {
        title: 'Log one weigh-in and the trend appears',
        body: 'Record it once on “Weight trend” and you get the curve, plus the change vs. 7 days and 30 days ago. Days you didn’t weigh in stay blank — it won’t draw you a line that isn’t there.',
      },
    },

    /*
     * “What it won’t do”. This section has to stay: it’s the product boundary and the user’s
     * expectation-setting in one. All four come from the safety sections of the three system
     * prompts — they are not promises the product made up for itself.
     */
    limits: {
      title: 'What it won’t do',
      intro: 'Hard limits, written into how it behaves.',
      items: [
        'It doesn’t diagnose anything, and it won’t recommend medication, meal replacements, diet teas or any prescription plan.',
        'It won’t suggest crash dieting, fasting, purging or over-exercising.',
        'It won’t promise “X pounds in Y days”, and it won’t judge how you look.',
        'If you bring up an eating disorder, weight anxiety or low mood, it leads with concern and suggests talking to a doctor or a counsellor rather than carrying on with weight advice.',
      ],
      footnote: 'Everything here only helps tailor what it says to you. It doesn’t diagnose anything and isn’t a substitute for a doctor.',
    },

    /* “Three steps to start” — if you remember one thing from this page, it’s these */
    start: {
      title: 'Three steps to start',
      steps: [
        { title: 'Sign in', body: 'Sign in with your username and password, or register first.' },
        { title: 'Fill in “About me” once', body: 'It pops up the first time you open a chat. A couple of fields is plenty — or skip it.' },
        { title: 'Ask away', body: 'Health questions go to Qingyu; anything you want done goes to PurifyManus.' },
      ],
      cta: 'Start chatting',
      ctaNote: 'You don’t have to fill anything in first — just ask.',
    },
  },

  auth: {
    shellBack: 'Back to home',

    login: {
      title: 'Sign in',
      subtitle: 'Sign in to start chatting and see your own history.',
      username: 'Username',
      usernamePlaceholder: 'Enter your username',
      password: 'Password',
      passwordPlaceholder: 'Enter your password',
      forgot: 'Forgot password?',
      needUsername: 'Please enter your username',
      needPassword: 'Please enter your password',
      failed: 'Sign-in failed, please try again later.',
      submitting: 'Signing in…',
      submit: 'Sign in',
      noAccount: 'No account yet?',
      toRegister: 'Create one',
    },

    register: {
      title: 'Create an account',
      subtitle: 'You’ll need an email address that can receive mail.',
      email: 'Email',
      code: 'Verification code',
      codePlaceholder: '6 digits',
      username: 'Username',
      usernamePlaceholder: '3–20 characters',
      password: 'Password',
      passwordPlaceholder: 'At least 8 characters',
      confirm: 'Confirm password',
      confirmPlaceholder: 'Type it again',
      mismatchHint: 'The two don’t match',
      mismatch: 'The two passwords don’t match',
      passwordTooShort: 'Password must be at least 8 characters',
      failed: 'Sign-up failed, please try again later.',
      submitting: 'Creating…',
      submit: 'Create account & sign in',
      hasAccount: 'Already have an account?',
      toLogin: 'Sign in',
    },

    forgot: {
      title: 'Reset password',
      subtitle: 'We’ll send a code to the email you registered with.',
      done: 'Your password has been changed. Sign in with the new one.',
      toLogin: 'Sign in',
      email: 'Registered email',
      code: 'Verification code',
      codePlaceholder: '6 digits',
      newPassword: 'New password',
      newPasswordPlaceholder: 'At least 8 characters',
      confirm: 'Confirm new password',
      confirmPlaceholder: 'Type it again',
      mismatchHint: 'The two don’t match',
      mismatch: 'The two passwords don’t match',
      passwordTooShort: 'Password must be at least 8 characters',
      failed: 'Reset failed, please try again later.',
      submitting: 'Submitting…',
      submit: 'Reset password',
      remember: 'Remembered it?',
    },

    code: {
      resendIn: 'Resend in {n}s',
      sending: 'Sending…',
      send: 'Get code',
      needEmail: 'Please enter your email first',
      sent: 'Code sent — check your inbox.',
      failed: 'Couldn’t send, please try again later.',
    },
  },

  chat: {
    slim: {
      title: 'Qingyu',
      welcome: 'What’s on your mind?',
      // This bubble is also teaching the user how to use the app. It used to say “tell me your
      // height, weight and goal, and I’ll lay out a plan” — which set “hand over your data first”
      // as the rule. Now it mirrors the prompt’s answer-first rule
      intro: 'I’m Qingyu. Ask me anything about food, exercise or weight — you don’t need to give me your height and weight first. I’ll answer, and if I really need your numbers I’ll ask one question at the end.',
      examples: [
        'I’m 175cm, 80kg, desk job, want to get down to 70kg — how should I plan it?',
        'Is walking 10,000 steps a day enough?',
        'What breakfast keeps you full longest when cutting fat?',
        'I keep craving sweets lately — any way to handle that?',
      ],
      placeholder: 'Tell me about your situation, or just ask…',
    },

    manus: {
      title: 'PurifyManus',
      welcome: 'Give me a task',
      // “Do first, ask later” belongs here too: the old line only said “when I need more from you,
      // I’ll stop and ask”, which reads like asking comes before work
      intro: 'I can check the weather, research things, read and write files, and work out what to do next on my own. I’ll do what I can and hand it over, then ask for the one thing I’m missing — I won’t guess.',
      examples: [
        'Is today good for a run in Hangzhou? If so, write it to a file for me',
        'How do I get from Hangzhou East Station to West Lake? Check the weather along the way too',
        'Find a few well-rated healthy food places nearby and put them in a list',
        'I want to plan this week’s workouts — ask me a few questions first',
      ],
      placeholder: 'Give me a task — the more specific the better…',
    },

    side: {
      backHome: 'Back to home',
      expand: 'Expand sidebar',
      collapse: 'Collapse sidebar',
      switchTo: 'Switch to {name}',
      switchLabel: 'Switch to',
      newChat: 'New chat',
      sessions: 'Conversations',
      noSessions: 'No conversations yet',
      rename: 'Rename',
      delete: 'Delete',
      deleteConfirm: 'Delete “{title}”? The whole conversation will be removed too.',
    },

    trace: {
      // `|` 分开的两档是 vue-i18n 的单复数：英文按 n 自动选，中文两档写一样
      steps: 'Trace · {n} step | Trace · {n} steps',
      summary: 'This turn took {n} steps (per-step detail isn’t stored — see the logs or the live run)',
      type: {
        step: 'step',
        toolCall: 'call',
        toolResult: 'result',
        loopSignal: 'self-check',
        retrieval: 'search',
      },
    },

    badge: {
      failed: 'Failed',
      stopped: 'Stopped',
      aborted: 'Halted',
      // 不写「已拦截」这种像报错的说法：用户看到的应该是顾问在关心他
      blocked: 'Turned into a safety note',
      waiting: 'Waiting for your answer',
    },

    hint: {
      waiting: 'It’s waiting on you — just reply in the same box.',
      aborted: 'The task was halted by the loop guard. Try breaking the request into smaller pieces.',
    },

    /*
     * What the model wrote into the profile this turn. Field names come from the
     * backend (ProfileField enum names); an unrecognised one just drops the item.
     */
    remember: {
      title: 'Noted:',
      view: 'View',
      more: 'and {n} more',
      item: {
        age: 'Age {value}',
        heightCm: 'Height {value}cm',
        weightKg: 'Weight {value}kg',
        goal: 'Goal {value}',
        activityLevel: 'Activity {value}',
        dietPreference: 'Diet {value}',
        avoidFood: 'Avoiding {value}',
      },
      // Enum name → copy. Used **only** for this line: the settings dropdown gets its
      // options from the backend untranslated (the backend matches on that label).
      activity: {
        SEDENTARY: 'Sedentary',
        LIGHT: 'Lightly active',
        MODERATE: 'Moderately active',
        ACTIVE: 'Very active',
        VERY_ACTIVE: 'Extremely active',
      },
    },

    composer: {
      stop: 'Stop',
      send: 'Send',
      tip: 'Enter to send, Shift + Enter for a new line',
    },

    history: {
      gone: 'That conversation no longer exists, so a new one was started for you.',
    },

    fallbackAnswer: 'Generation failed.',
  },

  settings: {
    title: 'Settings',
    avatar: {
      title: 'Avatar',
      upload: 'Change avatar',
      uploading: 'Uploading…',
      note: 'png / jpg / webp / gif, up to 2MB.',
      failed: 'Couldn’t upload the avatar, please try again later.',
    },
    appearance: {
      title: 'Appearance',
      light: 'Light',
      dark: 'Dark',
      note: 'Dark mode currently applies to the chat page only. Home, knowledge base and sign-in pages stay light.',
    },
    language: {
      title: 'Language',
      note: 'Interface text and error messages both switch over.',
    },
    // All that's left of “About me” in this drawer is an **entry point** (one-line summary plus a
    // link); the form and the read-only overview live on their own page, and their strings are in
    // the profile group below. Same arrangement the weight trend got when it moved out.
    profile: {
      empty: 'Nothing yet — fill it in once and every conversation can use it',
      link: 'View and edit',
      // The pieces of the drawer's one-line summary (“age 30 · 170cm · 71.5kg”). One key per
      // fragment rather than a single assembled sentence: Chinese and English order the value
      // and the unit differently (“30 岁” vs “age 30”), so each fragment needs its own wording.
      summary: {
        age: 'age {value}',
        height: '{value}cm',
        weight: '{value}kg',
      },
      // The weight trend moved to its own page (/weight) — that's where there's room for a
      // chart you can read numbers off and a full list of entries. The drawer keeps a one-line
      // summary plus a link; the chart's own strings live in the weight group below
      weightTitle: 'Weight trend',
      weightSummary: 'Latest {value}kg',
      weightEmpty: 'No weigh-ins yet',
      weightLink: 'View weight trend',
    },
  },

  /* ------------------------------------------------------------ about me */

  /*
   * The “About me” (user profile) page. This whole block used to live in the settings drawer
   * (under settings.profile.*); once it got its own page the copy moved here with it, leaving
   * only the keys the drawer's entry point needs.
   *
   * Shape of the page: a read-only overview on top (“what does it currently think I am?”), the
   * form underneath. The band wording (band.*) is deliberately neutral — “below / above the
   * usual range” rather than “underweight / overweight” — for the same reason the weight page
   * doesn't colour gains and losses: this page doesn't pass judgement.
   */
  profile: {
    title: 'About me',
    backHome: 'Back to home',
    updated: 'Updated {when}',
    needLogin: 'Sign in to fill this in — Qingyu uses it to tailor its advice.',
    age: 'Age',
    agePlaceholder: 'e.g. 30',
    height: 'Height (cm)',
    heightPlaceholder: 'e.g. 170',
    weight: 'Weight (kg)',
    weightPlaceholder: 'e.g. 71.5',
    bmi: 'BMI',
    goal: 'Goal',
    goalPlaceholder: 'e.g. get down to 65kg in three months',
    activity: 'Daily activity level',
    activityEmpty: 'Not set',
    diet: 'Diet preferences',
    dietPlaceholder: 'e.g. loves pasta / vegetarian',
    avoid: 'Avoid / allergies',
    avoidPlaceholder: 'e.g. seafood allergy / lactose intolerant',
    clearNote: 'Clear a field and save to delete it.',
    loadFailed: 'Couldn’t load your profile, please try again later.',
    saveFailed: 'Couldn’t save, please try again later.',
    footnote: 'This only helps Qingyu tailor what it says to you. It doesn’t diagnose anything and isn’t a substitute for a doctor.',
    overview: {
      title: 'What it knows',
      empty: 'Nothing yet. Fill it in below, or just say it in chat.',
      age: 'Age',
      ageEmpty: 'Not set',
      height: 'Height',
      heightEmpty: 'Not set',
      weight: 'Weight',
      weightEmpty: 'Not set',
      bmi: 'BMI',
      activity: 'Daily activity level',
      activityEmpty: 'Not set',
    },
    // BMI bands: the number comes from the backend, the band is decided here
    band: {
      under: 'Below the usual range',
      normal: 'Usual range',
      over: 'Above the usual range',
      obese: 'Well above the usual range',
    },
    form: {
      title: 'Edit it',
      intro: 'Fill it in once and every conversation can use it. You can also just say it in chat — both write to the same record.',
    },
    weight: {
      title: 'Weight trend',
      summary: 'Currently recorded as {value}kg',
      empty: 'No weigh-ins yet',
      link: 'See trend and entries',
    },
    /*
     * The one automatic prompt shown when entering a chat page (only while the profile is
     * still empty — see ProfilePrompt).
     *
     * The field labels are not repeated here: they are the **same keys** the About-me page
     * uses (profile.age / profile.height / …), so there is one less place to drift.
     *
     * The wording has to make "you can ignore this" obvious: nobody asked for this dialog,
     * so it must be visibly skippable — and the note explains that filling in anything stops
     * it, otherwise "Skip" reads like closing something that will just come back.
     */
    prompt: {
      title: 'Tell me a bit about you',
      intro: 'Fill in what you like and Qingyu can tailor its advice. Rather not? Just skip — or say it in chat; both write to the same record.',
      skip: 'Skip for now',
      note: 'Once you fill in anything, this stops popping up on its own.',
    },
  },

  /* -------------------------------------------------------- weight trend */

  /*
   * The weight-trend page. These strings used to live in the settings drawer
   * (settings.profile.chart.*); once the trend got its own page they moved here with it,
   * and the chart component now reads from this group too.
   *
   * Two conventions worth keeping:
   *   "vs. 7 days ago" compares against **the nearest entry outside that window**, not "the
   *   entry from exactly 7 days ago" — nobody weighs in daily, so an exact-day match is always
   *   empty. With less than 7 days of data the page says so instead of forcing a comparison
   *   the user never actually recorded.
   *   Gains and losses are **not colour-coded** — the reasoning is in WeightChart's styles
   *   (we don't want to reinforce "lighter is better").
   */
  weight: {
    title: 'Weight trend',
    backHome: 'Back to home',
    loadFailed: 'Couldn’t load your weigh-ins, please try again later.',
    record: {
      label: 'What do you weigh {date}?',
      placeholder: 'e.g. 71.5',
      hint: 'Logged as right now. The same number twice in one day is kept once; a later day is always kept — an unchanged weight is information too.',
      submit: 'Log it',
      saving: 'Logging…',
      done: 'Logged {value}kg',
      needValue: 'Enter a weight first',
      failed: 'Couldn’t log it, please try again later.',
    },
    stats: {
      current: 'Current weight',
      week: 'vs. 7 days ago',
      month: 'vs. 30 days ago',
      total: 'Total change',
      count: 'Weigh-ins',
      times: '{n}',
      bmi: 'BMI',
      // Why a cell has no number. {days} is 7 / 30; {date} is the baseline for the total
      notEnough: 'Less than {days} days of entries',
      needTwo: 'Needs at least two entries',
      since: 'since {date}',
      flat: 'no change',
      up: 'up {value}kg',
      down: 'down {value}kg',
    },
    chart: {
      title: 'Weight over time',
      empty: 'No weigh-ins yet. Enter today’s weight above and the first dot shows up here.',
      latest: 'Latest {value}kg',
      down: 'Down {value}kg from the earliest',
      up: 'Up {value}kg from the earliest',
      flat: 'Unchanged from the earliest',
      sameDay: '(several entries the same day)',
      note: 'The horizontal axis follows real dates, so days you didn’t weigh in stay blank. The vertical axis doesn’t start at zero — both ends are labelled with the actual weight.',
    },
    list: {
      title: 'All entries ({n})',
      empty: 'No entries yet.',
      when: 'When',
      value: 'Weight (kg)',
      change: 'vs. previous',
      first: 'earliest',
      same: 'no change',
      delete: 'Delete',
      deleteConfirm: 'Delete the {value}kg entry from {when}?',
      deleteFailed: 'Delete failed, please try again later.',
    },
  },

  resources: {
    title: 'Library',
    empty: 'Nothing here yet.',
    emptyHint: 'Have PurifyManus generate a PDF, download something or write a file — the output gets filed here.',
    source: 'From: {url}',
    deleteConfirm: 'Delete “{title}”? The file itself goes too.',
    loadFailed: 'Couldn’t load the library, please try again later.',
    downloadFailed: 'Download failed, please try again later.',
    deleteFailed: 'Delete failed, please try again later.',
  },

  knowledge: {
    title: 'Knowledge base',
    backHome: 'Back to home',
    overview: {
      title: 'Overview',
      chunks: 'Total chunks',
      documents: 'Documents',
      empty: 'The knowledge base is empty. Upload a few documents and the chat will start querying it.',
      loadFailed: 'Couldn’t load the overview',
    },
    upload: {
      title: 'Upload & index',
      desc: 'txt / md only. Re-uploading the same filename replaces its old chunks. Picking a whole folder imports in bulk.',
      category: 'Category',
      // Placeholder while the list is loading (and if loading it failed)
      categoryPlaceholder: 'Select…',
      // The category list comes from the backend (built-in + already used in the store);
      // only this entry is added by the UI, and picking it reveals the free-text input.
      // Keep the ellipsis: it is what tells this apart from a real category named “Other”
      categoryOther: 'Other…',
      categoryCustomLabel: 'Type name',
      categoryCustomPlaceholder: 'e.g. Skincare',
      // {name} is whatever they have typed (or “(empty)”). Same wording rules as the Chinese one
      categoryCustomHint: 'This becomes its own category, “{name}”. Retrieval matches keywords, so it only filters by this type when the question contains this name. Use letters, digits, spaces and _ - . , up to 20 characters.',
      categoryCustomEmpty: '(empty)',
      categoryRequired: 'Type a category name first, then choose the file',
      categoryLoadFailed: 'Couldn’t load the category list. You can still pick “Other…” and type one.',
      processing: 'Working…',
      pick: 'Choose files',
      pickDir: 'Import a folder',
      preview: 'Preview chunks',
      previewing: 'Previewing…',
      single: '“{source}” indexed: {count} chunks',
      batch: 'Import finished: {ok} succeeded, {failed} failed',
      failureItem: '{name}: {message}',
      failed: 'Upload failed',
    },
    preview: {
      heading: 'Preview: {source} → {count} chunks, {chars} characters',
      truncated: 'Only the first {count} chunks are listed.',
      chars: '{n} chars',
      failed: 'Preview failed',
    },
    docs: {
      title: 'Documents ({n})',
      prev: 'Previous',
      next: 'Next',
      loading: 'Loading…',
      empty: 'No documents yet.',
      source: 'Source',
      category: 'Category',
      chunks: 'Chunks',
      characters: 'Chars',
      uploadedAt: 'Indexed at',
      loadFailed: 'Couldn’t load the document list',
      deleteConfirm: 'Delete every chunk from “{source}”?',
      deleteFailed: 'Delete failed',
    },
    search: {
      title: 'Search check',
      desc: 'Run a real retrieval on one sentence and see whether it searches, what it finds, and the exact text that ends up in the prompt. Read-only — try anything.',
      placeholder: 'e.g. how many calories in a bowl of rice',
      running: 'Searching…',
      submit: 'Search',
      failed: 'Search failed',
      skipped: 'No search was run',
      skippedDetail: ' — the router decided this question has nothing to do with the knowledge base. That’s "never searched", not "searched and found nothing". To route questions like this through retrieval too, set purify.rag.router.query-all-when-unmatched to true.',
      retrieved: 'Searched',
      retrievedDetail: ': {count} hits in {elapsed}ms, category filter: {categories}',
      allCategories: 'whole library',
      rawSummary: 'Exact text that goes into the prompt',
      // Observability for the keyword arm. "Extracted search terms" is the valuable one:
      // the term-splitting is heuristic, and what it produced decides whether this arm hits anything
      keywordTerms: 'Extracted search terms: {terms}',
      keywordUnavailable: 'The keyword arm isn’t running: {reason}. This search was vector-only, so exact matches on proper nouns will be weaker.',
      rankVector: 'vector #{n}',
      rankKeyword: 'keyword #{n}',
    },

    // ------------------------------------------------------- Bailian sync
    // Moves chunks Bailian already produced into the local vector store.
    // The four states (not synced / synced / updated on Bailian / name clash) each get
    // their own sentence: what the user has to do about them is completely different.
    bailian: {
      title: 'Bailian sync',
      desc: 'Move chunks that Bailian already produced into the local vector store. Chunking stays Bailian’s, embedding happens locally, retrieval is unchanged. Re-syncing a document is safe — it replaces the local source of the same name.',
      reload: 'Reload',
      loadFailed: 'Couldn’t load the Bailian file list',
      notConfigured: 'Bailian sync isn’t configured yet; missing: {keys}. Add them to application-local.yml, then reload.',
      connected: 'Connected to: {name} · IndexId={indexId}',
      filter: 'Filter',
      filterFinish: 'Finished only',
      filterAll: 'All statuses',
      selectAll: 'Select unsynced',
      syncing: 'Syncing…',
      syncSelected: 'Sync selected ({n})',
      loading: 'Loading…',
      empty: 'Bailian has no documents matching this filter.',
      prev: 'Previous',
      next: 'Next',
      expand: 'Show chunks',
      collapse: 'Hide',
      columns: {
        name: 'File',
        status: 'Bailian status',
        size: 'Size',
        gmtModified: 'Updated on Bailian',
        local: 'Local',
      },
      state: {
        missing: 'Not synced',
        synced: 'Synced ({chunks} chunks)',
        stale: 'Updated on Bailian',
        // The only state where clicking does damage: syncing replaces the locally
        // uploaded document. It has to stand out, not blend into "not synced"
        conflict: '⚠ Same name (already local)',
      },
      chunks: {
        heading: 'Chunk preview: {name} — {count} chunks total, showing {from}-{to}',
        loading: 'Loading chunks…',
        failed: 'Couldn’t load the chunks',
        fromMetadata: 'Category from Bailian metadata: {category}',
        needPick: 'Bailian didn’t tag this document with a category. Pick one for the whole document:',
        mixed: 'This document’s chunks carry more than one category, so we can’t decide. Pick one for the whole document:',
        category: 'Category',
        // Same “Other… + type it in” flow as the upload card; see knowledgeCategory.js.
        // The ellipsis is what keeps this apart from a real category literally named “Other”
        categoryOther: 'Other…',
        categoryCustomLabel: 'Type name',
        categoryCustomPlaceholder: 'e.g. Skincare',
        categoryCustomHint: 'A new type becomes its own category. Retrieval matches keywords, so it only filters by this type when the question contains this name. Use letters, digits, spaces and _ - . , up to 20 characters.',
        empty: 'This document has no chunks on Bailian yet. Its current status is {status}; try again once it finishes parsing.',
        chars: '{n} chars',
      },
      syncOne: 'Sync just this one',
      noSelection: 'Please select at least one document to sync',
      needCategory: 'These still need a category: {names}. Expand one, pick a category, then sync.',
      // “Nothing picked” and “picked Other… but left it blank” are reported separately:
      // for the latter, hunting in the dropdown is useless — they did pick something
      needCustomName: 'These picked “Other…” but no type name yet: {names}. Expand one, fill the name in, then sync.',
      conflictConfirm: 'A local document named “{name}” already exists and was uploaded here. Syncing replaces it with Bailian’s chunks. Continue?',
      conflictConfirmBatch: 'Some selected documents share a name with an existing local source: {names}. Syncing replaces those with Bailian’s chunks. Continue?',
      timeoutHint: 'Syncing — please don’t close the page. If it times out, reload to see how many made it through; re-syncing is safe.',
      result: {
        batch: 'Sync finished: {ok} succeeded, {failed} failed',
        failureItem: '{name}: {message}',
        failed: 'Sync failed',
      },
    },
  },

  error: {
    unauthorized: 'Your session has expired, please sign in again.',
    forbidden: 'This feature is only available to super admins.',
    timeout: 'The request timed out, please try again later.',
    offline: 'Can’t reach the backend (http://localhost:8080) — check that Spring Boot is running.',
    notFound: 'That resource doesn’t exist (404).',
    http: 'Request failed (HTTP {status})',
    code: 'Request failed ({code})',
    generic: 'Request failed, please try again later.',
    streamUnsupported: 'This browser can’t read a streamed response (response.body is empty).',
  },
}
