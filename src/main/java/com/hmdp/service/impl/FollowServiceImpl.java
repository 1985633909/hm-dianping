package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService iUserService;
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //0.获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" +userId;
        //1.判断到底是关注还是取关
        if (isFollow) {
            //2.关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            //放入数据库
            if (isSuccess){
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        } else {
            //3.取关，删除 delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, followUserId));
            //删除redis的set
            if (isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();

    }

    @Override
    public Result isFollow(Long followUserId) {
        //0.获取当前登录用户id
        Long userId = UserHolder.getUser().getId();

        //1.查询是否关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();

        return Result.ok(count > 0);

    }

    @Override
    public Result followCommons(Long id) {
        //0.获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:";
        List<Long> list = new ArrayList<>();
        //1.查询当前登录用户id的关注
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key + userId, key + id);
        if (intersect == null ||intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        for (String s : intersect) {
            list.add(Long.parseLong(s));
        }

        //2.查询关注用户的id

        //3.返回共同关注的好友
        List<UserDTO> users = new ArrayList<>();
        for (User user : iUserService.listByIds(list)) {
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            users.add(userDTO);
        }
        return Result.ok(users);
    }
}
