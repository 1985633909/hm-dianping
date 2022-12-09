package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryAll() {
        //1.从redis查询商铺缓存
        List<String> shopJson  = stringRedisTemplate.opsForList().range("cache:shopType", 0, -1);
        //转换成shopType泛型
        List<ShopType> shopTypes = new ArrayList<>();
        //2.判断是否存在
        if (!shopJson.isEmpty()){
            //3.存在  返回
            for (String s : shopJson) {
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);
                shopTypes.add(shopType);
            }
            return Result.ok(shopTypes);
        }
        //4.不存在，查询数据库
        shopTypes = query().orderByAsc("sort").list();
        if (shopTypes.isEmpty()){
            //5.数据库没有，返回404
            return Result.fail("商品类型不存在!");
        }
        //6.数据库存在，写入Redis
        for (ShopType shopType : shopTypes) {
            String s = JSONUtil.toJsonStr(shopType);
            shopJson.add(s);
        }
        stringRedisTemplate.opsForList().rightPushAll("cache:shopType",shopJson);
        //7.返回商铺信息

        return Result.ok(shopTypes);

    }
}
