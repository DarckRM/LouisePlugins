package com.darcklh.plugin;

import com.alibaba.fastjson.JSONObject;
import com.darcklh.louise.Config.LouiseConfig;
import com.darcklh.louise.Model.InnerException;
import com.darcklh.louise.Model.Messages.InMessage;
import com.darcklh.louise.Model.Messages.OutMessage;
import com.darcklh.louise.Model.R;
import com.darcklh.louise.Model.ReplyException;
import com.darcklh.louise.Model.Sender;
import com.darcklh.louise.Service.PluginService;
import com.darcklh.louise.Utils.UniqueGenerator;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author DarckLH
 * @date 2022/10/20 15:37
 * @Description
 */
public class AiPaint implements PluginService {

    private Logger log = LoggerFactory.getLogger(AiPaint.class);
    /**
     * 最大连接时间
     */
    public final static int CONNECTION_TIMEOUT = 15;
    /**
     * JSON格式
     */
    public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    /**
     * OkHTTP线程池最大空闲线程数
     */
    public final static int MAX_IDLE_CONNECTIONS = 100;
    /**
     * OkHTTP线程池空闲线程存活时间
     */
    public final static long KEEP_ALIVE_DURATION = 30L;

    /**
     * client
     * 配置重试
     */
    private final static OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION, TimeUnit.MINUTES))
            .build();

    private BlockingQueue<PaintTask> paintQueue = new LinkedBlockingQueue<>();

    private static JSONObject naifuBody = new JSONObject();

    public static void main(String[] args) {
        AiPaint ai = new AiPaint();
        LouiseConfig.BOT_ACCOUNT = "1655944518";
        LouiseConfig.BOT_BASE_URL = "http://127.0.0.1:5700/";
        InMessage in = new InMessage();
        Sender sender = new Sender();
        sender.setUser_id((long) 412543224);
        in.setUser_id((long) 412543224);
        in.setMessage_type("private");
        in.setMessage("!aipaint loli,blonde_hair");
        ai.service(in);
    }

    @Override
    public String pluginName() {
        return null;
    }

    @Override
    public JSONObject service(InMessage inMessage) {
        // 加载配置文件
        Yaml yaml = new Yaml();
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream("aipaint.yml");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new InnerException("PLG-AiPaint", "读取配置文件失败", e.getLocalizedMessage());
        }
        Map<String, String> map = yaml.load(inputStream);
        String url = map.get("app-api");
        String cookie = map.get("cookie");
        String user_agent = map.get("user-agent");
        String ai_status = map.get("ai-status");

        // 初始各种参数
        naifuBody.put("width", map.get("width"));
        naifuBody.put("height", map.get("height"));
        naifuBody.put("scale", map.get("scale"));
        naifuBody.put("sampler", map.get("sampler"));
        naifuBody.put("steps", map.get("steps"));
        naifuBody.put("n_samples", map.get("n_samples"));
        naifuBody.put("ucPreset", map.get("udPreset"));

        naifuBody.put("seed", new Random().nextInt(9999));

        // 声明一个新任务
        PaintTask task = new PaintTask();
        task.prompt = map.get("prompt");
        task.negative_prompt = map.get("negative-prompt");
        task.inMessage = inMessage;
        String args = inMessage.getMessage().substring(8);
        String tag = inMessage.getMessage().split(" ")[1];
        task.prompt += args;
        R r = new R();
        OutMessage outMsg = new OutMessage(inMessage);
        task.outMessage = outMsg;

        if (tag.equals("$go")) {
            startPaint(cookie, user_agent, url, r);
            return null;
        }
        log.info("AiPaint 任务参数: " + task.prompt);
        boolean isAiReady = true;
        // 将任务添加进队列
        if (!ai_status.equals("ready")) {
            outMsg.setMessage("Ai 现在不可用，系统将尝试将你的绘画请求添加进任务队列，当 Ai 恢复可用时会按顺序处理任务");
            r.sendMessage(outMsg);
            isAiReady = false;
        }
        try {
            // 队列最大允许 20 个任务排队
            if (paintQueue.size() > 4) {
                throw new ReplyException("AI 已经被塞满了");
            } else {
                paintQueue.put(task);
                outMsg.setMessage("[CQ:at,qq=" + inMessage.getUser_id() + "]绘画请求已加入队列，当前队列长度: " + paintQueue.size());
                r.sendMessage(outMsg);
            }
        } catch (InterruptedException e) {
            throw new ReplyException("添加作画任务失败了");
        }

        if (!isAiReady)
            return null;

        // 如果队列中已经存在 2 个以上的任务则停止开启线程
        if (paintQueue.size() > 1)
            return null;
        startPaint(cookie, user_agent, url, r);

        return null;
    }

    @Override
    public JSONObject service() {
        return null;
    }

    private void startPaint(String cookie, String user_agent, String url, R r) {
        new Thread(() -> {
            while (paintQueue.size() != 0) {
                log.info("AiPaint 任务队列长度: " + paintQueue.size());
                PaintTask inTask = paintQueue.peek();
                inTask.outMessage.setMessage("开始作画任务，当前任务队列长度: " + paintQueue.size());
                r.sendMessage(inTask.outMessage);

                RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, getNaifuBody(inTask).toJSONString());
                Request.Builder builder = new Request.Builder()
                        .addHeader("Cookie", cookie)
                        .addHeader("User-Agent", user_agent);
                Request request = builder.url(url).post(body).build();
                try {
                    log.info("AiPaint 尝试请求 API: " + url);
                    Response response = HTTP_CLIENT.newCall(request).execute();
                    // 判断状态码，如果获取不到图片重试三次
                    boolean result = false;
                    if (response.code() != 200) {
                        if (response.code() == 204) {
                            throw new ReplyException("AI 维护中");
                        }
                        if (response.code() == 400) {
                            int retry = 0;
                            while (retry < 10) {
                                Thread.sleep(3000);
                                Response retryResponse = HTTP_CLIENT.newCall(request).execute();
                                if (retryResponse.code() == 200) {
                                    response = retryResponse;
                                    break;
                                }
                                retry++;
                            }
                            if (response.code() != 200) {
                                throw new ReplyException("AI 有未知的异常" +
                                        "\nHTTP 状态码: " + response.code() +
                                        "\nResponse Body: " + response.body());
                            } else
                                result = true;
                        }
                        if (response.code() != 200) {
                            throw new ReplyException("画图 API 异常，请检查 cookie 是否正确");
                        }
                    } else
                        result = true;

                    // String firstResult = response.body().string();
                    if (!result) {
                        throw new ReplyException("AI 忙不过来了，先让它歇一会儿吧~ (20s 后尝试)");
                    }

                    String imgData = response.body().string().split("\\r?\\n")[2].substring(5);

                    //TODO ai图片下载到本地
                    paintQueue.remove();
                    OutMessage out = new OutMessage(inTask.inMessage);
                    out.setMessage("[CQ:at,qq=" + out.getSender().getUser_id() + "]这是你召唤的图片哦" +
                            "\n参数: " + inTask.prompt +
                            "\n[CQ:image,file=base64://" + imgData +
                            "]\n剩下还有 " + paintQueue.size() + " 个任务");
                    r.sendMessage(out);
                } catch (Exception e) {
                    paintQueue.remove();
                    if (e instanceof ReplyException) {
                        throw (ReplyException) e;
                    }
                    throw new InnerException("PLG-AiPaint", "遇到内部错误", e.getLocalizedMessage());
                }
            }
        }, UniqueGenerator.uniqueThreadName("", "AIP")).start();
    }

    private JSONObject getNaifuBody(PaintTask task) {
        naifuBody.put("prompt", task.prompt);
        naifuBody.put("uc", task.negative_prompt);
        return naifuBody;
    }



    private ArrayList<Object> getAiAPIBody(PaintTask task) {
        ArrayList<Object> array = new ArrayList<>();
        array.add(task.prompt);
        array.add(task.negative_prompt);
        array.add("None");
        array.add("None");
        array.add(20);
        array.add("Euler a");
        array.add(false);
        array.add(false);
        array.add(1);
        array.add(1);
        array.add(7);
        array.add(-1);
        array.add(-1);
        array.add(0);
        array.add(0);
        array.add(0);
        array.add(false);
        array.add(768);
        array.add(512);
        array.add(false);
        array.add(0.7);
        array.add(768);
        array.add(512);
        array.add("None");
        array.add(false);
        array.add(false);
        array.add(null);
        array.add("");
        array.add("Seed");
        array.add("");
        array.add("Nothing");
        array.add("");
        array.add(true);
        array.add(false);
        array.add(false);
        array.add(null);
        array.add("");
        array.add("");
        return array;
    }

    class PaintTask {
        OutMessage outMessage;
        InMessage inMessage;
        String prompt;
        String negative_prompt;
    }

}
