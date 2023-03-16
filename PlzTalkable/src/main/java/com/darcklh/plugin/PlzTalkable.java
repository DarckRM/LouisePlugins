package com.darcklh.plugin;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.darcklh.louise.Model.GoCqhttp.RequestPost;
import com.darcklh.louise.Model.Messages.InMessage;
import com.darcklh.louise.Model.Messages.OutMessage;
import com.darcklh.louise.Model.R;
import com.darcklh.louise.Service.PluginService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author DarckLH
 * @date 2022/11/21 17:05
 * @Description
 */
public class PlzTalkable implements PluginService {

    Logger log = LoggerFactory.getLogger(PlzTalkable.class);

    /**
     * 最大连接时间
     */
    public final int CONNECTION_TIMEOUT = 15;

    /**
     * OkHTTP线程池最大空闲线程数
     */
    public final int MAX_IDLE_CONNECTIONS = 100;
    /**
     * OkHTTP线程池空闲线程存活时间
     */
    public final long KEEP_ALIVE_DURATION = 30L;

    /**
     * client
     * 配置重试
     */
    private OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION, TimeUnit.MINUTES))
            .protocols(Collections.singletonList(Protocol.HTTP_1_1))
            .build();

    @Override
    public String pluginName() {
        return null;
    }

    @Override
    public JSONObject service(InMessage inMessage) {

        OutMessage outMessage = new OutMessage(inMessage);
        R r = new R();
        JSONObject payload = new JSONObject();
        payload.put("text", inMessage.getMessage().substring(2));
        Request.Builder builder = new Request.Builder();
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), payload.toString());
        Request request = builder.url("https://lab.magiconch.com/api/nbnhhsh/guess").post(body).build();
        try {
            Response response = HTTP_CLIENT.newCall(request).execute();
            JSONArray result = JSONObject.parseArray(response.body().string());

            JSONObject object = result.getJSONObject(0);
            String trans = object.getJSONArray("trans").toString();

            outMessage.setMessage(inMessage.getMessage().substring(2) + " 可能是它们的缩写\n" + trans);
            r.sendMessage(outMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public JSONObject service() {
        return null;
    }
}
