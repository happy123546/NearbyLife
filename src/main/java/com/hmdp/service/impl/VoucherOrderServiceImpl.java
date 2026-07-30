package com.hmdp.service.impl;

import com.hmdp.config.RedisConfig;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWork;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;

    @Autowired
    private RedisIdWork redisIdWork;

    @Autowired
    private RedisConfig redisConfig;
    @Autowired
    private RedissonClient redissonClient;

    @Override
    public Long getSeckillVoucherId(Long voucherId){
        //根据id获取优惠券信息
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        //判断优惠券秒杀活动是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            log.info("----------> 秒杀活动还没开始");
            Result.fail("----------> 秒杀活动还没开始");
        }
        //是否结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            log.info("----------> 秒杀活动已经结束");
            Result.fail("----------> 秒杀活动已经结束");
        }

        long userId = UserHolder.getUser().getId();
        //redis分布式锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        if(!lock.tryLock()){
            throw new RuntimeException("不允许重复下单");
        }

        try {
            synchronized (UserHolder.getUser().toString().intern()) {
                IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
                return proxy.createOrder(voucher, voucherId);
            }
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }


    }

    @Transactional
    public Long createOrder(SeckillVoucher voucher, Long voucherId){

        long userId = UserHolder.getUser().getId();

        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0){
            throw new RuntimeException("您已购买过该优惠券");
        }

        //更新库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if(!success){
            log.info("更新失败");
            throw new RuntimeException("库存不足");
        }
        //生成订单id
        long orderId = redisIdWork.nextId("voucher");

        VoucherOrder vo = new VoucherOrder();
        vo.setUserId(userId);
        vo.setVoucherId(voucherId);
        vo.setId(orderId);
        save(vo);
        return orderId;
    }
}
