# 基于 Redis 且无缝集成响应式编程的分布式锁、公平信号量实现

## “免责声明”

本仓库的分布式锁实现仅作练习使用，虽然相对可靠但是功能不全面，也没经过时间的拷打；
若在正式项目中有使用基于 Redis 的分布式锁 🔒 的需求， 请移步至 [Redisson](https://github.com/redisson/redisson)

```XML
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson</artifactId>
    <version>3.51.0</version> <!-- 当前最新版本 -->
</dependency>
```

## 用法

### 依赖地址

当前该依赖已经发布至 Maven 的中央仓库，可以访问：[Redis_Lock](https://central.sonatype.com/artifact/io.github.jessez332623/redis_lock)，也可以在 pom.xml 中直接配置：

### 📢 重要通知

不要使用 `1.0.0` ~ `1.0.1` 版本，它们是有问题的，具体信息见：[修复文档](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/documents/%E7%89%88%E6%9C%AC%201.0.2%20%E4%BF%AE%E5%A4%8D.md)

---

```XML
<dependency>
    <groupId>io.github.jessez332623</groupId>
    <artifactId>redis_lock</artifactId>
    <version>1.0.2</version>
</dependency>
```

### 上下文配置

本依赖推荐使用 [Lettuce Redis 客户端](https://github.com/redis/lettuce)，Maven 依赖如下：

```XML
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
    <version>6.8.1</version> <!-- 当前最新版本 -->
</dependency>
```

此外，在使用本依赖的项目上必须准备一个名为 `executeLuaScriptReactiveRedisConnectionFactory`
的 `ReactiveRedisConnectionFactory`，我在下文给出一个示例：

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
    
    /** 此处复用 reactiveRedisConnectionFactory 即可。*/
    @Bean
    public ReactiveRedisConnectionFactory
    executeLuaScriptReactiveRedisConnectionFactory(
        @Qualifier("reactiveRedisConnectionFactory")
        ReactiveRedisConnectionFactory reactiveRedisConnectionFactory)
    {
        return reactiveRedisConnectionFactory;
    }
}
```

### 属性配置
[pom.xml](pom.xml)
```properties
# 禁用本依赖（默认开启）
app.redis-lock.enabled=false

# 设置分布式锁键的键前缀为：project-lock（默认为 lock）
app.redis-lock.distributed-lock.lock-key-prefix=project-lock

# 设置分布式公平信号量键的键前缀为：project-semaphore（默认为 semaphore）
app.redis-lock.fair-semaphore.fair-semaphore-key-prefix=project-semaphore
```

## 代码速览

[Redis 分布式锁默认实现](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/src/main/java/io/github/jessez332623/redis_lock/distributed_lock/impl/DefaultRedisDistributedLockImpl.java)

[Redis 分布式锁 Lua 脚本](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/src/main/resources/lua-script/distributed-lock)

[Redis 分布式公平信号量默认实现](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/src/main/java/io/github/jessez332623/redis_lock/fair_semaphore/impl/DefaultRedisFairSemaphoreImpl.java)

[Redis 分布式公平信号量 Lua 脚本](https://github.com/JesseZ332623/Redis-Distributed-Lock/tree/main/src/main/resources/lua-script/fair-semaphore)

[Lua 脚本读取器](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/src/main/java/io/github/jessez332623/redis_lock/utils/LuaScriptReader.java)
## LICENCE

[Apache License Version 2.0](https://github.com/JesseZ332623/Redis-Distributed-Lock/blob/main/LICENSE)

## Latest Update

*2025.09.22*