package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryTypeList() {

        //从缓存redis获取所有商品类型数据（String）
        List<String> cacheShopTypeJson = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);

        //if下面转移至上面
        List<ShopType> cacheShopType = new ArrayList<>();
        //判断集合（多条json）是否为空
        if(cacheShopTypeJson == null || cacheShopTypeJson.isEmpty()){
            //为空，则查询数据库
            cacheShopType = query().orderByAsc("sort").list();

            //再为空，报404
            if(cacheShopType == null || cacheShopType.isEmpty()){
                log.info("!!!!!!数据库无数据");
                return null;
            }
            log.info("!!!!!!查询数据库的商品类型成功");

            //转换为json集合
            cacheShopType.forEach(shopType -> cacheShopTypeJson.add(JSONUtil.toJsonStr(shopType)));

            //将json集合存到redis缓存中
            stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY, cacheShopTypeJson);
            log.info("！！！！！！成功将商品类型数据存入redis");

            //设置有效期!!!!
            stringRedisTemplate.expire(CACHE_SHOP_TYPE_KEY, 3, TimeUnit.MINUTES);

            return cacheShopType;
        }

        //遍历将json转换为bean
        for(String str : cacheShopTypeJson){
            cacheShopType.add(JSONUtil.toBean(str, ShopType.class));
        }

        log.info("!!!!!!使用redis缓存");
        return cacheShopType;
    }
}
