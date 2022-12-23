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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;


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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void loadShopData(){
        //1.查询店铺信息
        List<Shop> list = shopService.list();
        //2.把店铺分组 按照typeId分组，id一致的放入一个集合
        Map<Long,List<Shop>> map = list.stream().collect(Collectors.groupingBy(shop -> shop.getTypeId()));
        //3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            //3.2获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            //3.3写入redis GEOADD key 精度 纬度 member
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Shop shop : value) {
//                stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                                shop.getId().toString(),
                                new Point(shop.getX(),shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }

    
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
        for (long i = 1; i <= 14; i++) {
            shop = shopService.getById(i);
            cacheClient.setLogicalExpire(RedisConstants.CACHE_SHOP_KEY + i,shop,10L, TimeUnit.SECONDS);
        }

    }

    @Test
    void testHyperLogLog(){
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0 ; i < 1000000 ; i ++){
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999){
                //发送到Redis
                stringRedisTemplate.opsForHyperLogLog().add("hl2",values);
            }
        }
        //统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println("count = " + count);

    }
}
