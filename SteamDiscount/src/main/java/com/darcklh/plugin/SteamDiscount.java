package com.darcklh.plugin;

import com.alibaba.fastjson.JSONObject;
import com.darcklh.louise.Config.LouiseConfig;
import com.darcklh.louise.Model.Messages.InMessage;
import com.darcklh.louise.Model.Messages.Node;
import com.darcklh.louise.Model.Messages.OutMessage;
import com.darcklh.louise.Model.R;
import com.darcklh.louise.Model.ReplyException;
import com.darcklh.louise.Model.Sender;
import com.darcklh.louise.Service.PluginService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * @author DarckLH
 * @date 2022/10/23 2:19
 * @Description 获取 Steam 的折扣信息
 */
public class SteamDiscount implements PluginService {

    public static void main(String[] args) throws Exception {
        LouiseConfig.BOT_ACCOUNT = "1655944518";
        LouiseConfig.BOT_BASE_URL = "http://127.0.0.1:5700/";
        SteamDiscount sd = new SteamDiscount();
        InMessage in = new InMessage();
        Sender sender = new Sender();
        sender.setUser_id((long) 412543224);
        in.setUser_id((long) 412543224);
        in.setMessage_type("private");
        in.setMessage("!steamdb");
        sd.service(in);
    }

    @Override
    public String pluginName() {
        return null;
    }

    @Override
    public JSONObject service(InMessage inMessage) {
        R r = new R();
        Document doc = null;
        try {
            doc = Jsoup.connect("https://www.yxdzqb.com/index_popular.html").get();
        } catch (IOException e) {
            e.printStackTrace();
            throw new ReplyException("获取 Steam 折扣信息失败");
        }
        Element tables = doc.selectFirst("table");
        Element queryInfo = tables.child(0);
        String title = queryInfo.selectFirst("b").text();
        String update_date = queryInfo.select("span").get(1).text();
        Elements gameList = tables.child(1).select(".bg-none");
        OutMessage out = new OutMessage(inMessage);
        ArrayList<Node> nodeArrayList = new ArrayList<>();
        Node node = new Node("Steam " + title + "   " +update_date, Long.parseLong(LouiseConfig.BOT_ACCOUNT));
        nodeArrayList.add(node);
        int number = 0;
        for ( Element game : gameList ) {
            if ( number > 9)
                break;
            String game_name_en = game.selectFirst("span").text();
            String game_name_ch = game.select("span").get(1).text();

            Element score_table = game.select("table").get(1);
//            String steam_icon = score_table.select("img").get(2).attr("src");
//            String steam_icon = "https://www.yxdzqb.com/Others/Steam_icon_small.png";
            String steam_score = score_table.select("b").get(1).text();
            String game_thumbnail = score_table.select(".img_in_tab").attr("src");

            Elements price_infos = score_table.select(".cell_price");
            String discount = price_infos.get(0).text();
            String price = price_infos.get(0).select("b").text();
            String is_lowest = price_infos.get(1).select("font").text();

            String message = "[CQ:image,file=" + game_thumbnail + "]" +
                    "\n游戏名称(EN): " + game_name_en +
                    "\n游戏名称(CN): " + game_name_ch +
                    "\nSteam评分: " + steam_score +
                    "\n折扣: " + discount +
                    "\n现价: " + price +
                    "\n" + is_lowest +
                    "\n<------------- ^w^ ------------->";
            Node infoNode = new Node(message, Long.parseLong(LouiseConfig.BOT_ACCOUNT));
            nodeArrayList.add(infoNode);
            number++;
        }
        out.setMessages(nodeArrayList);
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
}
