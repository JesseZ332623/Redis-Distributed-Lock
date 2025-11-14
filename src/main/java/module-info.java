// 基于 Redis 且无缝集成响应式编程的分布式锁、公平信号量模块声明
module redis_lock
{
    // Spring 相关依赖
    requires spring.core;
    requires spring.boot;
    requires spring.context;
    requires spring.boot.autoconfigure;
    requires spring.beans;
    requires spring.tx;

    requires spring.data.redis;
    requires spring.data.commons;

    // Jackson 相关依赖
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;

    // Reactor 响应式编程
    requires transitive reactor.core;
    requires org.reactivestreams;

    // Lombok（编译时依赖）
    requires static lombok;
    requires static org.jetbrains.annotations;

    // 日志
    requires transitive org.slf4j;

    // 导出公共 API 包
    exports io.github.jessez332623.redis_lock.autoconfigure;
    exports io.github.jessez332623.redis_lock.distributed_lock;
    exports io.github.jessez332623.redis_lock.fair_semaphore;

    // 开放包给 Spring 反射
    opens io.github.jessez332623.redis_lock.autoconfigure
        to spring.core, spring.context;
    opens io.github.jessez332623.redis_lock.distributed_lock.impl
        to spring.core, spring.beans, spring.context;
    opens io.github.jessez332623.redis_lock.fair_semaphore.impl
        to spring.core, spring.beans, spring.context;
}