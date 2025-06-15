package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.entity.User;
import com.hmdp.utils.SystemConstants;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private IBlogService blogService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    /**
     * 根据 blog_id 查询 Blog
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        //1查询 blog
        Blog blog = getById(id);
        if(blog == null) {
            return Result.fail("笔记不存在");
        }
//        查询 blog 有关联的用户
        queryBlogUser(blog);
//        查询是否被点赞
        ifBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 封装函数 判断是否点赞 并且在 blog中保存点赞信息
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


    /**
     * 查询 Blog 相关的用户,并且给该 Blog设置所属的用户名和用户头像
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 查询热门Blog
     * @param current
     * @return
     */
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
            this.queryBlogUser(blog);
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

    /**
     * 新增 blog功能 (用户上传 Blog)
     * @param blog
     * @return
     */
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

    /**
     * 查询关注的人的 Blogs,
     * @param max
     * @param offset
     * @return Blog列表
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
//        1.获取当前用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
//        2.查询收件箱
        String key  = FEED_KEY +userId;
        Set<ZSetOperations.TypedTuple<String>> SortedBlogsTuple = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if(SortedBlogsTuple == null || SortedBlogsTuple.isEmpty()){
            log.info("BlogList Null");
            return Result.ok(Collections.emptyList());
        }
        //        3.解析收件箱 blogId,minTime,offset
        List<Long> ids = new ArrayList<>(SortedBlogsTuple.size());
        Long minTime = 0L;//最后一个时间是最小时间
        int os = 1;//偏移量.自己就是一个(这个偏移量是判断相同时间戳的score 个数)
        for(ZSetOperations.TypedTuple<String> tuple : SortedBlogsTuple){
//            获取 id
            String idStr = tuple.getValue();
            ids.add(Long.valueOf(idStr));
//            获取分数
            if(minTime == tuple.getScore().longValue()){
                os++;
            }else{
                minTime = tuple.getScore().longValue();
                os = 1;
            }
        }

//        根据 id查询 blog

        String idStr = StrUtil.join(",", ids);
//        批量查询(自定义批量查询,保证按照 idList 的顺序输出结果
        List<Blog> blogList = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        //查询 Blog 有关的信息
        for(Blog blog : blogList){
//            log.info("Blog Id:" + blog.getId() + " Title:" + blog.getTitle());
//        查询 blog 有关联的用户
            queryBlogUser(blog);
//        查询是否被点赞
            ifBlogLiked(blog);
        }
//        封装 blog集合
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
//        log.info("<滚动查询:{}>",scrollResult);

        return Result.ok(scrollResult);
    }
}
