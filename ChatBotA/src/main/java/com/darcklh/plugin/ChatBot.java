package com.darcklh.plugin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.darcklh.louise.Config.LouiseConfig;
import com.darcklh.louise.Model.InnerException;
import com.darcklh.louise.Model.Messages.InMessage;
import com.darcklh.louise.Model.Messages.Node;
import com.darcklh.louise.Model.Messages.OutMessage;
import com.darcklh.louise.Model.R;
import com.darcklh.louise.Model.Sender;
import com.darcklh.louise.Service.PluginService;
import com.darcklh.louise.Utils.UniqueGenerator;
import com.plexpt.chatgpt.ChatGPT;
import com.plexpt.chatgpt.util.Proxys;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author DarckLH
 * @date 2022/12/5 12:21
 * @Description
 */
public class ChatBot implements PluginService {

    private Logger log = LoggerFactory.getLogger(ChatBot.class);

    private class ChatEntity {
        Long unix_time;
        Long number;
        InMessage inMessage;
        boolean status;
    }

    private static OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(100, 30L, TimeUnit.MINUTES))
            // .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890)))
            .build();

    private R r = new R();

    public static void main(String[] args) {
//        //国内需要代理
////        Proxy proxy = Proxys.http("127.0.0.1", 7890);
//        //socks5 代理
//         Proxy proxy = Proxys.socks5("127.0.0.1", 7890);
//
//        ChatGPT chatGPT = new ChatGPT();
//
//        String res = chatGPT.chat("写一段七言绝句诗，题目是：火锅！");
        ChatBot ai = new ChatBot();
        LouiseConfig.BOT_ACCOUNT = "1655944518";
        LouiseConfig.LOUISE_ADMIN_NUMBER = "412543224";
        LouiseConfig.BOT_BASE_URL = "http://127.0.0.1:5700/";
        InMessage in = new InMessage();
        Sender sender = new Sender();
        sender.setNickname("DarckLh");
        sender.setUser_id((long) 412543224);
        in.setUser_id((long) 412543224);
        in.setGroup_id((long) -1);
        in.setSender(sender);
        in.setMessage_type("private");
        in.setMessage("!chat 你好");
        //in.setMessage("!aipaint blonde hair, loli");
        ai.service(in);
    }
    private static BlockingQueue<ChatEntity> chatEntities = new LinkedBlockingQueue<>();
    private static String api_key;
    private static String chat_api;
    private static int total_token;
    private boolean is_thread_on = false;

    @Override
    public String pluginName() {
        return null;
    }

    @Override
    public JSONObject service(InMessage inMessage) {

        // 初始化配置文件
        initConfig();
        OutMessage outMsg = new OutMessage(inMessage);

        // 如果 token 总数已经小于 1000 则拒绝服务
        if (total_token < 1000) {
            log.info("token 数不足，余" + total_token);
            outMsg.setMessage("token数告急，等待管理员补充！");
            r.sendMessage(outMsg);
            return null;
        }

        // 向列表中添加任务
        for ( ChatEntity chat_entity : chatEntities) {
            // 判断列表中是否存在该用户的请求
            if (chat_entity.number == inMessage.getUser_id()) {
                // 如果未超过任务等待时长则不作出任何回应
                if (Instant.now().getEpochSecond() - chat_entity.unix_time < 10) {
                    log.info("用户 " + inMessage.getUser_id() + " 频繁请求");
                    return null;
                }
                log.info("用户 " + inMessage.getUser_id() + " 等待时间过长");
                outMsg.setMessage("你上一句对话还没有得到回复，先别急");
                r.sendMessage(outMsg);
                return null;
            }
        }

        ChatEntity chat_entity = new ChatEntity();
        chat_entity.unix_time = Instant.now().getEpochSecond();
        chat_entity.number = inMessage.getUser_id();
        chat_entity.inMessage = inMessage;
        chat_entity.status = false;

        chatEntities.add(chat_entity);

        // 如果线程正在运行则不新建线程
        if (is_thread_on)
            return null;
        is_thread_on = true;

        // 启动一个线程循环读取对话列表里的任务并执行
        new Thread(() -> {
            for ( ChatEntity entity: chatEntities) {
                // 处理参数
                OutMessage out = new OutMessage(entity.inMessage);
                String prompt = entity.inMessage.getMessage().substring(5);
                Request.Builder builder = new Request.Builder();

                // 构造请求体
                JSONObject json = new JSONObject();
                json.put("model", "gpt-3.5-turbo");
                JSONObject message = new JSONObject();
                message.put("role", "user");
                message.put("content", prompt);
                JSONArray messages = new JSONArray();
                messages.add(message);
                json.put("messages", messages);
                RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json.toString());
                Request request = builder.url(chat_api).post(body).header("Authorization", "Bearer " + api_key).build();

                try {
                    Response response = HTTP_CLIENT.newCall(request).execute();
                    String body_string = response.body().string();
                    JSONObject result = JSON.parseObject(body_string);
                    // 统计 token 消耗
                    total_token -= result.getJSONObject("usage").getInteger("total_tokens");
                    // 获取回复并组装
                    String content = result.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");

                    if (out.getGroup_id() != -1) {
                        ArrayList<Node> nodeList = new ArrayList<>();
                        content = "[CQ:at,qq=" + entity.number + "]" + content;
                        nodeList.add(new Node(content, Long.parseLong(LouiseConfig.BOT_ACCOUNT)));
                        out.setMessages(nodeList);
                    } else {
                        out.setMessage(content);
                    }
                    r.sendMessage(out);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // 间隔固定时间执行下一条任务
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // 从队列中移除已经执行的任务
                chatEntities.remove();
            }
            // 执行完所有任务 线程状态置为闲置
            is_thread_on = false;
        }, UniqueGenerator.uniqueThreadName("CGR", "处理内部聊天任务")).start();

        return null;
    }

    @Override
    public JSONObject service() {
        return null;
    }

    @Override
    public boolean init() {
        return true;
    }

    @Override
    public boolean reload() {
        return true;
    }

    /**
     * 初始化配置文件
     */
    private void initConfig() {

        Yaml yaml = new Yaml();
        InputStream inputStream;
        try {
            inputStream = new FileInputStream("./config/chatgpt-config.yml");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new InnerException("PLG-ChatBot", "读取配置文件失败", e.getLocalizedMessage());
        }

        Map<String, Object> map = yaml.load(inputStream);
        api_key = (String) map.get("api_key");
        chat_api = (String) map.get("chat_api");
        total_token = (Integer) map.get("total_token");

    }
}
