[中文介绍](README_CN.md) | [文档中心](https://github.com/tencentmusic/supersonic/wiki)

![Java CI](https://github.com/tencentmusic/supersonic/workflows/supersonic%20CI/badge.svg)

# SuperSonic

SuperSonic is the next-generation BI platform that integrates **Chat BI** (powered by LLM) and **Headless BI** (powered by semantic layer) paradigms. This integration ensures that Chat BI has access to the same curated and governed semantic data models as traditional BI. Furthermore, the implementation of both paradigms benefits from the integration: 

- Chat BI's Text2SQL gets augmented with context-retrieval from semantic models.
- Headless BI's query interface gets extended with natural language API.

<img src="./docs/images/supersonic_ideas.png" height="75%" width="75%" align="center"/>

SuperSonic provides a **Chat BI interface** that empowers users to query data using natural language and visualize the results with suitable charts. To enable such experience, the only thing necessary is to build logical semantic models (definition of metric/dimension/tag, along with their meaning and relationships) through a **Headless BI interface**. Meanwhile, SuperSonic is designed to be extensible and composable, allowing custom implementations to be added and configured with Java SPI.

<img src="./docs/images/supersonic_demo.gif" height="100%" width="100%" align="center"/>

## Motivation

The emergence of Large Language Model (LLM) like ChatGPT is reshaping the way information is retrieved, leading to a new paradigm in the field of data analytics known as Chat BI. To implement Chat BI, both academia and industry are primarily focused on harnessing the power of LLMs to convert natural language into SQL, commonly referred to as Text2SQL or NL2SQL. While some approaches show promising results, their **reliability** falls short for large-scale real-world applications.

Meanwhile, another emerging paradigm called Headless BI, which focuses on constructing unified semantic data models, has garnered significant attention. Headless BI is implemented through a universal semantic layer that exposes consistent data semantics via an open API.

From our perspective, the integration of Chat BI and Headless BI has the potential to enhance the Text2SQL generation in two dimensions:

1. Incorporate data semantics (such as business terms, column values, etc.) into the prompt, enabling LLM to better understand the semantics and **reduce hallucination**.
2. Offload the generation of advanced SQL syntax (such as join, formula, etc.) from LLM to the semantic layer to **reduce complexity**. 

With these ideas in mind, we develop SuperSonic as a practical reference implementation and use it to power our real-world products. Additionally, to facilitate further development we decide to open source SuperSonic as an extensible framework.

## Out-of-the-box Features

- Built-in Chat BI interface for *business users* to enter natural language queries
- Built-in Headless BI interface for *analytics engineers* to build semantic data models
- Built-in rule-based semantic parser to improve efficiency in certain scenarios
- Support input auto-completion as well as query recommendation
- Support four-level permission control: domain-level, model-level, column-level and row-level

## Extensible Components

The high-level architecture and main process flow is as follows:

<img src="./docs/images/supersonic_components.png" height="65%" width="65%" align="center"/> 

- **Knowledge Base:** extracts schema information periodically from the semantic models and build dictionary and index to facilitate schema mapping.

- **Schema Mapper:** identifies references to schema elements(metrics/dimensions/entities/values) in user queries. It matches the query text against the knowledge base.

- **Semantic Parser:** understands user queries and generates semantic query statement. It consists of a combination of rule-based and model-based parsers, each of which deals with specific scenarios.

- **Semantic Corrector:** checks validity of semantic query statement and performs correction and optimization if needed.

- **Semantic Translator:** converts semantic query statement into SQL statement that can be executed against physical data models.

- **Chat Plugin:** extends functionality with third-party tools. The LLM is going to select the most suitable one, given all configured plugins with function description and sample questions.

## Quick Demo

SuperSonic comes with sample semantic models as well as chat conversations that can be used as a starting point. Please follow the steps: 

- Download the latest prebuilt binary from the [release page](https://github.com/tencentmusic/supersonic/releases)
- Run script "assembly/bin/supersonic-daemon.sh start" to start a standalone Java service
- Visit http://localhost:9080 in the browser to start exploration

## Build and Development

Please refer to project [wiki](https://github.com/tencentmusic/supersonic/wiki). 

## WeChat Contact

Please follow SuperSonic wechat official account:

<img src="./docs/images/supersonic_wechat_oa.png" height="50%" width="50%" align="center"/> 