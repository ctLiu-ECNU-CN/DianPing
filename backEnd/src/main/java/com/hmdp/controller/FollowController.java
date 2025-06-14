package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {


    @Resource
    private IFollowService followService;

    /**
     * 当前用户关注某个用户,或者取关
     * @param followUserId 关注或者取关的目标用户 id
     * @param isFollow 关注还是取关
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow ){
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 查询当前用户 是否关注了 某个用户
     * @param followUserId 某个用户
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result follow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    /**
     * 查询当前用户和目标用户的共同关注
     * @param followUserId
     * @return
     */
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") Long followUserId) {
        return followService.commonFollow(followUserId);
    }
}
