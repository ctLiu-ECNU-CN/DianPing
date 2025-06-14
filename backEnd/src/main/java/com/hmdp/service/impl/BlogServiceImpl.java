package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import net.bytebuddy.asm.Advice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.entity.User;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private IBlogService blogService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        //1查询 blog
        Blog blog = getById(id);
        if(blog == null) {
            return Result.fail("笔记不存在");
        }
//        查询 blog的用户
        queryById(blog);
        ifBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 封装函数 判断是否点赞
     * @param blog
     */
    private void ifBlogLiked(Blog blog) {
        Long userId = UserHolder.getUser().getId();
//        判断当前登录用户是否已经点赞
        String key = "blog:liked:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);
    }

    private void queryById(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            this.queryById(blog);
            this.ifBlogLiked(blog);//当前用户是否点赞
        });
        return Result.ok(records);
    }

    /**
     * blog点赞功能实现
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
//        获取登录用户id
        Long userId = UserHolder.getUser().getId();
//        判断当前登录用户是否已经点赞
        String key = "blog:liked:" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score == null){
//        如果未点赞,可以点赞
//        数据库点赞数量+1
            boolean updateResult = update().setSql("liked = liked + 1").eq("id", id).update();
            if(updateResult){
//        保存用户到 Redis 的 zset  zadd key value score
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }

        }else{

//        如果已经点赞,数据库点赞数-1
            boolean updateResult = update().setSql("liked = liked - 1 ").eq("id", id).update();
//        把用户从 Redis 的 set 集合移除
            if(updateResult){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }
}
