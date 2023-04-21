package com.darcklh.plugin;

import com.alibaba.fastjson.JSONObject;
import com.darcklh.louise.Api.FileControlApi;
import com.darcklh.louise.Config.LouiseConfig;
import com.darcklh.louise.Model.*;
import com.darcklh.louise.Model.Messages.*;
import com.darcklh.louise.Service.PluginService;
import com.darcklh.louise.Utils.LouiseProxy;
import com.darcklh.louise.Utils.OkHttpUtils;
import com.darcklh.louise.Utils.YamlReader;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author DarckLH
 * @date 2022/10/25 23:36
 * @Description 可以接受 PID 然后利用 Nginx 反向代理请求 Pixiv
 */
public class PixivProxy implements PluginService {

    private Logger log = LoggerFactory.getLogger(PixivProxy.class);

    private String pixiv_url;
    private String reverse_proxy_url;
    private String cookie;
    private String user_agent;

    private final FileControlApi fileControlApi = new FileControlApi();

    public static void main (String[] args) {
        LouiseConfig.BOT_ACCOUNT = "1655944518";
        LouiseConfig.BOT_BASE_URL = "http://127.0.0.1:5700/";
        PixivProxy pp = new PixivProxy();
        InMessage in = new InMessage();
        Sender sender = new Sender();
        sender.setUser_id((long) 412543224);
        in.setUser_id((long) 412543224);
        in.setMessage_type("private");
        in.setMessage("!pid 78286152");
        pp.init();
        pp.service(in);
    }

    public String pluginName() {
        return null;
    }

    public JSONObject service(InMessage inMessage) {

        // 处理参数
        Message message = Message.build(inMessage);
        String[] params = parseParams(inMessage.getMessage(), message);
        String pid = params[1];


        // 请求 pixiv 获取 pid 对应作品的 json 信息
        JSONObject body;
        try {
            log.info("开始请求 pixiv 获取作品 " + pid + " 对应信息");
            Proxy proxy = LouiseProxy.get();
//            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 7890));
            OkHttpUtils okHttpUtils;
            if (proxy != null)
                okHttpUtils = OkHttpUtils.builder(proxy);
            else
                okHttpUtils = OkHttpUtils.builder();
            Response response = okHttpUtils
                    .addHeader("cookie", cookie)
                    .addHeader("user-agent", user_agent)
                    .url(pixiv_url + pid)
                    .get()
                    .async(true);
            JSONObject result = JSONObject.parseObject(response.body().string());

            if (result.getBoolean("error")) {
                message.reply().text("请求 pixiv 时遇到了未知的问题\n" + result.getString("message")).send();
                return null;
            }
            body = result.getJSONObject("body");
        } catch (Exception e) {
            log.warn("请求 pixiv 遇到异常: " + e.getMessage());
            message.reply().text("请求 pixiv 时遇到了未知的问题").send();
            return null;
        }
        Integer pageCount = body.getInteger("pageCount");
        String illustTitle = body.getString("illustTitle");
        String alt = body.getString("alt");
        // 从作品子列表中获取完整信息
        JSONObject subInfo = body.getJSONObject("userIllusts").getJSONObject(pid);
        String original = body.getJSONObject("urls").getString("original").replaceAll("i.pximg.net","pixiv.rmdarck.icu");

        // 如果不指定页数参数则默认处理，若指定页数则修改 url
        int page = 0;
        if ( params.length == 3) {
            page = Integer.parseInt(params[2]) - 1;
            original = original.replaceAll("\\_p\\d", "_p" + page);
        }
        String suffix = original.substring(original.length() - 4);
        String fileName = pid + "_p" + page + suffix;

        fileControlApi.downloadPicture_RestTemplate(original, fileName, "Pixiv");

        message.node(Node.build().reply().text("作品名: " + illustTitle +
                        "\n作者名: " + alt +
                        "\n共有: " + pageCount + " 页"))
                .node(Node.build().image(LouiseConfig.BOT_LOUISE_CACHE_IMAGE + "Pixiv/" + fileName));

        // 如果是多页数作品，则携带输出剩余页数
        if (pageCount > 1) {
            StringBuilder urlBuilder = new StringBuilder();
            Node previewImage = Node.build().text("图集预览\n");
            while(pageCount > 0) {
                previewImage.image(original.replaceAll("\\_p\\d", "_p" + (pageCount - 1)));
                pageCount--;
            }
            message.node(previewImage);
        }
        message.send();
        return null;
    }

    public JSONObject service() {
        return null;
    }

    public boolean init() {
        // 加载配置文件
        YamlReader yaml = new YamlReader("./config/pxy-config.yml");
        Map<String, Object> map = yaml.getProperties();

        pixiv_url = (String) map.get("pixiv_url");
        reverse_proxy_url = (String) map.get("reverse_proxy_url");
        cookie = (String) map.get("cookie");
        user_agent = (String) map.get("user_agent");

        return true;
    }

    public boolean reload() {
        return true;
    }

    private String[] parseParams(String message, Message out) {

        // 去除特殊字符
        message = message.replaceAll("\u200B", "");
        // 按空格区分参数
        String[] params = message.split(" ");
        if (params.length < 2)
            out.reply().text("pixiv 功能需要指定 pid 哦").fall();
        // 校验 PID 以及页数参数
        String page = "0";
        // 如果存在第三位参数则是页数
        if (params.length == 3)
            page = params[2];
        String pid = params[1];

        Pattern pattern = Pattern.compile("[0-9]*");
        Matcher isNum = pattern.matcher(pid);
        Matcher isPageNum = pattern.matcher(page);

        if (!isNum.matches() || !isPageNum.matches())
            out.reply().text("pid 和页数必须要全数字哦").fall();
        return params;
    }

}
