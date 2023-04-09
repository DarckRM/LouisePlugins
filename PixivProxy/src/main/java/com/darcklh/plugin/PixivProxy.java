package com.darcklh.plugin;

import com.alibaba.fastjson.JSONObject;
import com.darcklh.louise.Config.LouiseConfig;
import com.darcklh.louise.Model.*;
import com.darcklh.louise.Model.Messages.InMessage;
import com.darcklh.louise.Model.Messages.OutMessage;
import com.darcklh.louise.Service.PluginService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

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
    private Integer proxy_port;
    private String proxy_url;

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
    private static OkHttpClient HTTP_CLIENT = null;

    R r = new R();

    public static void main (String[] args) {
        LouiseConfig.BOT_ACCOUNT = "1655944518";
        LouiseConfig.BOT_BASE_URL = "http://127.0.0.1:5700/";
        PixivProxy pp = new PixivProxy();
        InMessage in = new InMessage();
        Sender sender = new Sender();
        sender.setUser_id((long) 412543224);
        in.setUser_id((long) 412543224);
        in.setMessage_type("private");
        in.setMessage("!pid 104032208");

        pp.service(in);
    }

    @Override
    public String pluginName() {
        return null;
    }

    @Override
    public JSONObject service(InMessage inMessage) {

        // 读取配置文件
        initConfig();

        // 处理参数
        OutMessage out = new OutMessage(inMessage);
        String[] params = parseParams(inMessage.getMessage(), out);
        String pid = params[1];

        // RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, "");
        Request.Builder builder = new Request.Builder();
        Request request = builder.url(pixiv_url + pid).get().build();

        // 请求 pixiv 获取 pid 对应作品的 json 信息
        JSONObject body;
        try {
            log.info("开始请求 pixiv 获取作品 " + pid + " 对应信息");
            Response response = HTTP_CLIENT.newCall(request).execute();
            JSONObject result = JSONObject.parseObject(response.body().string());

            if (result.getBoolean("error")) {
                out.setMessage("请求 pixiv 时遇到了未知的问题\n" + result.getString("message"));
                r.sendMessage(out);
                return null;
            }
            body = result.getJSONObject("body");
        } catch (Exception e) {
            log.warn("请求 pixiv 遇到异常: " + e.getMessage());
            out.setMessage("请求 pixiv 时遇到了未知的问题");
            r.sendMessage(out);
            return null;
        }
        Integer pageCount = body.getInteger("pageCount");
        String illustTitle = body.getString("illustTitle");
        String alt = body.getString("alt");
        // 从作品子列表中获取完整信息
        JSONObject subInfo = body.getJSONObject("userIllusts").getJSONObject(pid);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd/HH/mm/ss");
        String formattedDate = sdf.format(subInfo.getDate("updateDate"));
        String urls = reverse_proxy_url + "img-original/img/" + formattedDate + "/" + pid + "_p0.jpg";
        String small = body.getJSONObject("urls").getString("small");
        // 如果不指定页数参数则默认处理，若指定页数则修改 url
        if ( params.length == 3) {
            int page = Integer.parseInt(params[2]) - 1;
            urls = urls.replaceAll("\\_p\\d", "_p" + page);
        }

        String r_proxy_url = urls;
        String small_r_proxy_url = urls.replaceAll(".jpg", "_master1200.jpg").replaceAll("img-original", "img-master");

        String basicMsg = "[CQ:at,qq=" + out.getUser_id() + "] 这是你的请求结果" +
                "\n作品名: " + illustTitle +
                "\n作者名: " + alt +
                "\n共有: " + pageCount + " 页" +
                "\n[CQ:image,file=" + r_proxy_url + "]";
        // 如果是多页数作品，则携带输出剩余页数
        if (pageCount > 1) {
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("\n图集预览\n");
            while(pageCount > 0) {
                urlBuilder.append("[CQ:image,file=").append(small_r_proxy_url.replaceAll("\\_p\\d", "_p" + (pageCount - 1))).append("]");
                pageCount--;
            }
            basicMsg += urlBuilder;
        }
        out.setMessage(basicMsg);
        r.sendMessage(out);
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

    private String[] parseParams(String message, OutMessage out) {

        // 去除特殊字符
        message = message.replaceAll("\u200B", "");
        // 按空格区分参数
        String[] params = message.split(" ");
        if (params.length < 2) {
            out.setMessage("pid 功能需要指定 pid 哦");
            r.sendMessage(out);
            throw new InnerException("PLG-PixivProxy", "缺少参数 PID", "");
        }


        // 校验 PID 以及页数参数
        String page = "0";
        // 如果存在第三位参数则是页数
        if (params.length == 3)
            page = params[2];
        String pid = params[1];

        Pattern pattern = Pattern.compile("[0-9]*");
        Matcher isNum = pattern.matcher(pid);
        Matcher isPageNum = pattern.matcher(page);

        if (!isNum.matches() || !isPageNum.matches()) {
            out.setMessage("pid 和页数必须要全数字哦");
            r.sendMessage(out);
            throw new InnerException("PLG-PixivProxy", "参数格式不正确", "");
        }
        return params;
    }

    /**
     * 初始化 yaml 配置文件
     */
    private void initConfig() {
        // 加载配置文件
        Yaml yaml = new Yaml();
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream("pxy-config.yml");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new InnerException("PLG-PixivProxy", "读取配置文件失败", e.getLocalizedMessage());
        }
        Map<String, Object> map = yaml.load(inputStream);

        pixiv_url = (String) map.get("pixiv_url");
        reverse_proxy_url = (String) map.get("reverse_proxy_url");
        proxy_url = (String) map.get("proxy_url");
        proxy_port = (Integer) map.get("proxy_port");

        // 判断是否需要代理
        if (proxy_port > 0) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10809));
            HTTP_CLIENT = new OkHttpClient.Builder()
                    .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(300, TimeUnit.SECONDS)
                    .connectionPool(new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION, TimeUnit.MINUTES))
                    .proxy(proxy)
                    .build();
        } else {
            HTTP_CLIENT = new OkHttpClient.Builder()
                    .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(300, TimeUnit.SECONDS)
                    .connectionPool(new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION, TimeUnit.MINUTES))
                    .build();
        }


    }
}
