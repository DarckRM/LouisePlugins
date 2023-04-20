package com.darcklh.plugin;

import com.darcklh.louise.Model.Sender;

import java.util.Date;

/**
 * @author DarckLH
 * @date 2022/10/28 5:29
 * @Description 被封禁的用户
 */
public class BanUser {
    Long user_id;
    Integer baned_times;
    Long release_date;

    public Integer getBaned_times() {
        return baned_times;
    }

    public void setBaned_times(Integer baned_times) {
        this.baned_times = baned_times;
    }

    public Long getUser_id() {
        return user_id;
    }

    public void setUser_id(Long user_id) {
        this.user_id = user_id;
    }

    public Long getRelease_date() {
        return release_date;
    }

    public void setRelease_date(Long release_date) {
        this.release_date = release_date;
    }
}
