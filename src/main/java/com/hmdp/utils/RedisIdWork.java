package com.hmdp.utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;


@Component
public class RedisIdWork {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //Timestamp时间戳
    public static final Long BEGIN_TIMESTAMP = 1784764800L;

    public Long nextId(String keyPrefix){
        //生成时间戳
        long now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timeTamp = now - BEGIN_TIMESTAMP;

        //生成序列号
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //拼接
        return timeTamp << 32 | count;
    }

    public static void main(String[] args) {
        long epochSecond = LocalDateTime.of(2026, 7, 23, 0, 0, 0)
                .toEpochSecond(ZoneOffset.UTC);
        System.out.println(epochSecond);
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        System.out.println(date);
    }
}
