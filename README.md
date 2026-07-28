# Quantitative Trading Service

## 项目简介

本项目是基于 **Spring AI** 构建的量化交易服务，旨在将人工智能能力与量化交易策略深度融合，实现智能化的金融市场分析与自动化交易决策。

## 主要目标

- **AI 驱动的策略分析**：利用 Spring AI 集成大语言模型（LLM），对市场数据、新闻资讯及技术指标进行智能解读，辅助生成和优化交易策略。
- **量化策略引擎**：支持多种量化交易策略的开发、回测与执行，包括趋势跟踪、均值回归、套利等策略模型。
- **实时市场数据处理**：接入多源市场数据，进行实时行情监控、技术指标计算与信号生成。
- **风险管理**：内置仓位管理、止盈止损、最大回撤控制等风险控制机制。
- **自动化交易执行**：对接交易所或券商 API，实现订单的自动化生成与执行。

## 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | 应用框架 |
| Spring AI | AI 集成框架，连接 LLM 进行智能分析 |
| Java | 主要开发语言 |

## 核心模块（规划中）

- `market-data` — 市场数据采集与处理
- `strategy` — 量化策略定义与回测
- `ai-analysis` — Spring AI 驱动的智能分析
- `risk-management` — 风险控制模块
- `trading-executor` — 交易执行模块

## 快速开始

```bash
# 克隆项目
git clone <repository-url>
cd quantitative-trading-service

# 构建项目
./mvnw clean install

# 启动服务
./mvnw spring-boot:run
```

## 许可证

本项目仅供学习与研究使用。
