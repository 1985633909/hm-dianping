package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author: 19856
 * @date: 2022/12/9-17:33
 * @description:
 */
@Component
@Slf4j
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);

    }

    public void setLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        //逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit timeUnit){
        String key = keyPrefix + id;
        //1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在  此时空值的话会判定false
        if (StrUtil.isNotBlank(json)){
            //3.存在  返回
            return JSONUtil.toBean(json, type);
        }
        //判断是否空值, null不等于""
        if (json !=null ){
            //部位空值，则为""
            return null;
        }

        //4.不存在，查询数据库
        R r = dbFallback.apply(id);
        if (r == null){
            //5.数据库没有，返回404
//            return Result.fail("店铺不存在!");
            //将控制写入redis
            stringRedisTemplate.opsForValue().set(key,"",time, timeUnit);
            return null;
        }
        //6.数据库存在，写入Redis
        this.set(key,r,time,timeUnit);
        //7.返回商铺信息

        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time, TimeUnit timeUnit){
        String key = keyPrefix + id;
        //1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在  此时空值的话会判定false
        if (StrUtil.isBlank(json)){
            //3.不存在  返回
            return null;
        }
        //命中，判断是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //5.1未过期，直接返回
        //过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //店铺信息
        R r = JSONUtil.toBean((JSONObject)redisData.getData(), type);
        //未过期直接返回
        if (expireTime.isAfter(LocalDateTime.now())){
            return r;
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
            R r2 = JSONUtil.toBean((JSONObject)redisData2.getData(),type);
            //未过期直接返回
            if (expireTime2.isAfter(LocalDateTime.now())){
                return r2;
            }

            //6.3开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    //重建缓存
                    R r1 = dbFallback.apply(id);
                    this.setLogicalExpire(key,r1,time,timeUnit);
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
        return  r;

    }


    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}
