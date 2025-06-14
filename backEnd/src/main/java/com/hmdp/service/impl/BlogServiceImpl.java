package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
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
import java.util.Collections;
import java.util.Formattable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

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

    @Resource
    private IFollowService followService;

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
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return;
        }
        Long userId = user.getId();
//        Long userId = UserHolder.getUser().getId();
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

    /**
     * 查询用户前五名点赞的用户
     * @param id
     * @return
     */
    @Override
    public Result queryTopLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //查询 top5的点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key,0,4);

        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());

        }
        //解析用户
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());

        //根据 id 查询用户
        String join = StrUtil.join(",", ids); //利用 MyBatisPlus自定义 query按照指定的 id 顺序 投影结果
        List<UserDTO> userDTOS = userService.query().in("id",ids).last("ORDER BY FIELD(id," + join +")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
//        获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
//        保存笔记
        boolean save = save(blog);
        if(!save){
            return Result.fail("新增笔记失败!");
        }
//        推送给粉丝
//        查询所有粉丝 select * from tb_follow where follow_id = user_id;
        List<Follow> followerIds = followService.query().eq("follow_user_id", user.getId()).list();
        for(Follow follow : followerIds){
//          推送给每一个粉丝的收件箱
            Long userId = follow.getUserId();
            // 推送
            String key = "feed:" + userId;
            // 使用 SortedSet,按照时间戳排序
            stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }
}
