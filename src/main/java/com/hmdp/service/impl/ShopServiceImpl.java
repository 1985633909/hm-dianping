package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class, this::getById,CACHE_SHOP_TTL,TimeUnit.SECONDS);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.SECONDS);
        if (shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
/*
    public Shop queryWithLogicalExpire(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在  此时空值的话会判定false
        if (StrUtil.isBlank(shopJson)){
            //3.不存在  返回
            return null;
        }
        //命中，判断是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //5.1未过期，直接返回
        //过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
//        System.out.println("过期时间 = " + expireTime);
//        System.out.println("当前时间 = " + LocalDateTime.now());
        //店铺信息
        Shop shop = JSONUtil.toBean((JSONObject)redisData.getData(), Shop.class);
        //未过期直接返回
        if (expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        //5.2过期，缓存重建
        //6.1尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock){
            //6.2获取成功
            //再次检测redis缓存是否过期（如果并发线程刚好新建立缓存，则不需要重新新建）
            String shopJson2 = stringRedisTemplate.opsForValue().get(key);
            //2.判断是否存在  此时空值的话会判定false
            if (StrUtil.isBlank(shopJson2)){
                //3.不存在  返回
                return null;
            }
            //命中，判断是否过期
            RedisData redisData2 = JSONUtil.toBean(shopJson2, RedisData.class);
            //5.1未过期，直接返回
            //过期时间
            LocalDateTime expireTime2 = redisData2.getExpireTime();
            //店铺信息
            Shop shop2 = JSONUtil.toBean((JSONObject)redisData2.getData(), Shop.class);
            //未过期直接返回
            if (expireTime2.isAfter(LocalDateTime.now())){
                return shop2;
            }

            //6.3开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    //重建缓存
                    this.saveShop2Redis(id,30L);
                    System.out.println("刷新热点key");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });

        }
        //7 返回过期的商铺信息
        return  shop;

    }*/


/*    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在  此时空值的话会判定false
        if (StrUtil.isNotBlank(shopJson)){
            //3.存在  返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否空值, null不等于""
        if (shopJson !=null ){
            //不为空值，则为""
            return null;
        }
        //4.1开始实现缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否获取成功
            if (!isLock){
                //失败，休眠，并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //再次判断redis缓存是否存在，如果存在无需重建缓存
            String shopJson2 = stringRedisTemplate.opsForValue().get(key);
            //2.判断是否存在  此时空值的话会判定false
            if (StrUtil.isNotBlank(shopJson2)){
                //3.存在  返回
                return JSONUtil.toBean(shopJson2, Shop.class);
            }
            //判断是否空值, null不等于""
            if (shopJson2 !=null ){
                //不为空值，则为""
                return null;
            }

            //确定不存在多线程已经新建缓存，此时可以继续

            //4.成功，查询数据库
            shop = getById(id);
            //模拟重建的延迟
            Thread.sleep(200);
            if (shop == null){
                //5.数据库没有，返回404
    //            return Result.fail("店铺不存在!");
                //将控制写入redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);

                return null;
            }
            //6.数据库存在，写入Redis
            String parse = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key,parse,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unlock(lockKey);
        }

        //7.返回商铺信息

        return shop;
    }*/



    public Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在  此时空值的话会判定false
        if (StrUtil.isNotBlank(shopJson)){
            //3.存在  返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断是否空值, null不等于""
        if (shopJson !=null ){
            //部位空值，则为""
            return null;
        }

        //4.不存在，查询数据库
        Shop shop = getById(id);
        if (shop == null){
            //5.数据库没有，返回404
//            return Result.fail("店铺不存在!");
            //将控制写入redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.数据库存在，写入Redis
        String parse = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key,parse,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回商铺信息

        return shop;
    }

/*    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装成逻辑过期
        RedisData redisData = new RedisData();

        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }*/


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺id不能为null");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
