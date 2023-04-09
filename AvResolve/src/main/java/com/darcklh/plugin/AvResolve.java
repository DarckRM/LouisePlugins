package com.darcklh.plugin;

import com.alibaba.fastjson.JSONObject;
import com.darcklh.louise.Config.LouiseConfig;
import com.darcklh.louise.Model.InnerException;
import com.darcklh.louise.Model.Messages.InMessage;
import com.darcklh.louise.Model.Messages.OutMessage;
import com.darcklh.louise.Model.R;
import com.darcklh.louise.Model.Sender;
import com.darcklh.louise.Service.PluginService;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author DarckLH
 * @date 2022/11/12 21:46
 * @Description
 */
public class AvResolve implements PluginService {

    Logger log = LoggerFactory.getLogger(AvResolve.class);

    /**
     * 最大连接时间
     */
    public final static int CONNECTION_TIMEOUT = 15;

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
    private static OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION, TimeUnit.MINUTES))
            .build();

    private String bilibili_api;

    private R r = new R();

    public static void main (String[] args) {
        LouiseConfig.BOT_ACCOUNT = "1655944518";
        LouiseConfig.BOT_BASE_URL = "http://127.0.0.1:5700/";
        AvResolve pp = new AvResolve();
        InMessage in = new InMessage();
        Sender sender = new Sender();
        sender.setUser_id((long) 412543224);
        in.setUser_id((long) 412543224);
        in.setMessage_type("private");
        in.setMessage("{\"post_type\":\"message\",\"message_type\":\"private\",\"time\":1668448566,\"self_id\":1655944518,\"sub_type\":\"friend\",\"user_id\":412543224,\"target_id\":1655944518,\"message\":\"[CQ:xml,data=\\u003c?xml version='1.0' encoding='UTF-8' standalone='yes'?\\u003e\\u003cmsg templateID=\\\"123\\\" url=\\\"https://b23.tv/nBoD9JH?share_medium=android\\u0026amp;amp;share_source=qq\\u0026amp;amp;bbid=XY0A35A0B1D714E1CAB505D643F33C00D4067\\u0026amp;amp;ts=1668445816230\\\" serviceID=\\\"1\\\" action=\\\"web\\\" actionData=\\\"\\\" a_actionData=\\\"\\\" i_actionData=\\\"\\\" brief=\\\"\\u0026#91;QQ小程序\\u0026#93;哔哩哔哩\\\" flag=\\\"0\\\"\\u003e\\u003citem layout=\\\"2\\\"\\u003e\\u003cpicture cover=\\\"http://pubminishare-30161.picsz.qpic.cn/f25bc7cd-f05d-4bfc-bd18-a7ae6731427f\\\"/\\u003e\\u003ctitle\\u003e哔哩哔哩\\u003c/title\\u003e\\u003csummary\\u003e“真.龟霸”?这样的忍者神龟，梦里都没见过！火仔动漫 究极合金 忍者神龟 李奥纳多 达芬奇【评头论足】\\u003c/summary\\u003e\\u003c/item\\u003e\\u003csource url=\\\"https://b23.tv/nBoD9JH?share_medium=android\\u0026amp;amp;share_source=qq\\u0026amp;amp;bbid=XY0A35A0B1D714E1CAB505D643F33C00D4067\\u0026amp;amp;ts=1668445816230\\\" icon=\\\"https://open.gtimg.cn/open/app_icon/00/95/17/76/100951776_100_m.png?t=1659061321\\\" name=\\\"哔哩哔哩\\\" appid=\\\"0\\\" action=\\\"web\\\" actionData=\\\"\\\" a_actionData=\\\"tencent0://\\\" i_actionData=\\\"\\\"/\\u003e\\u003c/msg\\u003e,resid=]\",\"raw_message\":\"[CQ:xml,data=\\u003c?xml version='1.0' encoding='UTF-8' standalone='yes'?\\u003e\\u003cmsg templateID=\\\"123\\\" url=\\\"https://b23.tv/nBoD9JH?share_medium=android\\u0026amp;amp;share_source=qq\\u0026amp;amp;bbid=XY0A35A0B1D714E1CAB505D643F33C00D4067\\u0026amp;amp;ts=1668445816230\\\" serviceID=\\\"1\\\" action=\\\"web\\\" actionData=\\\"\\\" a_actionData=\\\"\\\" i_actionData=\\\"\\\" brief=\\\"\\u0026#91;QQ小程序\\u0026#93;哔哩哔哩\\\" flag=\\\"0\\\"\\u003e\\u003citem layout=\\\"2\\\"\\u003e\\u003cpicture cover=\\\"http://pubminishare-30161.picsz.qpic.cn/f25bc7cd-f05d-4bfc-bd18-a7ae6731427f\\\"/\\u003e\\u003ctitle\\u003e哔哩哔哩\\u003c/title\\u003e\\u003csummary\\u003e“真.龟霸”?这样的忍者神龟，梦里都没见过！火仔动漫 究极合金 忍者神龟 李奥纳多 达芬奇【评头论足】\\u003c/summary\\u003e\\u003c/item\\u003e\\u003csource url=\\\"https://b23.tv/nBoD9JH?share_medium=android\\u0026amp;amp;share_source=qq\\u0026amp;amp;bbid=XY0A35A0B1D714E1CAB505D643F33C00D4067\\u0026amp;amp;ts=1668445816230\\\" icon=\\\"https://open.gtimg.cn/open/app_icon/00/95/17/76/100951776_100_m.png?t=1659061321\\\" name=\\\"哔哩哔哩\\\" appid=\\\"0\\\" action=\\\"web\\\" actionData=\\\"\\\" a_actionData=\\\"tencent0://\\\" i_actionData=\\\"\\\"/\\u003e\\u003c/msg\\u003e,resid=]\",\"font\":0,\"sender\":{\"age\":0,\"nickname\":\"Akarin\",\"sex\":\"unknown\",\"user_id\":412543224},\"message_id\":261454883}");

        pp.service(in);
    }

    @Override
    public String pluginName() {
        return null;
    }

    @Override
    public JSONObject service(InMessage inMessage) {

        initConfig();

        // 正则取值
        Pattern pattern = Pattern.compile("https://b23.tv/.*?s");
        Matcher matcher = pattern.matcher(inMessage.getMessage());
        // 如果是短链接类型
        if(matcher.find()) {
            String short_url = matcher.group(0);
            Request.Builder builder = new Request.Builder();
            Request request = builder.url(short_url).get().build();

            try {
                Response response = HTTP_CLIENT.newCall(request).execute();
                pattern = Pattern.compile("BV.*/");
                matcher = pattern.matcher(response.request().url().toString());
                matcher.find();
                String bvid = matcher.group(0);
                bilibili_api += "bvid=" + bvid.replaceAll("/", "");
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            if (inMessage.getMessage().matches("^[aA].*"))
                bilibili_api += "aid=" + inMessage.getMessage().substring(2);
            else
                bilibili_api += "bvid=" + inMessage.getMessage();
        }

        OutMessage outMessage = new OutMessage(inMessage);

        Request.Builder builder = new Request.Builder();
        Request request = builder.url(bilibili_api).get().build();
        // Request requestqa = builder.url("https://b23.tv/nBoD9JH").get().build();
        try {
            Response response = HTTP_CLIENT.newCall(request).execute();
            JSONObject result = JSONObject.parseObject(response.body().string());

            int code = result.getInteger("code");
            switch (code) {
                case 0: sendResult(result, outMessage); return null;
                case -400: outMessage.setMessage("请求错误"); break;
                case -403: outMessage.setMessage("权限不足"); break;
                case -404: outMessage.setMessage("没有找到视频"); break;
                case 62002: outMessage.setMessage("稿件不可见"); break;
                case 62004: outMessage.setMessage("稿件审核中"); break;
            }
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

    @Override
    public boolean init() {
        return true;
    }

    @Override
    public boolean reload() {
        return true;
    }

    private void sendResult(JSONObject result, OutMessage outMessage) {
        StringBuilder message = new StringBuilder();
        JSONObject data = result.getJSONObject("data");
        JSONObject stat = data.getJSONObject("stat");
        String formats = "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat dateFormat = new SimpleDateFormat(formats);

        String pdate = dateFormat.format(new Date(data.getLong("pubdate") * 1000));
        String ctime = dateFormat.format(new Date(data.getLong("ctime") * 1000));

        message.append("BV号: ").append(data.getString("bvid")).append("\n").
        append("AV号: ").append(data.getInteger("aid")).append("\n").
        append("发布者: ").append(data.getJSONObject("owner").getString("name")).append(" 版权: ").append(data.getInteger("copyright") == 1 ? "原创" : "转载").append("\n").
        append("发布时间: ").append(pdate).append(" 投稿时间: ").append(ctime).append("\n").
        append("标题: ").append(data.getString("title")).append("\n").
        append("[CQ:image,file=").append(data.getString("pic")).append("]\n").
        append("描述: ").append(data.getString("desc")).append("\n").
        append("视频地址: ").append("https://www.bilibili.com/video/").append(data.getString("bvid")).append("\n").
        append("播放: ").append(stat.getInteger("view")).append(" 弹幕: ").append(stat.getInteger("danmaku")).append(" 评论: ").append(stat.getInteger("reply")).append("\n").
        append("收藏: ").append(stat.getInteger("favorite")).append(" 投币: ").append(stat.getInteger("coin")).append(" 分享: ").append(stat.getInteger("share")).append("\n").
        append("点赞: ").append(stat.getInteger("like")).append(" 点踩: ").append(stat.getInteger("dislike"));
        outMessage.setMessage(message.toString());

        r.sendMessage(outMessage);
    }

    private void initConfig() {
        // 加载配置文件
        Yaml yaml = new Yaml();
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream("config/avr-config.yml");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new InnerException("PLG-AvResolve", "读取配置文件失败", e.getLocalizedMessage());
        }
        Map<String, Object> map = yaml.load(inputStream);
        bilibili_api = (String) map.get("bilibili-api");
    }
}
