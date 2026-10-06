# Purify AI

健康瘦身方向的 AI 助手平台。**轻语**负责一问一答的健康咨询，**PurifyManus** 是能自己调工具把活干完的智能体——两者共用同一份用户画像和知识库。

> Java 21 · Spring Boot 3.5 · Spring AI 1.0 · Vue 3 · MySQL · PostgreSQL/pgvector · 通义千问（DashScope）

---

## 目录

- [它能做什么](#它能做什么)
- [技术栈](#技术栈)
- [架构](#架构)
- [快速开始](#快速开始)
- [配置](#配置)
- [目录结构](#目录结构)
- [测试](#测试)
- [密钥与安全](#密钥与安全)

---

## 它能做什么

| 入口 | 路径 | 说明 |
| --- | --- | --- |
| 首页 | `/` | 公开，未登录也能看 |
| 轻语 | `/slim` | 健康顾问，一问一答，答前先查知识库 |
| PurifyManus | `/manus` | 自主智能体：查天气、搜资料、读写文件、生成 PDF |
| 我的情况 | `/profile` | 用户画像（身高体重、目标、忌口），模型和表单写的是同一份数据 |
| 体重变化 | `/weight` | 体重曲线与较 7 天 / 30 天的变化 |
| 知识库 | `/knowledge` | 上传资料、看切片、检索自检、百炼同步（**仅超级管理员**） |
| 使用说明 | `/guide` | 公开，介绍平台定位与使用方式 |

另外有两个全局抽屉：**设置**（头像、外观、语言）和**资料库**（智能体产出的文件归档）。
界面中英双语，对话页支持深色主题。

## 技术栈

**后端**：Java 21、Spring Boot 3.5、Spring AI 1.0、Spring AI Alibaba（DashScope）、Maven

**模型**（全部走阿里云百炼）：`qwen-plus` 对话 · `qwen-vl-max` 看图 · `text-embedding-v4` 向量化 · `gte-rerank-v2` 重排

**存储**：MySQL（对话记忆、聊天记录、会话、用户、画像、资料库）、PostgreSQL + pgvector（知识库向量）

**前端**：Vue 3 + Vite 6 + vue-router + vue-i18n + axios，Markdown 渲染用 markdown-it + DOMPurify + highlight.js

**集成**：MCP（Model Context Protocol，stdio，接高德地图）、阿里云 OSS、iText（PDF）、jsoup（抓网页）

## 架构

```
浏览器（Vue 3 + Vite）
  │  /api/**  ·  SSE 流式
  ▼
Spring Boot 3.5
  │
  ├── 轻语 SlimApp ──── ChatClient + Advisor 链
  │                       记忆 → 敏感词 → 知识库检索 → Re-Reading → 日志
  │                       工具由框架内置循环执行（应用层看不到中间过程）
  │
  └── PurifyManus ───── 自驱动 ReAct 循环
                          BaseAgent → ToolCallAgent → PurifyManus
                          + LoopGuard      循环看门狗（判据 / 处置双接口）
                          + AskHuman       暂停等用户回答，再带上下文继续
                          + Agent Trace    每一步都可观测
  │
  ├── RAG ── 向量 + 关键词双路召回 → RRF 融合 → 重排精排
  │          检索路由先判断该不该查、查哪一类（纯字符串匹配，不调模型）
  │
  └── 工具 ── 本地工具（搜索 / 抓网页 / 读写文件 / 生成 PDF / 用户画像）
              + MCP 工具（高德地图：天气、地理编码、路线规划、周边搜索）
  ▼
MySQL（业务数据）  ·  PostgreSQL + pgvector（知识库）
```

两条链路是**独立**的：轻语走 `ChatClient`，工具循环由框架内部消化；PurifyManus 自己驱动 think→act，因此中间过程可观测、也能在模型原地打转时介入。代价是 Advisor 链上的检索和敏感词拦截在智能体这条路上不会自动发生，所以那两件事被显式补在了它的开场逻辑里。

**部署形态**：开发时前后端分离（Vite `5173` 代理 `/api` 和 `/files` 到 `8080`）；生产时前端构建产物直接落到 `src/main/resources/static/`，由 Spring Boot 同源托管——只有一个 `8080` 端口，不存在跨域。

## 快速开始

### 环境要求

- **JDK 21**
- **Node.js 22+** —— 这个下限是 `vue-i18n` 定的（它声明 `engines.node >= 22`），Vite 那边只要求 18+
- **MySQL** —— 业务数据
- **PostgreSQL** 并安装 [pgvector](https://github.com/pgvector/pgvector) 扩展（使用本地向量库链路时；HNSW 索引需要 pgvector ≥ 0.5.0，低版本改用 `ivfflat` 或 `none`）
- **通义千问 API Key**：[阿里云百炼](https://bailian.console.aliyun.com/) 控制台申请

### 1. 准备数据库

**MySQL**：业务表由应用启动时自动创建，无需手工执行脚本。连接串里带 `createDatabaseIfNotExist=true` 即可自动建库。

**PostgreSQL**：需要先装 pgvector 扩展。建表脚本在 `src/main/resources/db/pgvector-schema-postgresql.sql`。
默认 `purify.rag.pgvector.initialize-schema: true` 会让应用自己建扩展和表；若数据库账号没有建扩展权限，改成 `false` 并手工执行该脚本。

### 2. 配置密钥

所有真实密钥放在 **`src/main/resources/application-local.yml`**（已被 `.gitignore` 挡住，不会进仓库）。新建该文件并至少填上下面这几项：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/purify_ai_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: your-username
    password: your-password
  ai:
    dashscope:
      api-key: sk-xxxxxxxxxxxxxxxx

purify:
  auth:
    jwt:
      # 至少 32 个字符。生成一个：openssl rand -base64 48
      secret: "把生成结果粘在这里"
  rag:
    pgvector:
      jdbc-url: jdbc:postgresql://localhost:5432/purify_vector
      username: your-username
      password: your-password
```

其余可选项见[配置](#配置)一节。

### 3. 启动后端

```bash
./mvnw spring-boot:run
# Windows
mvnw.cmd spring-boot:run
```

> **如果报 `The JAVA_HOME environment variable is not defined correctly`**：`mvnw` 认的是 `JAVA_HOME`，不是 `PATH` 里的 `java`。确认它指向一个**真实存在**的 JDK 21 目录（升级过 JDK 之后指向旧版本目录是常见情况，那个目录已经不在了，报错却只说「未正确定义」）。
>
> ```powershell
> $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'   # 换成你机器上的实际路径
> ```

服务在 <http://localhost:8080>。启动日志里会打出**实际装上了哪些工具、知识库有没有接入、哪些依赖因为没配被跳过**——排查「某个功能没反应」时先看这几行。

首次启动会创建超级管理员 `root_agent`，口令来自 `purify.auth.root-agent.password`（每次启动按该值重置，见 `RootAgentInitializer`）。

### 4. 启动前端

```bash
cd purify-ai-agent-fronted
npm install
npm run dev
```

开发地址 <http://localhost:5173>。生产构建：

```bash
npm run build
```

产物直接写入 `../src/main/resources/static/`，之后只跑 Spring Boot 就能访问完整应用（<http://localhost:8080>）。

## 配置

配置分两层：`application.yml` 放**该填什么**（不含密钥，进仓库），`application-local.yml` 放**真实值**（被 gitignore）。

### 必填

| 配置项 | 不填的后果 |
| --- | --- |
| `spring.datasource.*` | MySQL 连不上，启动失败 |
| `spring.ai.dashscope.api-key` | 所有模型调用失败 |
| `purify.auth.jwt.secret` | **启动直接失败**——这是有意的，留空时若自动生成随机密钥，重启会让所有人被登出，而且「没配」这件事会被完全藏起来 |
| `purify.rag.pgvector.*` | `store=pgvector` 时启动失败（连接池初始化） |

### 可选（不配就少一个功能，不影响启动）

整个项目贯彻一条原则：**宁可少一个工具，也不要起不来**。少一个工具时模型会自己用别的方式回答，而启动失败是整个服务不可用。

| 配置项 | 对应的功能 |
| --- | --- |
| `aliyun.oss.access-key-id` / `-secret` | 生成 PDF 并回下载链接、**上传用户头像**（两者共用同一个 OSS 客户端）。不配的话 PDF 工具不注册、头像也传不了 |
| `searchapi.api-key` | 联网搜索 |
| `spring.mail.*` + `purify.auth.mail.enabled=true` | 真实发送验证码邮件；否则只在日志里打印验证码，注册流程照样走得通 |
| `purify.rag.bailian.access-key-id` / `-secret` | 知识库页的「百炼同步」卡片 |
| MCP 的 `AMAP_MAPS_API_KEY` | 高德地图那组工具；连不上只丢 MCP 那部分 |
| `purify.rag.enabled` | 检索服务出问题时的应急开关，关掉后退化成纯模型对话（`store=pgvector` 时也是数据库连不上导致启动失败的逃生口） |

### 常用可调项

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `purify.rag.store` | `pgvector` | 检索链路：`pgvector`（本地向量库）或 `bailian`（云知识库）。两条链路产出同一个 Advisor 接口，切换不动上层代码 |
| `purify.manus.max-steps` | `12` | 智能体一次任务最多走几步 |
| `purify.manus.loop.*` | 见 yml | 循环检测阈值：重复 / 横跳 / 雷同各自的判定次数，以及「问用户」和「中止」的升级点 |
| `purify.rag.router.query-all-when-unmatched` | `false` | 没命中任何分类时：`false` 压根不查（默认，最省资源），`true` 不带过滤查全库 |
| `purify.rag.pgvector.rerank-top-n` | `5` | 最终拼进 Prompt 的切片条数，最值得调的参数：少了答不全，多了被噪声带偏 |

## 目录结构

```
purify-ai-agent/
├── src/main/java/com/purify/purifyaiagent/
│   ├── app/           轻语：ChatClient + Advisor 链
│   ├── agent/         PurifyManus：BaseAgent → ToolCallAgent → PurifyManus
│   │   ├── loop/      循环看门狗（检测器 / 处置器双接口）
│   │   └── tool/      人机中断（AskHuman）、工具上下文
│   ├── advisor/       记忆、敏感词、Re-Reading、日志
│   ├── rag/           检索路由、混合召回、RRF 融合
│   │   ├── pgvector/  本地向量库链路（含 pg_bigm 关键词一路）
│   │   └── bailian/   百炼云知识库链路与管控面同步
│   ├── auth/          JWT、拦截器、@RequireLogin / @RequireAdmin、用户仓储
│   ├── tools/         本地工具 + 工具注册表（合并 MCP 工具）
│   ├── profile/       用户画像读写规则（工具和表单共用）
│   ├── chat/          会话与聊天记录仓储
│   ├── resource/      资料库（智能体产出归档）
│   ├── controller/    HTTP 接口
│   ├── config/        各组件的装配与开关
│   └── model/         对外数据结构
├── src/main/resources/
│   ├── application.yml          配置模板（不含密钥）
│   ├── application-local.yml    真实密钥（gitignore，需自己创建）
│   ├── db/*.sql                 建表脚本
│   ├── prompts/*.st             四份提示词模板（中英各一份，共 8 个文件），改人设不用动 Java
│   └── mcp-servers.json         MCP server 清单
└── purify-ai-agent-fronted/     前端（Vue 3 + Vite）
    ├── src/views/               页面
    ├── src/components/          组件（ChatRoom、各类抽屉）
    ├── src/i18n/                中英语言包
    └── scripts/                 文案检查脚本
```

### HTTP 接口一览

| 前缀 | 说明 |
| --- | --- |
| `/api/auth/**` | 注册、登录、找回密码、邮箱验证码、头像、当前用户 |
| `/api/slim/**` | 轻语对话（`/chat` SSE 流式、`/image` 看图、`/history`） |
| `/api/manus/**` | PurifyManus 对话（`/chat` SSE 流式、`/history`） |
| `/api/sessions/**` | 会话列表、重命名、删除 |
| `/api/profile/**` | 用户画像读写、记体重、删体重流水 |
| `/api/resources/**` | 资料库列表、下载、删除 |
| `/api/knowledge/**` | 知识库上传、预览、文档列表、分类、统计（**需管理员**） |
| `/api/knowledge/bailian/**` | 百炼同步：状态、文件清单、切片、同步 |
| `/api/rag/search` | 检索自检：拿一句话真跑一次检索，看查出什么 |

## 测试

```bash
./mvnw test
```

当前有 **4 个测试类、共 18 个用例**，都是**不需要外部服务**的纯单元测试：中英语言包键位一致性、提示词模板中英一致、pgvector 的 SQL 元数据转义与分类发现。

`pom.xml` 里配了 `excludedGroups=integration`，如果以后加入带 `@Tag("integration")` 的测试（要真调模型、连远程数据库），用下面这条单独跑：

```bash
./mvnw test -Dgroups=integration
```

前端有两个文案检查脚本，改完文案建议跑一遍：

```bash
cd purify-ai-agent-fronted
npm run check:i18n   # 中英语言包键位是否一一对应、代码里用到的键是否存在
npm run check:copy   # 首页文案里有没有混进框架名、端口号这类开发者才懂的词
```

## 密钥与安全

**当前仓库里有两处明文凭据，上线前必须处理：**

1. **`src/main/resources/mcp-servers.json` 里的 `AMAP_MAPS_API_KEY`** —— 这个文件是入库的，`.gitignore` 的 `*.local.yml` 规则挡不住它。**该 Key 已经进过仓库，建议直接去高德控制台重新生成一个**，然后把配置改成从 `application-local.yml` 读：

   ```yaml
   spring:
     ai:
       mcp:
         client:
           stdio:
             connections:
               amap-maps:
                 command: cmd
                 args: ["/c", "npx", "-y", "@amap/amap-maps-mcp-server"]
                 env:
                   AMAP_MAPS_API_KEY: 你的新 Key
   ```

   同名时 `connections` 会整个覆盖 `mcp-servers.json` 里的那条，所以两处不能各写一半。

2. **`application.yml` 里的 `purify.auth.root-agent.password`** —— 目前是明文引导口令。上线前挪进 `application-local.yml`。

**其余原则**：任何存密钥的文件都按 `*.local.yml` / `.env*` 命名，靠 gitignore 的模式统一挡住，避免以后新建 `application-prod.yml` 之类又忘了加。

**凭据一旦进过仓库就不会因为删除而消失**——它还在 git 历史里。所以正确做法是**轮换**（去对应控制台重新生成），而不是只改文件。
