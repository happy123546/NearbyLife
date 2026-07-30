package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.anno.OperationLog;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.iKunInfoConstants.*;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public static final ExecutorService CACHE_BUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @OperationLog(desc = "商品详情")
    @Override
    public Shop queryShopById(Long id) {
        //商品key
        String key = CACHE_SHOP_KEY + id;

        //互斥锁（缓存击穿+缓存穿透解决）redis -> map
        //Shop shop = queryWithMutex1(id, key);

        //互斥锁（缓存击穿+缓存穿透解决）redis -> json
        Shop shop = queryWithMutex(id, key);

        //返回该商品数据
        return shop;
    }

    //尝试获取锁
    public Boolean tryLock(Long id){
        String key = SHOP_LOCK_KEY + id;
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", 20, TimeUnit.SECONDS);
        if(BooleanUtil.isTrue(lock)){
            return true;
        }
        return false;
    }

    //释放锁
    public void unLock(Long id){
        stringRedisTemplate.delete(SHOP_LOCK_KEY + id);
    }

    //缓存击穿(逻辑过期)
    public Shop queryWithLogicalExpire (Long id, String key){
        //从redis获取
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        //空则返回null
        if(redisDataJson == null){
            return null;
        }
        //获取RedisData
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);

        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = BeanUtil.toBean(data, Shop.class);
        //时间未过期，返回数据
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            return shop;
        }

        //时间过期
        //获取锁
        boolean lock = tryLock(id);

        //无锁则返回旧数据
        if(!lock){
            return shop;
        }
        //有锁则redis重构数据
        //开新线程进行存储
        CACHE_BUILD_EXECUTOR.submit(() -> {
            try {
                saveRedis(key, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                //释放锁
               unLock(id);
            }
        });

        //返回数据
        return shop;
    }

    public void saveRedis(String key, Long id){
        //查询数据
        Shop shop = getById(id);
        //封装为redisData
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(30));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //缓存击穿(互斥锁) -- json
    public Shop queryWithMutex(Long id, String key){
        //通过key查询redis的shop
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        Shop shop = new Shop();
        //不为空,判断是否为""即不为null,是则返回shop不存在
        if(StrUtil.isNotBlank(shopJson)){
            shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        if(shopJson != null){
            log.info("----------> 该商品不存在！");
            return null;
        }

        //为空，查询数据库

        boolean lock = false;
        try {
            //是否能获取锁
            //否，则进行递归
            if(!tryLock(id)){
                //休眠
                Thread.sleep(100);
                return queryShopById(id);
            }
            //能，则查询数据库
            lock = true;
            shop = getById(id);
            //shop为空时,缓存击穿解决
            if(shop == null){
                stringRedisTemplate.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
            }

            //将数据存入redis
            shopJson = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            if(lock){
                unLock(id);
            }
        }

        return shop;
    }

    //缓存击穿（互斥锁）-- map
    public Shop queryWithMutex1(Long id, String key){
        //从redis获取该商品
        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(key);
        //将map转换为bean
        Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);

        //redis有数据则返回
        if(BeanUtil.isNotEmpty(shop)){
            //查看数据是否为""
            if(shop.getName().isEmpty()){
                //该商品不存在
                log.info("----------> 已存入redis, 该商品不存在");
                return null;
            }
            log.info("{}的商品被查询",id);
            return shop;
        }

        Boolean lock = false;

        try {
            //获取异步锁
            lock = tryLock(id);
            if(!lock){
                //获取失败，休眠+重新(递归)
                Thread.sleep(100);
                return queryShopById(id);
            }

            log.info("成功获取锁");
            //无商品数据则从数据库获取该商品
            log.info(LOG_INFO_SELECT_SQL);
            shop = getById(id);
            //若再为空，则在缓存存入"",防止缓存穿透
            //
            if(BeanUtil.isEmpty(shop)) {
                Shop shop1 = new Shop();
                shop1.setName("");
                Map<String, Object> shopMap1 = BeanUtil.beanToMap(shop1);
                Map<String, String> newShopMap1 = new HashMap<>();
                shopMap1.forEach((k, v) -> newShopMap1.put(k, v == null ? "" : v.toString()));
                stringRedisTemplate.opsForHash().putAll(key, newShopMap1);
                stringRedisTemplate.expire(key, 2, TimeUnit.MINUTES);
                return shop1;
            }

            //将数据库查询的内容转化为map
            Map<String, Object> newShopMap = BeanUtil.beanToMap(shop);

            //将商品存在redis缓存
            Map<String, String> strShopMap = new HashMap<>();
            //newShopMap.forEach((k,v) -> strShopMap.put(k, v.toString()));
            newShopMap.forEach((k,v) -> strShopMap.put(k, v == null ? "" : v.toString()));

            stringRedisTemplate.opsForHash().putAll(key, strShopMap);
            //设置有效期
            stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            log.info(LOG_INFO_INSERT_REDIS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            if(lock){
                unLock(id);
                log.info("释放锁");
            }
        }

        return shop;
    }


    @Override
    public Result update(Shop shop) {
        //判断id是否为null
        if(shop.getId() == null){
            log.info(LOG_INFO_NULL);
            return null;
        }
        //更新数据库
        updateById(shop);
        log.info(LOG_INFO_UPDATE_SQL);

        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        log.info(LOG_INFO_DELETE_REDIS);

        return Result.ok();
    }
}
