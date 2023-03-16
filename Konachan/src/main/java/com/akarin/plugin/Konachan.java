package com.akarin.plugin;

import com.alibaba.fastjson.JSONObject;
import com.darcklh.louise.Model.Messages.InMessage;
import com.darcklh.louise.Service.PluginService;

/**
 * @author DarckLH
 * @date 2022/12/16 20:15
 * @Description
 */
public class Konachan implements PluginService {
    @Override
    public String pluginName() {
        return null;
    }

    @Override
    public JSONObject service(InMessage inMessage) {
        return null;
    }

    @Override
    public JSONObject service() {
        return null;
    }

    private boolean validParam() {
        return true;
    }
}
