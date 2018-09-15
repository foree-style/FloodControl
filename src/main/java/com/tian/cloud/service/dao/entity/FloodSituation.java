package com.tian.cloud.service.dao.entity;

import lombok.Data;

/**
 * @author tianguang
 * 2018/8/20 上午11:14
 **/
@Data
public class FloodSituation {

    private Integer id;

    private Integer companyId;

    private Integer status;

    private long startTime;

    private long endTime;

    private String title = "";

    @Deprecated
    private String floodDesc = "";

    private long createTime;

    private long updateTime;

    /* for前端展示-start */
    private String companyName;

    private String createTimeStr;
}
