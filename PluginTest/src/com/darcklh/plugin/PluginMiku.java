package com.darcklh.plugin;

import com.alibaba.fastjson.JSONObject;
import com.darcklh.louise.Model.Messages.InMessage;
import com.darcklh.louise.Model.Messages.OutMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.darcklh.louise.Model.*;

public class PluginMiku implements com.darcklh.louise.Service.PluginService {

    Logger logger = LoggerFactory.getLogger(PluginMiku.class);

    public static void main(String[] args) {
        PluginMiku pluginMiku = new PluginMiku();
        pluginMiku.service();
    }

    @Override
    public String pluginName() {
        return null;
    }

    @Override
    public com.alibaba.fastjson.JSONObject service() {
        R r = new com.darcklh.louise.Model.R();
        logger.info("进入插件PluginMiku");
        OutMessage outMessage = new OutMessage();
        outMessage.setUser_id((long)412543224);
        outMessage.setMessage("测试插件发送消息");
        r.sendMessage(outMessage);
        return null;
    }
}
