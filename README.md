# 基于 Redis 且无缝集成响应式编程的分布式锁、公平信号量实现

## “免责声明”

本仓库的分布式锁实现仅作练习使用，虽然相对可靠但是功能不全面，也没经过时间的拷打；
若在正式项目中有使用基于 Redis 的分布式锁 🔒 的需求， 请移步至 [Redisson](https://github.com/redisson/redisson)

```XML
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson</artifactId>
    <version>3.52.0</version> <!-- 当前最新版本 -->
</dependency>
```

## 用法

### 依赖地址

当前该依赖已经发布至 Maven 的中央仓库，可以访问：[Redis_Lock](https://central.sonatype.com/artifact/io.github.jessez332623/redis_lock)，也可以在 pom.xml 中直接配置：

```XML
<dependency>
    <groupId>io.github.jessez332623</groupId>
    <artifactId>redis_lock</artifactId>
    <version>1.0.6</version>
</dependency>
```

### 📢 重要通知

不要使用 `1.0.0` ~ `1.0.5` 版本，它们是有问题或者性能不佳的，具体信息见：

- [修复文档-1.0.2 分布式公平信号量键在 Redis 集群中的适配](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/documents/%E7%89%88%E6%9C%AC%201.0.2%20%E4%BF%AE%E5%A4%8D.md)
- [修复文档-1.0.5 模块化迁移，以及更简易、更安全的上下文配置](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/documents/%E7%89%88%E6%9C%AC%201.0.5%20%E4%BF%AE%E5%A4%8D.md)
- [更新文档-1.0.6 Lua 脚本缓存策略，以及为所有锁操作配置统一的调度器]()
---

### 上下文配置

本依赖推荐使用 [Lettuce Redis 客户端](https://github.com/redis/lettuce)，Maven 依赖如下：

```XML
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
    <version>6.8.1</version> <!-- 当前最新版本 -->
</dependency>
```

此外，在使用本依赖的项目上必须准备一个 `ReactiveRedisConnectionFactory`，
我在下文给出一个示例：

```java
/** 项目 Redis 配置类。*/
@Configuration
public class ReactiveRedisConfig
{
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    /** Redis 响应式连接工厂配置类。 */
    @Bean
    @Primary
    public ReactiveRedisConnectionFactory
    reactiveRedisConnectionFactory()
    {
        // 1. 创建独立 Redis 配置
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);       // Redis 地址
        config.setPort(redisPort);           // Redis 端口

        // 密码
        config.setPassword(RedisPassword.of(redisPassword));

        // 2. 创建客户端配置
        LettuceClientConfiguration clientConfig
            = LettuceClientConfiguration.builder()
            .clientOptions(
                ClientOptions.builder()
                    .autoReconnect(true)
                    .socketOptions(
                        SocketOptions.builder()
                            .connectTimeout(Duration.ofSeconds(5L)) // 连接超时
                            .keepAlive(true) // 自动管理 TCP 连接存活
                            .build()
                    )
                    .timeoutOptions(
                        TimeoutOptions.builder()
                            .fixedTimeout(Duration.ofSeconds(15L)) // 操作超时
                            .build()
                    ).build()
            )
            .commandTimeout(Duration.ofSeconds(15L))  // 命令超时时间
            .shutdownTimeout(Duration.ofSeconds(5L))  // 关闭超时时间
            .build();

        // 3. 创建连接工厂
        return new LettuceConnectionFactory(config, clientConfig);
    }
}
```

### 属性配置

```yml
app:
  redis-lock:
    # 是否禁用本依赖（默认开启）
    enable: true

    schedulers:
      # 最大线程数（默认 100 线程）
      max-threads: 100
      # 任务队列容量（默认 1000）
      task-queue-capacity: 1000
      # 调度线程名（默认 redis-lock）
      thread-name: redis-lock
      # 空闲线程存活时间（默认 60 秒）
      ttl-seconds: 60
      # 是否开启守护线程？（对于分布式锁操作来说是必须的）
      daemon: true
    
    distributed-lock:
      # 设置分布式锁键的键前缀为：project-lock（默认为 lock）
      key-prefix: project-lock
      
    fair-semaphore:
      # 设置分布式公平信号量键的键前缀为：project-semaphore（默认为 semaphore）
      key-prefix: project-semaphore
```

## 代码速览

- [Redis 分布式锁默认实现](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/src/main/java/io/github/jessez332623/redis_lock/distributed_lock/impl/DefaultRedisDistributedLockImpl.java)

- [Redis 分布式锁 Lua 脚本](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/src/main/resources/lua-script/distributed-lock)

- [Redis 分布式公平信号量默认实现](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/src/main/java/io/github/jessez332623/redis_lock/fair_semaphore/impl/DefaultRedisFairSemaphoreImpl.java)

- [Redis 分布式公平信号量 Lua 脚本](https://github.com/JesseZ332623/Redis-Distributed-Lock/tree/main/src/main/resources/lua-script/fair-semaphore)

- [Lua 脚本读取器](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/src/main/java/io/github/jessez332623/redis_lock/utils/LuaScriptReader.java)

## LICENCE

[Apache License Version 2.0](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/LICENSE)

## Latest Update

*2025.11.10*