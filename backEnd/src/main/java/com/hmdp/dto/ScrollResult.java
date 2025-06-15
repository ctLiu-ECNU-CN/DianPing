package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * 滚动分页
 */
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime; // 上一次查询的最小时间戳结果
    private Integer offset;
}
