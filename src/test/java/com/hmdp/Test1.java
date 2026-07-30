package com.hmdp;


import com.hmdp.utils.RedisIdWork;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SpringBootTest
@RunWith(SpringRunner.class)
public class Test1 {
    @Autowired
    private RedisIdWork redisIdWork;

    private ExecutorService es = Executors.newFixedThreadPool(100);

    @Test
    public void testRedisIdWork() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(100);
        Runnable task = () -> {
            for (int i = 0; i < 50; i++) {
                Long order = redisIdWork.nextId("order");
                System.out.println(order);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();

        System.out.println("所用时间:" + (end - begin));

        es.shutdown();
    }

}
