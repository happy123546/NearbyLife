package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClient (){
        Config config = new Config();
        //配置
        config.useSingleServer().setAddress("redis://192.168.126.129:6379").setPassword("root");
        //创建客户端
        return Redisson.create(config);

    }
}
