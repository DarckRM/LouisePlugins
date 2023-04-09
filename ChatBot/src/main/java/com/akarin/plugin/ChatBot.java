package com.akarin.plugin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;
import com.darcklh.louise.Config.LouiseConfig;
import com.darcklh.louise.Model.Messages.InMessage;
import com.darcklh.louise.Model.Messages.Node;
import com.darcklh.louise.Model.Messages.OutMessage;
import com.darcklh.louise.Model.R;
import com.darcklh.louise.Service.PluginService;
import com.darcklh.louise.Utils.OkHttpUtils;
import com.darcklh.louise.Utils.UniqueGenerator;
import com.darcklh.louise.Utils.YamlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author DarckLH
 * @date 2023/4/8 17:48
 * @Description
 */
public class ChatBot implements PluginService {

    Logger log = LoggerFactory.getLogger(ChatBot.class);
    private static BlockingQueue<ChatEntity> chatEntities = new LinkedBlockingQueue<>();
    private R r = new R();
    private String apiKey;
    private String chatApi;
    private int totalToken;

    private class ChatEntity {
        Long unix_time;
        Long number;
        InMessage inMessage;
        String prompt;
        boolean status;
    }

    public static void main (String[] args) {
        ChatBot chatBot = new ChatBot();
        chatBot.init();
    }

    @Override
    public String pluginName() {
        return null;
    }

    @Override
    public JSONObject service(InMessage inMessage) {
        OutMessage outMsg = new OutMessage(inMessage);

        // 如果 token 总数已经小于 1000 则拒绝服务
        if (totalToken < 1000) {
            log.info("token 数不足，余" + totalToken);
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
        chat_entity.prompt = inMessage.getMessage().substring(5);
        chat_entity.status = false;

        chatEntities.add(chat_entity);
        return null;
    }

    @Override
    public JSONObject service() {
        return null;
    }

    @Override
    public boolean init() {
        YamlReader yr = new YamlReader("./config/chatgpt-config.yml");
        Map<String, Object> map = yr.getProperties();
        apiKey = (String) map.get("api_key");
        chatApi = (String) map.get("chat_api");
        totalToken = (Integer) map.get("total_token");

        log.info("已加载 ChatBot 所有配置");
        // TODO 加载对话记录
        log.info("当前对话队列长度: " + chatEntities.size());
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890));
        // 启动一个线程循环读取对话列表里的任务并执行
        new Thread(() -> {
            while (true) {
                for ( ChatEntity entity: chatEntities) {
                    // 处理参数
                    OutMessage out = new OutMessage(entity.inMessage);
                    String prompt = entity.prompt;

                    // 构造请求体
                    JSONObject json = new JSONObject();
                    json.put("model", "gpt-3.5-turbo");
                    JSONObject message = new JSONObject();
                    message.put("role", "user");
                    message.put("content", prompt);
                    JSONArray messages = new JSONArray();
                    messages.add(message);
                    json.put("messages", messages);

                    String res = OkHttpUtils.builder(LouiseConfig.LOUISE_PROXY_PORT > 0 ? proxy : null).url(chatApi)
                            .addParam("model", "gpt-3.5-turbo")
                            .addBody(json.toString())
                            .addHeader("Authorization", "Bearer " + apiKey)
                            .post(false)
                            .async();

                    JSONObject result = JSON.parseObject(res);
                    // 统计 token 消耗
                    totalToken -= result.getJSONObject("usage").getInteger("total_tokens");
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

                    // 间隔固定时间执行下一条任务
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // 从队列中移除已经执行的任务
                    chatEntities.remove();
                }
                // 队列清空进入慢轮询模式
                try {
                    log.debug("聊天队列清空 开启慢轮询");
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, UniqueGenerator.uniqueThreadName("GPT", "聊天")).start();
        log.info("已启用聊天任务线程");
        return true;
    }

    @Override
    public boolean reload() {
        return false;
    }
}
