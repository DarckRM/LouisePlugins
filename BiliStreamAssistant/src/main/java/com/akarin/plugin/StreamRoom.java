package com.akarin.plugin;

/**
 * @author DarckLH
 * @date 2023/3/19 8:45
 * @Description
 */

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 内部类直播间
 * 包含注册的需要监听直播间的用户和一些基础信息
 */
public class StreamRoom implements Serializable {
    List<Long> user_ids = new ArrayList<>();
    List<Long> group_ids = new ArrayList<>();
    boolean is_listening = true;
    int status = 0;
}
