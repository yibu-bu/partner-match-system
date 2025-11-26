package com.arteon.domain.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 封装分页查询请求
 */
@Data
public class PageRequest implements Serializable {

    private static final long serialVersionUID = -5860707094194210842L;

    /**
     * 页面大小
     */
    protected int pageSize = 10;

    /**
     * 当前是第几页
     */
    protected int pageNum = 1;

}