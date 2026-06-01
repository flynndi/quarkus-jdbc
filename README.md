# Quarkus JDBC

[English](#readme-en) | [中文](#readme-zh-cn)

<a id="readme-en"></a>

## English

### Introduction

Quarkus JDBC is a JDBC extension for Quarkus. It provides Spring JDBC-style template APIs without depending on the Spring Framework.

The extension is built on Quarkus CDI, Agroal, and Narayana JTA. It currently provides:

- `JdbcTemplate` and `JdbcOperations`
- `NamedParameterJdbcTemplate` and `NamedParameterJdbcOperations`
- `JdbcClient`
- Java Bean mapping for query results
- Default and named datasource support
- Configuration-driven SQL initialization, default script detection, and optional cleanup scripts

The project is currently in preview. Spring XML, Spring BeanFactory, Spring local transaction managers, and deprecated LOB/XML APIs are intentionally out of scope.

### Installation

The current preview version is `0.1.0-alpha`. Applications only need the runtime artifact; Quarkus resolves the deployment artifact automatically.

Gradle:

```kotlin
dependencies {
    implementation("io.github.flynndi:quarkus-jdbc:0.1.0-alpha")
    implementation("io.quarkus:quarkus-jdbc-h2")
    implementation("io.quarkus:quarkus-narayana-jta")
    implementation("io.quarkus:quarkus-config-yaml")
}
```

Maven:

```xml
<dependencies>
    <dependency>
        <groupId>io.github.flynndi</groupId>
        <artifactId>quarkus-jdbc</artifactId>
        <version>0.1.0-alpha</version>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-jdbc-h2</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-narayana-jta</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-config-yaml</artifactId>
    </dependency>
</dependencies>
```

Replace `quarkus-jdbc-h2` with the Quarkus JDBC driver for your database.

### Quick Start

Configure the default datasource and run initialization scripts from the classpath at application startup:

```yaml
quarkus:
  datasource:
    <default>:
      db-kind: h2
      jdbc:
        url: "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1"
      username: sa
      password: sa
  jdbc:
    datasource:
      <default>:
        sql-init:
          enabled: true
          schema-locations:
            - "classpath:schema.sql"
          data-locations:
            - "classpath:data.sql"
```

Inject and use the JDBC beans directly:

```java
package com.example;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.jdbc.runtime.core.BeanPropertyRowMapper;
import io.quarkiverse.jdbc.runtime.core.JdbcTemplate;
import io.quarkiverse.jdbc.runtime.core.namedparam.NamedParameterJdbcTemplate;
import io.quarkiverse.jdbc.runtime.core.simple.JdbcClient;

@ApplicationScoped
public class BookRepository {

    @Inject
    JdbcTemplate jdbcTemplate;

    @Inject
    NamedParameterJdbcTemplate namedJdbcTemplate;

    @Inject
    JdbcClient jdbcClient;

    public List<Book> findAll() {
        return jdbcTemplate.query(
                "select id, title from book order by id",
                BeanPropertyRowMapper.newInstance(Book.class));
    }

    public Book findById(long id) {
        return namedJdbcTemplate.queryForObject(
                "select id, title from book where id = :id",
                Map.of("id", id),
                BeanPropertyRowMapper.newInstance(Book.class));
    }

    public int rename(long id, String title) {
        return jdbcClient.sql("update book set title = :title where id = :id")
                .param("id", id)
                .param("title", title)
                .update();
    }
}
```

Use the Quarkus Agroal qualifier for a named datasource:

```java
@Inject
@io.quarkus.agroal.DataSource("extra")
JdbcTemplate extraJdbcTemplate;
```

### Transactions

Transactions are provided by `quarkus-narayana-jta`. Application code uses Jakarta Transactions `@Transactional`; Spring `DataSourceTransactionManager` is not required.

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkiverse.jdbc.runtime.core.JdbcTemplate;

@ApplicationScoped
public class BookService {

    @Inject
    JdbcTemplate jdbcTemplate;

    @Transactional
    public void createBook(long id, String title) {
        jdbcTemplate.update(
                "insert into book(id, title) values (?, ?)",
                id,
                title);
    }
}
```

Agroal enlists connections in the current JTA transaction. The transaction is rolled back when the method throws a runtime exception.

### Complete YAML Example

The following configuration includes:

- A default datasource with explicit schema and data scripts
- An `extra` named datasource
- An `auto` named datasource with default and platform-specific script detection
- Optional cleanup scripts for the default datasource

```yaml
quarkus:
  datasource:
    <default>:
      db-kind: h2
      jdbc:
        url: "jdbc:h2:mem:default;DB_CLOSE_DELAY=-1"
        max-size: 4
      username: sa
      password: sa
    extra:
      db-kind: h2
      jdbc:
        url: "jdbc:h2:mem:extra;DB_CLOSE_DELAY=-1"
        max-size: 4
      username: sa
      password: sa
    auto:
      db-kind: h2
      jdbc:
        url: "jdbc:h2:mem:auto;DB_CLOSE_DELAY=-1"
        max-size: 4
      username: sa
      password: sa
  jdbc:
    datasource:
      <default>:
        sql-init:
          enabled: true
          schema-locations:
            - "classpath:schema.sql"
          data-locations:
            - "classpath:data.sql"
          ignore-failed-drops: true
          cleanup:
            enabled: true
            locations:
              - "classpath:cleanup.sql"
      extra:
        sql-init:
          enabled: true
          schema-locations:
            - "classpath:extra-schema.sql"
          data-locations:
            - "classpath:extra-data.sql"
      auto:
        sql-init:
          enabled: true
          platform: h2
```

When `schema-locations` or `data-locations` are not configured explicitly, the extension detects:

- `schema.sql`
- `schema-${platform}.sql`
- `data.sql`
- `data-${platform}.sql`

### Roadmap

- Add SQL initialization boundary tests for `mode=never`, `continue-on-error`, `ignore-failed-drops`, and cleanup
- Add a transaction rollback integration test
- Complete native image resource verification
- Add reflection metadata and native image verification for runtime reflection paths
- Continue porting Spring JDBC tests that do not depend on the Spring container

[中文](#readme-zh-cn)

---

<a id="readme-zh-cn"></a>

## 中文

### 项目简介

Quarkus JDBC 是一个面向 Quarkus 的 JDBC 扩展，提供接近 Spring JDBC 的模板式 API，同时不依赖 Spring Framework。

扩展基于 Quarkus CDI、Agroal 和 Narayana JTA，当前提供：

- `JdbcTemplate` 和 `JdbcOperations`
- `NamedParameterJdbcTemplate` 和 `NamedParameterJdbcOperations`
- `JdbcClient`
- Java Bean 查询结果映射
- 默认数据源和 named datasource 支持
- 配置驱动的 SQL 初始化、默认脚本探测和可选 cleanup 脚本

项目当前处于 preview 阶段。Spring XML、Spring BeanFactory、Spring 本地事务管理器和已废弃的 LOB/XML API 不在支持范围内。

### 安装

当前 preview 版本为 `0.1.0-alpha`。应用只需要引入 runtime artifact，Quarkus 会自动处理 deployment artifact。

Gradle：

```kotlin
dependencies {
    implementation("io.github.flynndi:quarkus-jdbc:0.1.0-alpha")
    implementation("io.quarkus:quarkus-jdbc-h2")
    implementation("io.quarkus:quarkus-narayana-jta")
    implementation("io.quarkus:quarkus-config-yaml")
}
```

Maven：

```xml
<dependencies>
    <dependency>
        <groupId>io.github.flynndi</groupId>
        <artifactId>quarkus-jdbc</artifactId>
        <version>0.1.0-alpha</version>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-jdbc-h2</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-narayana-jta</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-config-yaml</artifactId>
    </dependency>
</dependencies>
```

实际项目中请将 `quarkus-jdbc-h2` 替换为目标数据库对应的 Quarkus JDBC driver。

### 快速开始

配置默认数据源，并让扩展在应用启动时执行 classpath 下的初始化脚本：

```yaml
quarkus:
  datasource:
    <default>:
      db-kind: h2
      jdbc:
        url: "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1"
      username: sa
      password: sa
  jdbc:
    datasource:
      <default>:
        sql-init:
          enabled: true
          schema-locations:
            - "classpath:schema.sql"
          data-locations:
            - "classpath:data.sql"
```

直接注入并使用 JDBC beans：

```java
package com.example;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.jdbc.runtime.core.BeanPropertyRowMapper;
import io.quarkiverse.jdbc.runtime.core.JdbcTemplate;
import io.quarkiverse.jdbc.runtime.core.namedparam.NamedParameterJdbcTemplate;
import io.quarkiverse.jdbc.runtime.core.simple.JdbcClient;

@ApplicationScoped
public class BookRepository {

    @Inject
    JdbcTemplate jdbcTemplate;

    @Inject
    NamedParameterJdbcTemplate namedJdbcTemplate;

    @Inject
    JdbcClient jdbcClient;

    public List<Book> findAll() {
        return jdbcTemplate.query(
                "select id, title from book order by id",
                BeanPropertyRowMapper.newInstance(Book.class));
    }

    public Book findById(long id) {
        return namedJdbcTemplate.queryForObject(
                "select id, title from book where id = :id",
                Map.of("id", id),
                BeanPropertyRowMapper.newInstance(Book.class));
    }

    public int rename(long id, String title) {
        return jdbcClient.sql("update book set title = :title where id = :id")
                .param("id", id)
                .param("title", title)
                .update();
    }
}
```

Named datasource 使用 Quarkus Agroal qualifier：

```java
@Inject
@io.quarkus.agroal.DataSource("extra")
JdbcTemplate extraJdbcTemplate;
```

### 事务

事务由 `quarkus-narayana-jta` 提供。业务代码使用 Jakarta Transactions 的 `@Transactional`，不需要 Spring `DataSourceTransactionManager`。

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkiverse.jdbc.runtime.core.JdbcTemplate;

@ApplicationScoped
public class BookService {

    @Inject
    JdbcTemplate jdbcTemplate;

    @Transactional
    public void createBook(long id, String title) {
        jdbcTemplate.update(
                "insert into book(id, title) values (?, ?)",
                id,
                title);
    }
}
```

Agroal 会把连接 enlist 到当前 JTA 事务。方法抛出运行时异常时，事务会回滚。

### 完整 YAML 示例

下面的配置包含：

- 默认数据源及显式 schema/data 脚本
- `extra` named datasource
- `auto` named datasource 的默认脚本和 platform 脚本探测
- 默认数据源的可选 cleanup 脚本

```yaml
quarkus:
  datasource:
    <default>:
      db-kind: h2
      jdbc:
        url: "jdbc:h2:mem:default;DB_CLOSE_DELAY=-1"
        max-size: 4
      username: sa
      password: sa
    extra:
      db-kind: h2
      jdbc:
        url: "jdbc:h2:mem:extra;DB_CLOSE_DELAY=-1"
        max-size: 4
      username: sa
      password: sa
    auto:
      db-kind: h2
      jdbc:
        url: "jdbc:h2:mem:auto;DB_CLOSE_DELAY=-1"
        max-size: 4
      username: sa
      password: sa
  jdbc:
    datasource:
      <default>:
        sql-init:
          enabled: true
          schema-locations:
            - "classpath:schema.sql"
          data-locations:
            - "classpath:data.sql"
          ignore-failed-drops: true
          cleanup:
            enabled: true
            locations:
              - "classpath:cleanup.sql"
      extra:
        sql-init:
          enabled: true
          schema-locations:
            - "classpath:extra-schema.sql"
          data-locations:
            - "classpath:extra-data.sql"
      auto:
        sql-init:
          enabled: true
          platform: h2
```

当 `schema-locations` 或 `data-locations` 未显式配置时，扩展会探测：

- `schema.sql`
- `schema-${platform}.sql`
- `data.sql`
- `data-${platform}.sql`

### Roadmap

- 补充 SQL init 边界测试：`mode=never`、`continue-on-error`、`ignore-failed-drops` 和 cleanup
- 增加事务回滚集成测试
- 完成 native image 资源验证
- 为 runtime 反射路径补充 reflection metadata 和 native image 验证
- 继续迁移不依赖 Spring 容器的 Spring JDBC 单元测试

[English](#readme-en)
