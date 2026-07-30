package com.hmdp.utils;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.SHOP_LOCK_KEY;


@Component
public class CacheClient {
    //缓存穿透+缓存击穿 工具类

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public static final ExecutorService CACHE_BUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    //反序列化存入数据(缓存穿透)
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    //读取redis(缓存穿透->缓存空值)
    //反序列化存入数据(缓存击穿)
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    //读取redis(逻辑过期)
    public <R, ID> R queryWithLogicalExpire
    (String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        //key
        String key = keyPrefix + id;
        //从redis获取
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        //空则返回null
        if(redisDataJson == null){
            return null;
        }
        //获取RedisData
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);

        JSONObject data = (JSONObject) redisData.getData();
        R r = BeanUtil.toBean(data, type);
        //时间未过期，返回数据
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            return r;
        }

        //时间过期
        //获取锁
        boolean lock = tryLock(id);

        //无锁则返回旧数据
        if(!lock){
            return r;
        }
        //有锁则redis重构数据
        //开新线程进行存储
        CACHE_BUILD_EXECUTOR.submit(() -> {
            try {
                //查询数据
                R dbData = dbFallback.apply(id);
                setWithLogicalExpire(key, dbData, time, unit);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                //释放锁
                unLock(id);
            }
        });

        //返回数据
        return r;
    }

    //尝试获取锁
    public <ID> Boolean tryLock(ID id){
        String key = SHOP_LOCK_KEY + id;
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", 20, TimeUnit.SECONDS);
        if(BooleanUtil.isTrue(lock)){
            return true;
        }
        return false;
    }

    //释放锁
    public <ID> void unLock(ID id){
        stringRedisTemplate.delete(SHOP_LOCK_KEY + id);
    }



}
