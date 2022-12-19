package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.SeckillVoucherServiceImpl;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private CacheClient cacheClient;
    
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Autowired
    private VoucherOrderServiceImpl voucherOrder;
    
    private ExecutorService es = Executors.newFixedThreadPool(500);

    
    @Test
    public void testOnePeople(){
        LambdaQueryWrapper<VoucherOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(VoucherOrder::getVoucherId,2)
                .eq(VoucherOrder::getUserId,1010);
        int order = voucherOrder.count(queryWrapper);
        System.out.println("order = " + order);
    }
    @Test
    public void testUpdateSeckill(){
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(2);
        Integer stock = seckillVoucher.getStock();
        LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(SeckillVoucher::getStock,stock-1)
                .eq(SeckillVoucher::getVoucherId,2)
                .gt(SeckillVoucher::getStock,0);

        seckillVoucherService.update(updateWrapper);
    }
    
    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100; i++) {
                    long id = redisIdWorker.nextId("order");
                    System.out.println("id = " + id);
                }
                latch.countDown();
            }
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("(end-begin) = " + (end-begin));
        
    }

    @Test
    public void testSaveShop() throws InterruptedException {
/*
        Shop shop = shopService.getById(4L);
        cacheClient.setLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 4L,shop,10L, TimeUnit.SECONDS);
*/
        Shop shop;
        for (long i = 1; i <= 5; i++) {
            shop = shopService.getById(i);
            cacheClient.setLogicalExpire(RedisConstants.CACHE_SHOP_KEY + i,shop,10L, TimeUnit.SECONDS);
        }

    }
}
