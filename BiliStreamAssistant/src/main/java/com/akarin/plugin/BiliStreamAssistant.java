package com.akarin.plugin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.darcklh.louise.Config.LouiseConfig;
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

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author DarckLH
 * @date 2023/3/18 0:40
 * @Description
 */
public class  BiliStreamAssistant implements PluginService {

    public Logger log = LoggerFactory.getLogger(BiliStreamAssistant.class);
    private static OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(100, 30L, TimeUnit.MINUTES))
            // .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890)))
            .build();

    private R r = new R();
    private boolean plugin_on = false;

    // 用于存储所有被订阅的直播间和订阅用户
    private static Map<String, StreamRoom> stream_rooms = new HashMap<>();
    private static boolean biliAssInit = false;

    public static void main(String[] args) {
        BiliStreamAssistant ai = new BiliStreamAssistant();
//        LouiseConfig.BOT_ACCOUNT = "1655944518";
//        LouiseConfig.LOUISE_ADMIN_NUMBER = "412543224";
//        LouiseConfig.BOT_BASE_URL = "http://127.0.0.1:5700/";
//        InMessage in = new InMessage();
//        Sender sender = new Sender();
//        sender.setNickname("DarckLh");
//        sender.setUser_id((long) 412543224);
//        in.setUser_id((long) 412543224);
//        in.setGroup_id((long) 477960516);
//        in.setSender(sender);
//        in.setMessage_type("private");
//        in.setMessage("!Bili直播 监听 6655");
//        ai.service(in);
       ai.init();
    }

    @Override
    public String pluginName() {
        return null;
    }

    @Override
    public JSONObject service(InMessage inMessage) {

        OutMessage outMsg = new OutMessage(inMessage);
        // 处理命令 Bili直播 订阅|退订 room_id
        String room_id = inMessage.getMessage().substring(11);
        boolean valid = true;
        // 校验 room_id
        if (room_id.length() > 16) {
            outMsg.setMessage("直播间房号不合法，请重新订阅");
            valid = false;
        }

        if (!room_id.matches("^[0-9]*$")) {
            outMsg.setMessage("直播间房号不为全数字，请重新订阅");
            valid = false;
        }

        // 校验房间号是否有效
        Request req = new Request.Builder()
                .url("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=" + room_id)
                .get()
                .build();
        try {
            Response resp = HTTP_CLIENT.newCall(req).execute();
            JSONObject result = JSON.parseObject(resp.body().string());
            int num = result.getInteger("code");
            if (num != 0) {
                outMsg.setMessage("直播间房号是错误的，没有这个房间");
                valid = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        if (!valid) {
            r.sendMessage(outMsg);
            log.info("不合法的 room_id");
            return null;
        }

        switch (inMessage.getMessage().substring(8, 10)) {
            case "订阅": {
                // 从 map 中尝试取出房间
                StreamRoom room = stream_rooms.get(room_id);
                Long user_id = inMessage.getUser_id();
                Long group_id = inMessage.getGroup_id();
                // 取出空值则全新添加
                if (room == null) {
                    room = new StreamRoom();
                    room.is_listening = true;
                    room.user_ids.add(user_id);
                    log.info("用户 " + group_id + " 订阅 " + room_id);
                    if (group_id != -1) {
                        room.group_ids.add(group_id);
                        log.info("群组 " + group_id + " 订阅 " + room_id);
                    }
                // 如果取出值判断是否重复添加
                } else {
                    if (room.user_ids.contains(user_id)) {
                        log.info("用户 " + user_id + " 重复订阅" + room_id);
                    } else {
                        room.user_ids.add(user_id);
                        log.info("用户 " + user_id + " 订阅 " + room_id);
                    }

                    if (group_id != -1) {
                        if (room.group_ids.contains(group_id)) {
                            log.info("群组 " + group_id + " 重复订阅" + room_id);
                        } else {
                            room.group_ids.add(group_id);
                            log.info("群组 " + group_id + " 订阅 " + room_id);
                        }
                    }
                }
                stream_rooms.put(room_id, room);
                writeToFile();
                outMsg.setMessage("[CQ:at,qq=" + inMessage.getUser_id() + "]订阅成功");
                r.sendMessage(outMsg);
            } break;
            case "退订": {
                if(!stream_rooms.get(room_id).user_ids.remove(inMessage.getUser_id()) || stream_rooms.get(room_id) == null) {
                    outMsg.setMessage("[CQ:at,qq=" + inMessage.getUser_id() + "]退订失败，可能是因为直播间从未被订阅过或是直播间房号错误");
                } else {
                    outMsg.setMessage("[CQ:at,qq=" + inMessage.getUser_id() + "]退订成功");
                }
                writeToFile();
                r.sendMessage(outMsg);
            } break;
            case "监听": {
                log.info("启动直播间监听程序");
            } break;
            default: log.info("Unsupported operation");
        }
       return null;
    }

    @Override
    public JSONObject service() {
        String[] room_ids = {"9205174", "8777439", "4728179", "26998169", "25937512", "25686236", "93285", "13059717", "7358490"};
        for (String room_id: room_ids) {
            List<Long> group_ids = new ArrayList<>();
            List<Long> user_ids = new ArrayList<>();
            group_ids.add(726751510L);
            StreamRoom room = new StreamRoom();
            room.is_listening = true;
            room.group_ids = group_ids;
            room.user_ids = user_ids;
            room.status = 0;

            stream_rooms.put(room_id, room);
        }
        writeToFile();
        return null;
    }

    @Override
    public boolean init() {
        // 从文件读取监听列表
        readToMap();
        if (stream_rooms.size() != 0) {
            log.info("读取缓存成功，监听列表长度 " + stream_rooms.size());
            for (Map.Entry<String, StreamRoom> entry : stream_rooms.entrySet()) {
                log.info("房间: " + entry.getKey());
                log.info("    |_> 订阅群组: " + entry.getValue().group_ids);
                log.info("    |_> 订阅用户: " + entry.getValue().user_ids);
            }
        } else
            log.info("监听列表长度为 0");
        // 启动一个新线程来监听每个直播间的状态
        new Thread(() -> {
            while (true) {
                // 每 15s 查询一次
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for ( Map.Entry<String, StreamRoom> record : stream_rooms.entrySet()) {
                    String room_id_in = record.getKey();
                    StreamRoom room = record.getValue();
                    // 对 stream_rooms 里的所有直播间遍历 如果是置于监听状态的直播间则处理
                    if (record.getValue().is_listening) {
                        // 请求 B站直播相关 API 获取直播间状态
                        Request request = new Request.Builder()
                                .url("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=" + record.getKey())
                                .get()
                                .build();

                        try {
                            Response response = HTTP_CLIENT.newCall(request).execute();
                            JSONObject result = JSON.parseObject(response.body().string());
                            JSONObject data = result.getJSONObject("data");
                            int status = data.getInteger("live_status");
                            // 检测到从未开播到开播
                            if (status == 1 && room.status != 1) {
                                OutMessage outMessage = new OutMessage();
                                record.getValue().status = 1;
                                log.info("检测到直播间" + room_id_in + "上播");
                                log.info("将向以下用户通知: " + room.user_ids);
                                log.info("将向以下群组通知: " + room.group_ids);
                                for ( Long user_id: room.user_ids ) {
                                    outMessage.setMessage("你订阅的直播间" + room_id_in + "开播了"
                                            + "\n直播地址: https://live.bilibili.com/" + room_id_in
                                            + "\n标题: " + data.getString("title")
                                            + "\n描述: " + data.getString("description")
                                            + "[CQ:image,file=" + data.getString("keyframe") + "]");
                                    outMessage.setUser_id(user_id);
                                    outMessage.setGroup_id((long)-1);
                                    // 发送订阅信息私聊
                                    r.sendMessage(outMessage);
                                }

                                for ( Long group_id: room.group_ids) {
                                    outMessage.setMessage("群订阅的直播间" + room_id_in + "开播了"
                                            + "\n直播地址: https://live.bilibili.com/" + room_id_in
                                            + "\n标题: " + data.getString("title")
                                            + "\n描述: " + data.getString("description")
                                            + "[CQ:image,file=" + data.getString("keyframe") + "]");
                                    outMessage.setMessage_type("group");
                                    outMessage.setGroup_id(group_id);
                                    // 发送订阅信息群聊
                                    r.sendMessage(outMessage);
                                }
                            }
                            // 检测到下播
                            if (status == 0 && room.status != 0) {
                                OutMessage outMessage = new OutMessage();
                                record.getValue().status = 0;
                                log.info("检测到直播间" + room_id_in + "下播");
                                log.info("将向以下用户通知: " + room.user_ids);
                                log.info("将向以下群组通知: " + room.group_ids);
                                for ( Long user_id: room.user_ids ) {
                                    outMessage.setMessage("你订阅的直播间" + room_id_in + "下播了");
                                    outMessage.setUser_id(user_id);
                                    outMessage.setGroup_id((long)-1);
                                    r.sendMessage(outMessage);
                                }

                                for ( Long group_id: room.group_ids) {
                                    outMessage.setMessage("群订阅的直播间" + room_id_in + "下播了");
                                    outMessage.setGroup_id(group_id);
                                    outMessage.setMessage_type("group");
                                    // 发送订阅信息群聊
                                    r.sendMessage(outMessage);
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        }).start();
        return true;
    }

    @Override
    public boolean reload() {
        return false;
    }

    /**
     * 将监听列表写入文件
     */
    private void writeToFile() {
        try {
            // isDirExist();
            // 文件保存的本地路径
            String targetPath = "./cache/plugins/bili-stream-assistant/subscribe";
            File file = new File(targetPath);
            if (file.exists()) {
                FileOutputStream fs = new FileOutputStream(file);
                ObjectOutputStream os = new ObjectOutputStream(fs);
                os.writeObject(stream_rooms);
                os.close();
                fs.close();
            } else {
                log.info("打开 BiliStreamAssistant 缓存文件失败");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将监听列表读入内存
     */
    private void readToMap() {
        try {
            // isDirExist();
            // 文件保存的本地路径
            String targetPath = "./cache/plugins/bili-stream-assistant/subscribe";
            File file = new File(targetPath);
            if (file.exists()) {
                FileInputStream fs = new FileInputStream(file);
                ObjectInputStream is = new ObjectInputStream(fs);
                stream_rooms = (HashMap<String, StreamRoom>) is.readObject();
                is.close();
                fs.close();
            } else {
                log.info("打开 BiliStreamAssistant 缓存文件失败");
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void isDirExist() throws IOException {
        //判断目录是否存在
        String filePath = "./cache/plugins/bili-stream-assistant/";
        File folder = new File(filePath);

        if (!folder.exists() && !folder.isDirectory()) {
            folder.mkdirs();
            log.info("创建了 BiliStreamAssistant 缓存目录");
            // 文件保存的本地路径
            String targetPath = "./cache/plugins/bili-stream-assistant/subscribe";
            File file = new File(targetPath);
            if(file.createNewFile())
                log.info("BiliStreamAssistant 缓存文件创建成功");
            else
                log.info("BiliStreamAssistant 缓存文件已经存在");
        }
    }
}
