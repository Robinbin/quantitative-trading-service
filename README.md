# Quantitative Trading Service

基于 **Spring Boot 4 + Spring AI** 构建的量化交易服务，将 GPT-4o 智能分析与量化策略引擎深度融合，提供从市场数据采集、技术指标计算、多策略评估、风险管理到自动化模拟交易的完整闭环。

---

## 目录

1. [项目概述](#项目概述)
2. [技术栈](#技术栈)
3. [目录结构](#目录结构)
4. [核心模块介绍](#核心模块介绍)
5. [启动与部署](#启动与部署)

---

## 项目概述

| 特性 | 说明 |
|------|------|
| **实时行情** | 通过 Yahoo Finance v8/v10 API 获取 Tick、OHLCV 及基本面数据，Caffeine 多级缓存 |
| **技术指标** | ta4j 计算 MA5/10/20/60、EMA12/26、MACD、RSI(14)、布林带(20,2σ) |
| **量化策略** | 5 种内置策略（均线交叉、MACD、RSI、布林带、多指标综合），可扩展 |
| **风险管理** | 年化波动率、参数化 VaR(95%)、最大回撤、Sharpe 比率，支持单仓与组合风险评估 |
| **AI 分析** | GPT-4o 读取指标 + 策略信号 + 风险指标，输出结构化分析报告（BUY/HOLD/SELL + 置信度） |
| **模拟交易** | 内存级模拟撮合（MARKET/LIMIT 订单），策略共识自动下单，持仓与 P/L 实时追踪 |
| **API 文档** | Swagger UI 自动生成，覆盖全部接口 |

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 主要开发语言 |
| Spring Boot | 4.1.0 | 应用框架 |
| Spring AI | 2.0.0 | ChatClient 接入 OpenAI GPT-4o |
| Spring WebMVC | — | REST API |
| WebClient | — | 非阻塞 HTTP 调用 Yahoo Finance |
| Caffeine | 3.1.8 | 多级内存缓存（TTL 15s / 1min / 1h） |
| ta4j | 0.16 | 技术指标计算库 |
| springdoc-openapi | 2.8.6 | Swagger UI / OpenAPI 3.0 |
| JUnit 5 + Mockito + AssertJ | — | 单元测试与切片测试 |
| JaCoCo | 0.8.12 | 代码覆盖率门禁（最低 80%） |
| Gradle | 8.14 | 构建工具 |

---

## 目录结构

```
quantitative-trading-service/
├── src/
│   ├── main/
│   │   ├── java/com/trading/
│   │   │   ├── QuantitativeTradingServiceApplication.java   # 启动类
│   │   │   ├── marketdata/           # 市场数据模块
│   │   │   │   ├── cache/            # Caffeine 缓存门面
│   │   │   │   ├── config/           # 配置属性 & Caffeine & OpenAPI
│   │   │   │   ├── controller/       # REST /api/v1/market-data
│   │   │   │   ├── domain/           # TickQuote / OhlcvBar / TechnicalIndicators / FundamentalData
│   │   │   │   ├── indicator/        # TechnicalIndicatorCalculator (ta4j)
│   │   │   │   ├── provider/         # MarketDataProvider 接口 + Yahoo Finance 实现
│   │   │   │   └── scheduler/        # 定时缓存预热
│   │   │   ├── strategy/             # 量化策略模块
│   │   │   │   ├── controller/       # REST /api/v1/strategy
│   │   │   │   ├── domain/           # Signal / StrategyResult / StrategyContext
│   │   │   │   ├── engine/           # Strategy 接口 + StrategyEngineImpl
│   │   │   │   ├── impl/             # 5 种策略实现
│   │   │   │   └── service/          # StrategyService + Impl
│   │   │   ├── risk/                 # 风险管理模块
│   │   │   │   ├── calculator/       # RiskCalculator（波动率/VaR/回撤/Sharpe）
│   │   │   │   ├── config/           # RiskProperties + RiskConfig
│   │   │   │   ├── controller/       # REST /api/v1/risk
│   │   │   │   ├── domain/           # RiskLevel / RiskMetrics / PositionRisk / PortfolioRisk
│   │   │   │   └── service/          # RiskService + Impl
│   │   │   ├── aianalysis/           # AI 分析模块
│   │   │   │   ├── config/           # AIAnalysisConfig（ChatClient Bean）
│   │   │   │   ├── controller/       # REST /api/v1/ai
│   │   │   │   ├── domain/           # AnalysisResult
│   │   │   │   └── service/          # AIAnalysisService + Impl
│   │   │   └── executor/             # 模拟交易模块
│   │   │       ├── config/           # TradingExecutorProperties + Config
│   │   │       ├── controller/       # REST /api/v1/trading
│   │   │       ├── domain/           # Order / Position / TradingPortfolio / enums
│   │   │       └── service/          # TradingExecutorService + Impl
│   │   └── resources/
│   │       └── application.yml       # 全局配置
│   └── test/
│       └── java/com/trading/         # 镜像 main 结构，单元测试 + 切片测试
├── build.gradle
├── settings.gradle
└── CLAUDE.md                         # 项目开发指南
```

---

## 核心模块介绍

### 1. marketdata — 市场数据

从 Yahoo Finance 免费接口拉取数据，缓存后对外暴露 REST API。

| 接口 | 说明 |
|------|------|
| `GET /api/v1/market-data/{symbol}/tick` | 实时报价（TTL 15s） |
| `GET /api/v1/market-data/{symbol}/ohlcv` | K 线数据（TTL 1min） |
| `GET /api/v1/market-data/{symbol}/indicators` | 技术指标（MA/EMA/MACD/RSI/布林带） |
| `GET /api/v1/market-data/{symbol}/fundamental` | 基本面数据（TTL 1h） |
| `POST /api/v1/market-data/{symbol}/refresh` | 强制刷新缓存 |
| `POST /api/v1/market-data/batch/tick` | 批量获取多 symbol 报价 |

**定时任务**：Tick 每 30s 刷新，OHLCV+指标每 5min，基本面每个工作日 08:00。

---

### 2. strategy — 量化策略引擎

内置 5 种策略，均实现 `Strategy` 接口，可通过 Spring 依赖注入扩展。

| 策略 | 信号逻辑 |
|------|---------|
| `MovingAverageCross` | MA5 上穿 MA20 → BUY；下穿 → SELL |
| `MACD` | MACD 柱 > 0 且上升 → BUY；< 0 且下降 → SELL |
| `RSI` | RSI < 30 → BUY；RSI > 70 → SELL |
| `BollingerBand` | 价格触下轨 → BUY；触上轨 → SELL |
| `MultiIndicator` | 以上 4 个信号加权综合评分 |

| 接口 | 说明 |
|------|------|
| `GET /api/v1/strategy` | 列出所有策略 |
| `GET /api/v1/strategy/{symbol}/evaluate` | 单策略评估 |
| `GET /api/v1/strategy/{symbol}/evaluate-all` | 全策略评估 |
| `GET /api/v1/strategy/scan` | 对监控列表所有标的扫描 |

---

### 3. risk — 风险管理

| 指标 | 计算方式 |
|------|---------|
| 年化波动率 | 日对数收益率标准差 × √252 |
| VaR(95%) | price × (σ/√252) × 1.6449，参数化单日 |
| 最大回撤 | 历史价格序列峰谷法 |
| Sharpe 比率 | (收益率 − 无风险利率) / 波动率 |
| 组合 VaR | √(ΣVaRᵢ²)（保守估计） |
| 集中度 | HHI 指数（赫芬达尔-赫希曼指数） |

风险等级：`LOW` / `MEDIUM` / `HIGH` / `CRITICAL`

| 接口 | 说明 |
|------|------|
| `GET /api/v1/risk/{symbol}/metrics` | 单标的风险指标 |
| `POST /api/v1/risk/position` | 单仓位风险（止盈止损价、VaR） |
| `POST /api/v1/risk/portfolio` | 组合风险（多仓聚合） |
| `GET /api/v1/risk/scan` | 监控列表全量扫描 |

---

### 4. aianalysis — AI 智能分析

调用 GPT-4o，将技术指标、策略信号、风险数据整合成结构化提示词，返回分析报告。

```json
{
  "symbol": "AAPL",
  "interval": "D1",
  "summary": "苹果公司当前处于...",
  "recommendation": "BUY",
  "confidence": 0.78,
  "keyFactors": ["RSI 超卖反弹", "MACD 柱转正", "布林带下轨支撑"],
  "generatedAt": "2026-07-31T10:00:00Z"
}
```

解析失败时自动回退为 `HOLD / confidence=0.50`，不抛出异常。

| 接口 | 说明 |
|------|------|
| `GET /api/v1/ai/{symbol}/analyze` | 单标的 AI 分析 |
| `GET /api/v1/ai/scan` | 监控列表全量 AI 分析（并行） |

---

### 5. executor — 模拟交易执行引擎

内存级纸面交易，所有状态重启后重置。

**订单类型**：`MARKET`（立即以当前价成交）、`LIMIT`（挂单，条件满足时成交）

**自动执行逻辑**：
- 同一标的 ≥ 3 个策略发出同向信号 且 平均置信度 ≥ 60%
- 买入：风险等级非 `CRITICAL` 且当前无持仓
- 卖出：已有持仓时全量平仓

| 接口 | 说明 |
|------|------|
| `POST /api/v1/trading/orders` | 提交订单 |
| `GET /api/v1/trading/orders` | 查询所有订单 |
| `GET /api/v1/trading/orders/{id}` | 查询单笔订单 |
| `DELETE /api/v1/trading/orders/{id}` | 撤销挂单 |
| `GET /api/v1/trading/positions` | 持仓列表（含实时市价） |
| `GET /api/v1/trading/portfolio` | 组合快照（现金 + 持仓 + 总 P/L） |
| `POST /api/v1/trading/auto-execute` | 策略驱动自动下单 |

---

## 启动与部署

### 前置条件

| 依赖 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 21+ | [下载](https://adoptium.net/) |
| OpenAI API Key | — | AI 分析模块必须；其余模块不依赖 |

### 本地开发启动

```bash
# 1. 克隆项目
git clone https://github.com/Robinbin/quantitative-trading-service.git
cd quantitative-trading-service

# 2. 构建（含测试 & 覆盖率验证）
./gradlew build

# 3. 启动服务
OPENAI_API_KEY=your-openai-api-key ./gradlew bootRun
```

服务启动后默认监听 **8080** 端口：

| 地址 | 说明 |
|------|------|
| http://localhost:8080/swagger-ui.html | Swagger UI 交互文档 |
| http://localhost:8080/v3/api-docs | OpenAPI JSON 规范 |
| http://localhost:8080/actuator/health | 健康检查 |

### 常用 Gradle 命令

```bash
# 仅运行测试
./gradlew test

# 生成 JaCoCo 覆盖率报告（build/reports/jacoco/）
./gradlew jacocoTestReport

# 验证覆盖率门禁（最低 80%）
./gradlew jacocoTestCoverageVerification

# 运行单个测试类
./gradlew test --tests "com.trading.executor.service.TradingExecutorServiceImplTest"

# 跳过测试直接打包
./gradlew bootJar -x test
```

### 配置说明

主要配置项位于 `src/main/resources/application.yml`：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}   # 通过环境变量注入
      chat.options.model: gpt-4o

trading:
  market-data:
    watch-list: [AAPL, TSLA, MSFT, GOOGL, AMZN]  # 监控标的列表
  executor:
    initial-cash: 100000.0          # 模拟账户初始资金（USD）
    max-order-value: 10000.0        # 单笔最大下单金额（USD）
    min-strategies-agree: 3         # 自动下单所需最少策略共识数
    min-confidence: 0.60            # 自动下单所需最低平均置信度
  risk:
    stop-loss-pct: 0.05             # 止损比例 5%
    take-profit-pct: 0.15           # 止盈比例 15%
    risk-free-rate: 0.05            # 无风险利率（Sharpe 计算用）
```

### Docker 部署（可选）

```bash
# 构建 JAR
./gradlew bootJar -x test

# 构建镜像
docker build -t quantitative-trading-service .

# 运行容器
docker run -d \
  -p 8080:8080 \
  -e OPENAI_API_KEY=your-key \
  quantitative-trading-service
```

> **注意**：市场数据来源为 Yahoo Finance 免费接口，无需 API Key，但存在请求频率限制。生产环境建议替换为付费数据源并实现 `MarketDataProvider` 接口。

---

## 许可证

本项目仅供学习与研究使用，不构成任何投资建议。
