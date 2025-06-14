package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    /**
     * 关注 or 取关
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 获取当前操作的用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        String key = "follows:" + userId;
        if(isFollow){
            // 关注,新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            // 更新数据库
            boolean save = save(follow);
            // 更新 redis
            if(save){
                // sadd usserId followerUserId

                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else{
            // 取关
            // 更新数据库
            boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            // 更新redis
            if(remove){
                //更新数据库成功后,再删除缓存
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 当前用户是否关注
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        // 获取当前操作的用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        // 查询是否关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    /**
     * 求共同用户
     * @param followUserId
     * @return
     */
    @Override
    public Result commonFollow(Long followUserId) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        String key = "follows:" + userId;
        String key2 = "follows:" + followUserId;
        // 交集运算
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);

        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        // 类型转换
        List<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 查询用户
        List<UserDTO> userDTOList = userService.listByIds(collect).stream().map(users -> BeanUtil.copyProperties(users, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
