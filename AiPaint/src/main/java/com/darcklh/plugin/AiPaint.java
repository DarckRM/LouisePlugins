package com.darcklh.plugin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.darcklh.louise.Config.LouiseConfig;
import com.darcklh.louise.Controller.CqhttpWSController;
import com.darcklh.louise.Model.InnerException;
import com.darcklh.louise.Model.Messages.InMessage;
import com.darcklh.louise.Model.Messages.Node;
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

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static String url;
    private static String cookie;
    private static String user_agent;
    private static boolean is_thread_on = false;

    private static Map<String, Object> map;
    private static Map<String, BanUser> bannerMap = new HashMap<>();

    public static void main(String[] args) {
        AiPaint ai = new AiPaint();
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
        in.setMessage("!aipaint 1girl,cute,skirt");
        //in.setMessage("!aipaint blonde hair, loli");
        ai.service(in);
    }

    @Override
    public String pluginName() {
        return null;
    }

    @Override
    public JSONObject service(InMessage inMessage) {

        R r = new R();
        AtomicBoolean taskReady = new AtomicBoolean(false);
        // 每次请求的全局计数器
        AtomicInteger timer = new AtomicInteger();

        new Thread(() -> {
            // 声明一个新任务
            PaintTask task = new PaintTask();
            OutMessage outMsg = new OutMessage(inMessage);

            String[] arr = inMessage.getMessage().split(" ");
            String tag;
            if (arr.length == 1)
                tag = arr[0];
            else
                tag = arr[1];
            task.outMessage = outMsg;

            // 初始化配置文件
            initConfig(task);

            if (tag.equals("$help")) {
                String[] nsfw = map.get("nsfw").toString().split(",");
                StringBuilder builder = new StringBuilder();
                outMsg.setMessage("违禁词列表会动态更新，不确定的情况下先看一眼再用吧\n");
                r.sendMessage(outMsg);
                int line = 0;
                for (String word: nsfw) {
                    builder.append(word).append(" ,");
                    if (line == 10) {
                        line = 0;
                        outMsg.setMessage(builder.toString());
                        r.sendMessage(outMsg);
                        builder.delete(0, builder.length());
                    } else
                        line++;
                }
                return;
            }

            if (tag.contains("$")) {
                adminCommand(inMessage.getUser_id().toString(), outMsg, r, tag);
                return;
            }

            // 进入监听模式
            CqhttpWSController.startWatch(inMessage.getUser_id());

            int interval = 0;

            // 判断是否需要以图作图
            outMsg.setMessage("[CQ:at,qq=" + inMessage.getUser_id() + "]如果需要以图作图请回复 嗯");
            r.sendMessage(outMsg);

            try {
                while (interval < 5000) {
                    // 尝试从监听队列获取消息体
                    InMessage inMsg = CqhttpWSController.messageMap.get(inMessage.getUser_id());
                    if (inMsg != null) {
                        if (inMsg.getMessage().equals("嗯")) {
                            outMsg.setMessage("[CQ:at,qq=" + inMessage.getUser_id() + "]请在 15秒 内发送一张图片");
                            r.sendMessage(outMsg);
                            interval = 0;
                            // 清除原有的参数
                            CqhttpWSController.messageMap.remove(inMessage.getUser_id());
                            while (true) {
                                if (interval == 15000) {
                                    timer.set(-1);
                                    outMsg.setMessage("[CQ:at,qq=" + inMessage.getUser_id() + "]你太久没有理露易丝，已经忘记画图了");
                                    r.sendMessage(outMsg);
                                    return;
                                }
                                // 尝试从监听队列获取消息体
                                InMessage imgMsg = CqhttpWSController.messageMap.get(inMessage.getUser_id());
                                if (imgMsg != null) {
                                    inMessage.setMessage(inMessage.getMessage() + " " + imgMsg.getMessage());
                                    break;
                                }
                                Thread.sleep(1000);
                                interval += 1000;
                            }
                            break;
                        }
                    }
                    Thread.sleep(1000);
                    interval += 1000;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                // 监听计数器减少，移除多余消息
                CqhttpWSController.stopWatch(inMessage.getUser_id());
            }

            // 校验参数
            String[] params = parseParams(inMessage.getMessage() + "[CQ:image,file=d3cc538fffe266d06fa1f0005168f9ce.image,subType=0,url=https://gchat.qpic.cn/gchatpic_new/412543224/798823950-2595967398-D3CC538FFFE266D06FA1F0005168F9CE/0?term=2&amp;is_origin=0]", inMessage, r);
            if (params == null)
                return;

            if (params.length == 5) {
                task.image = params[1];
                task.original_image = params[2];
                task.setHeight(Integer.parseInt(params[3]));
                task.setWidth(Integer.parseInt(params[4]));
            }
            task.prompt = map.get("prompt") + params[0];
            task.uc = (String) map.get("negative-prompt");
            task.inMessage = inMessage;

            log.info("AiPaint 任务参数: " + task.prompt);

            try {
                // 队列最大允许 20 个任务排队
                if (paintQueue.size() > 8) {
                    throw new ReplyException("AI 已经被塞满了");
                } else {
                    paintQueue.put(task);
                    outMsg.setMessage("[CQ:at,qq=" + inMessage.getUser_id() + "]绘画请求已加入队列，当前队列长度: " + paintQueue.size());
                    taskReady.set(true);
                    r.sendMessage(outMsg);
                }
            } catch (InterruptedException e) {
                throw new ReplyException("添加作画任务失败了");
            }

        }, UniqueGenerator.uniqueThreadName("AIP", "Waiting Img")).start();

        // 成功添加任务后执行
        while(!taskReady.get()) {
            if (timer.get() == -1) {
                return null;
            }
            if (timer.get() == 25) {
                OutMessage outMsg = new OutMessage(inMessage);
                outMsg.setMessage("[CQ:at,qq=" + inMessage.getUser_id() + "]添加画图任务超时，请检查参数或询问管理员");
                r.sendMessage(outMsg);
                return null;
            }
            timer.getAndIncrement();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        taskReady.set(false);
        // 如果队列中已经存在 2 个以上的任务则停止开启线程
        if (is_thread_on)
            return null;
        // startNaifu(cookie, user_agent, url, r);
        is_thread_on = true;
        startStable(r);
        return null;
    }

    @Override
    public JSONObject service() {
        return null;
    }

    private void adminCommand(String user_id, OutMessage outMsg, R r, String tag) {
        // 如果不是管理员则不允许使用以下命令
        if (!user_id.equals(LouiseConfig.LOUISE_ADMIN_NUMBER)) {
            outMsg.setMessage("非法的参数请求");
            r.sendMessage(outMsg);
            return;
        }

        // 预留指令 直接开启任务线程
        if (tag.equals("$go")) {
            // startNaifu(cookie, user_agent, url, r);
            if (paintQueue.size() == 0) {
                outMsg.setMessage("当前没有需要开启的任务");
                r.sendMessage(outMsg);
                return;
            }
            outMsg.setMessage("当前队列中有 " + paintQueue.size() + " 任务待开启");
            r.sendMessage(outMsg);
            startStable(r);
            return;
        }
        // 预留指令 输出所有封禁用户
        if (tag.equals("$banList")) {
            if (map.size() == 0) {
                outMsg.setMessage("当前没有被封禁的用户");
                r.sendMessage(outMsg);
                return;
            }
            int number = 0;
            StringBuilder builder = new StringBuilder();
            builder.append("⚠ 封禁用户列表 ⚠\n");
            for (Map.Entry<String, BanUser> user: bannerMap.entrySet()) {
                long restTime = user.getValue().getRelease_date() - System.currentTimeMillis();
                builder.append(number).append(" - 还剩 ").append(user.getKey()).append(restTime / 1000).append(" 秒解封\n");
            }
            outMsg.setMessage(builder.toString());
            r.sendMessage(outMsg);
            return;
        }
        // 预留指令 输出当前任务队列信息
        if (tag.equals("$taskList")) {
            if (paintQueue.size() == 0) {
                outMsg.setMessage("当前队列没有任何任务");
                r.sendMessage(outMsg);
                return;
            }
            int number = 0;
            StringBuilder builder = new StringBuilder();
            for (PaintTask task: paintQueue) {
                builder.append(number).append(" - 用户 ").append(task.getInMessage().getUser_id()).append(" 的任务\n");
            }
            outMsg.setMessage(builder.toString());
            r.sendMessage(outMsg);
        }
    }

    private void startNaifu(String cookie, String user_agent, String url, R r) {
        new Thread(() -> {
            Request.Builder builder = new Request.Builder()
                    .addHeader("Cookie", cookie)
                    .addHeader("User-Agent", user_agent);

            while (paintQueue.size() > 0) {
                log.info("AiPaint 任务队列长度: " + paintQueue.size());
                PaintTask inTask = paintQueue.peek();
                inTask.outMessage.setMessage("开始作画任务，当前任务队列长度: " + paintQueue.size());
                // 开始时间
                long start_time = System.currentTimeMillis();
                r.sendMessage(inTask.outMessage);
                if (inTask.image != null) {
                    inTask.setNoise((Double) map.get("noise"));
                    inTask.setStrength((Double) map.get("strength"));
                    inTask.setSteps(50);
                }
//                log.info(inTask.toString());
                RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, inTask.toString());
                Request request = builder.url(url).post(body).build();
                try {
                    log.info("AiPaint 尝试请求 API: " + url);
                    Response response = HTTP_CLIENT.newCall(request).execute();
                    // 判断状态码，如果获取不到图片重试三次
                    boolean result = false;
                    if (response.code() != 200) {
                        OutMessage out = new OutMessage(inTask.inMessage);
                        if (response.code() == 204) {
                            out.setMessage("AI 维护中");
                            r.sendMessage(out);
                            throw new ReplyException("AI 维护中");
                        }
                        if (response.code() == 404) {
                            out.setMessage("AI 现在不可用，当 AI 恢复可用时会按顺序处理任务");
                            r.sendMessage(out);
                            return;
                        }
                        if (response.code() != 200) {
                            out.setMessage("画图 API 异常，请检查 cookie 是否正确");
                            r.sendMessage(out);
                            throw new ReplyException("画图 API 异常，请检查 cookie 是否正确");
                        }
                    } else
                        result = true;
                    if (!result) {
                        throw new ReplyException("AI 忙不过来了，先让它歇一会儿吧~ (30s 后尝试)");
                    }

                    String imgData = response.body().string().split("\\r?\\n")[2].substring(5);

                    //TODO ai图片下载到本地
                    long end_time = System.currentTimeMillis();
                    long cost_time = (end_time - start_time) / 1000;
                    paintQueue.remove();
                    OutMessage out = new OutMessage(inTask.inMessage);

                    // 封装返回消息 需要分别处理 imgToImg 和 textToImg
                    String headPart = "[CQ:at,qq=" + out.getUser_id() + "]这是你召唤的图片哦";
                    String bodyPart;
                    if (inTask.image != null)
                        bodyPart = imgToImg(inTask);
                    else
                        bodyPart = textToImg(inTask);
                    String restPart = "\n耗时: " + cost_time + " 秒" +
                            "\n[CQ:image,file=base64://" + imgData +
                            "]\n剩下还有 " + paintQueue.size() + " 个任务";
                    out.setMessage(headPart + bodyPart + restPart);
                    r.sendMessage(out);
                } catch (Exception e) {
                    paintQueue.remove();
                    if (e instanceof ReplyException) {
                        throw (ReplyException) e;
                    }
                    throw new InnerException("PLG-AiPaint", "遇到内部错误", e.getMessage());
                }
            }
        }, UniqueGenerator.uniqueThreadName("AIN", "Naifu")).start();
    }

    private void startStable(R r) {
        new Thread(() -> {
            // 构造请求头
            Request.Builder builder = new Request.Builder();
            // 如果队列任务未处理完则一直处理
            while (paintQueue.size() > 0) {
                log.info("AiPaint 任务队列长度: " + paintQueue.size());
                // 从队列中取出一个任务
                PaintTask inTask = paintQueue.peek();
                log.info("开始用户 " + inTask.getInMessage().getUser_id() + " 的作画任务");
                // inTask.outMessage.setMessage("开始作画任务，当前任务队列长度: " + paintQueue.size());
                // r.sendMessage(inTask.outMessage);
                // 开始时间
                long start_time = System.currentTimeMillis();
                // 判断任务类型，根据不同类型构造不同 Body
                ArrayList<Object> array;
                JSONObject inBody = new JSONObject();
                if (inTask.getImage() != null) {
                    inTask.setNoise((Double) map.get("noise"));
                    inTask.setStrength((Double) map.get("strength"));
                    array = getStableDiffImageBody(inTask);
                    inBody.put("fn_index", map.get("img_fn"));
                }
                else {
                    array = getStableDiffTextBody(inTask);
                    inBody.put("fn_index", map.get("text_fn"));
                }
                inBody.put("data", array);
                inBody.put("session_hash", map.get("session"));
                // (inBody.toString());
                RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, inBody.toString());
                Request request = builder.url(url).post(body).build();
                // 请求 AI
                Response response = requestAIApi(request, r, inTask);

                JSONObject result = new JSONObject();
                try {
                    result = JSON.parseObject(response.body().string());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                JSONArray data = result.getJSONArray("data");
                String imgData = map.get("app-file-api") + data.getJSONArray(0).getJSONObject(0).getString("name");

                //TODO ai图片下载到本地
                long end_time = System.currentTimeMillis();
                long cost_time = (end_time - start_time) / 1000;
                OutMessage out = new OutMessage(inTask.inMessage);

                // 封装返回消息 需要分别处理 imgToImg 和 textToImg
                String headPart = out.getSender().getNickname() + "，这是你召唤的图片哦";
                String bodyPart;
                if (inTask.image != null)
                    bodyPart = imgToImg(inTask);
                else
                    bodyPart = textToImg(inTask);
                String restPart = "\n耗时: " + cost_time + " 秒" +
                        "\n[CQ:image,file=" + imgData + "]" +
                        "\n剩下还有 " + (paintQueue.size() - 1) + " 个任务";
                String nodeMsg = headPart + bodyPart + restPart;
                if (out.getGroup_id() == -1)
                    out.setMessage(nodeMsg);
                else {
                    ArrayList<Node> nodeList = new ArrayList<>();
                    nodeList.add(new Node(nodeMsg, Long.parseLong(LouiseConfig.BOT_ACCOUNT)));
                    out.setMessages(nodeList);
                }
                r.sendMessage(out);
                // 执行完任务移除一个任务
                paintQueue.remove();

                // 适当缓冲
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            is_thread_on = false;
        }, UniqueGenerator.uniqueThreadName("AIS", "Stable")).start();
    }

    private Response requestAIApi(Request request, R r, PaintTask inTask) {
        Response response;
        try {
            log.info("AiPaint 尝试请求 API: " + url);
            response = HTTP_CLIENT.newCall(request).execute();
            // 判断状态码，如果获取不到图片重试三次
            boolean result = false;
            if (response.code() != 200) {
                OutMessage out = new OutMessage(inTask.inMessage);
                if (response.code() == 204) {
                    out.setMessage("AI 维护中");
                    r.sendMessage(out);
                    is_thread_on = false;
                    throw new ReplyException("AI 维护中");
                }
                if (response.code() == 404) {
                    out.setMessage("AI 现在不可用，当 AI 恢复可用时会按顺序处理任务");
                    r.sendMessage(out);
                    is_thread_on = false;
                    return null;
                }
                if (response.code() != 200) {
                    out.setMessage("画图 API 异常，请检查 cookie 是否正确");
                    r.sendMessage(out);
                    is_thread_on = false;
                    throw new ReplyException("画图 API 异常，请检查 cookie 是否正确");
                }
            } else
                result = true;
            if (!result) {
                is_thread_on = false;
                throw new ReplyException("AI 忙不过来了，先让它歇一会儿吧~ (30s 后尝试)");
            }
        } catch (Exception e) {
            paintQueue.remove();
            if (e instanceof ReplyException) {
                throw (ReplyException) e;
            }
            is_thread_on = false;
            throw new InnerException("PLG-AiPaint", "遇到内部错误", e.getMessage());
        }
        return response;
    }

    private String[] parseParams(String message, InMessage inMsg, R r) {
        // 删除所有换行符
        message = message.replaceAll("\n", "").replaceAll("\r", "");
        // 群聊过滤 NSFW 关键词
        boolean canGO = true;
        if (inMsg.getGroup_id() != -1)
         canGO = promptFilter(message, inMsg);

        if (!canGO)
            return null;

        String[] params = message.split("!aipaint ");
        if (params.length < 2) {
            OutMessage outMsg = new OutMessage(inMsg);
            outMsg.setMessage("AI 画图需要指定参数哦，请在命令空格后加上参数或在最后贴上图片");
            r.sendMessage(outMsg);
            return null;
        }
        String param = params[1];
        // 存在图片则开启 img-img 模式
        if (param.contains("[CQ:image")) {
            String img_url = param.substring(param.indexOf("url=") + 4, param.length() - 1);
            String original_img = img_url;
            // 图片转为 Base64 编码
            InputStream in = null;
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            Integer[] hAw = {512, 768};
            // byte[] data = null;
            try {
                // 创建URL
                URL url = new URL(img_url);
                // 创建链接
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10 * 1000);
                in = conn.getInputStream();
                InputStream imageIn = in;
                // data = in.readAllBytes();
                // 获取 Image 对象 获取长宽比信息
                BufferedImage image = ImageIO.read(imageIn);
                hAw = adjustScale(image.getHeight(), image.getWidth());
                ImageIO.write(image, "jpg", stream);
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Base64.Encoder encoder = Base64.getEncoder();
            img_url = encoder.encodeToString(stream.toByteArray());

            // 取出剩下的 prompt 参数
            String[] args = new String[5];
            args[0] = param.substring(0, param.indexOf("[CQ"));
            args[1] = img_url;
            args[2] = original_img;
            args[3] = hAw[0].toString();
            args[4] = hAw[1].toString();
            return args;
        } else {
            String[] args = new String[1];
            args[0] = param;
            return args;
        }
    }

    private void initConfig(PaintTask task) {
        // 加载配置文件
        Yaml yaml = new Yaml();
        InputStream inputStream;
        try {
            inputStream = new FileInputStream("aipaint-config.yml");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new InnerException("PLG-AiPaint", "读取配置文件失败", e.getLocalizedMessage());
        }
        map = yaml.load(inputStream);

        url = (String) map.get("app-api");
        cookie = (String) map.get("cookie");
        user_agent = (String) map.get("user-agent");

        // 初始各种参数
        task.width = (Integer) map.get("width");
        task.height = (Integer) map.get("height");
        task.scale = (Integer) map.get("scale");
        task.sampler = (String) map.get("sampler");
        task.steps = (Integer) map.get("steps");
        task.setImg_to_steps((Integer) map.get("img_to_steps"));
        task.n_samples = (Integer) map.get("n_samples");
        task.ucPreset = (Integer) map.get("ucPreset");
        task.seed = new Random().nextInt(9999);
    }

    private String textToImg(PaintTask inTask) {
        return "\n采样次数: " + inTask.getSteps() +
                "\n参数: " + inTask.getPrompt() +
                "\n种子: " + inTask.getSeed();
    }

    private String imgToImg(PaintTask inTask) {
        return "\n采样次数: " + inTask.getSteps() +
                "\n参数: " + inTask.getPrompt() +
                "\n扰动: " + inTask.getNoise() +
                "\n强度: " + inTask.getStrength() +
                "\n种子: " + inTask.getSeed() +
                "\n[CQ:image,file="  + inTask.original_image + "]";
    }

    private Integer[] adjustScale(int height, int width) {
        // 三种类型
        float offset = height - width;
        Integer[] heightAndWidth = {768, 512};
        float scale = (float) height / width;
        if (offset < 0) {
            heightAndWidth[0] = 512;
            heightAndWidth[1] = 768;
        }
        if (scale > 0.8 && scale < 1.2) {
            heightAndWidth[0] = 512;
            heightAndWidth[1] = 512;
        }
        return heightAndWidth;
    }

    private ArrayList<Object> getStableDiffTextBody(PaintTask task) {
        ArrayList<Object> array = new ArrayList<>();
        array.add(task.prompt);
        array.add(task.uc);
        array.add("None");
        array.add("None");
        array.add(task.getSteps());
        array.add("Euler a");
        array.add(false);
        array.add(false);
        array.add(1);
        array.add(1);
        array.add(task.getScale());
        array.add(-1);
        array.add(-1);
        array.add(0);
        array.add(0);
        array.add(0);
        array.add(false);
        array.add(task.getHeight());
        array.add(task.getWidth());
        array.add(false);
        array.add(0.7);
        array.add(task.getHeight());
        array.add(task.getWidth());
        array.add("None");
        array.add(false);
        array.add(false);
        array.add(false);
        // array.add(null);
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

    private ArrayList<Object> getStableDiffImageBody(PaintTask task) {
        ArrayList<Object> array = new ArrayList<>();
        array.add(0);
        array.add(task.getPrompt());
        array.add(task.getUc());
        array.add("None");
        array.add("None");
        array.add("data:image/jpeg;base64," + task.getImage());
        array.add(null);
        array.add(null);
        array.add(null);
        array.add("Draw mask");
        array.add(task.getImg_to_steps());
        array.add("Euler a");
        array.add(4);
        array.add("original");
        array.add(false);
        array.add(false);
        array.add(1);
        array.add(1);
        array.add(task.getScale());
        array.add(task.getStrength());
        array.add(-1);
        array.add(-1);
        array.add(0);
        array.add(0);
        array.add(0);
        array.add(false);
        array.add(task.getHeight());
        array.add(task.getWidth());
        array.add("Just resize");
        array.add(false);
        array.add(32);
        array.add("Inpaint masked");
        array.add("");
        array.add("");
        array.add("None");
        array.add("");
        array.add(true);
        array.add(true);
        array.add("");
        array.add("");
        array.add(true);
        array.add(50);
        array.add(true);
        array.add(1);
        array.add(0);
        array.add(false);
        array.add(4);
        array.add(1);
        array.add("");
        array.add(128);
        array.add(8);
        String[] direction = {"left", "right", "up", "down"};
        array.add(direction);
        array.add(1);
        array.add(0.05);
        array.add(128);
        array.add(4);
        array.add("fill");
        String[] another_direction = {"left", "right", "up", "down"};
        array.add(another_direction);
        array.add(false);
        array.add(false);
        array.add(false);
        // array.add(null);
        array.add("");
        array.add("");
        array.add(64);
        array.add("None");
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

    // 如果存在 NSFW 相关关键词则返回失败结果
    private boolean promptFilter(String prompt, InMessage inMsg) {
        String[] nsfw_words = map.get("nsfw").toString().split(",");
        String user_id = inMsg.getUser_id().toString();
        R r = new R();
        OutMessage outMsg = new OutMessage(inMsg);
        // 尝试从 BanList 中获取用户
        BanUser user = bannerMap.get(user_id);
        if (user == null) {
            user = new BanUser();
            user.setUser_id(user_id);
            user.setBaned_times(0);
        } else {
            // 如果成功从列表中获取用户 先判断是否可以解封 通过则进入一般过审流程
            if (user.getBaned_times() > 10) {
                outMsg.setMessage("[CQ:at,qq=" + user.getUser_id() + "]你已经违规请求过多次数");
                return false;
            }

            long offsetTime = user.getRelease_date() - System.currentTimeMillis();
            if ( offsetTime > 0) {
                outMsg.setMessage("还有 " + offsetTime / 1000 + " 秒才能使用 AI 画图功能");
                r.sendMessage(outMsg);
                return false;
            }
        }

        for (String words: nsfw_words) {
            if (prompt.contains(words)) {

                // 用户触发了保护机制 加入 bannerList
                user.setBaned_times(user.getBaned_times() + 1);
                // 获取当前时间戳
                long time = System.currentTimeMillis();
                // 随着用户触发保护机制的次数越多 惩罚时间越高
                double multiple = (long) Math.pow(2, user.getBaned_times());
                time += multiple * 30000;
                user.setRelease_date(time);

                outMsg.setMessage(words + " 违禁,AI 功能将暂时对你禁用\r\n" +
                "禁用次数:" + user.getBaned_times() + "\r\n" +
                "恢复时间: " + (time - System.currentTimeMillis()) / 1000 + " 秒后\r\n" +
                "主账号已经被封禁,保护机制是为了有效延续 bot 的生命,输入!aipaint $help以获取违禁词列表");
                bannerMap.put(user.getUser_id(), user);
                r.sendMessage(outMsg);
                return false;
            }
        }
        return true;
    }

}
