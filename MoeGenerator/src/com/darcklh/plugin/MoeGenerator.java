package com.darcklh.plugin;

import com.alibaba.fastjson.JSONObject;
import com.darcklh.louise.Config.LouiseConfig;
import com.darcklh.louise.Model.*;
import com.darcklh.louise.Model.Messages.InMessage;
import com.darcklh.louise.Model.Messages.OutMessage;
import com.darcklh.louise.Model.Saito.SysConfig;
import com.darcklh.louise.Service.Impl.SysConfigImpl;
import com.darcklh.louise.Service.PluginService;
import com.darcklh.louise.Service.SysConfigService;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

/**
 * @author DarckLH
 * @date 2022/9/14 3:25
 * @Description
 */
public class MoeGenerator implements PluginService {

    public static void main(String[] args) {
        MoeGenerator moe = new MoeGenerator();
        InMessage inMessage = new InMessage();
        inMessage.setUser_id((long)412543224);
        inMessage.setMessage("!moevoice paimon_dusk p 792873704 947082838");
        moe.service(inMessage);
    }

    @Override
    public String pluginName() {
        return null;
    }

    @Override
    public JSONObject service(InMessage inMessage) {
        // !moevoice [voice file] [p private]/[g group] "numbers..."
        // 第一位参数代表音频文件名 第二位指定群发或是私发 第三位是QQ号数组
        String[] args = inMessage.getMessage().split(" ");
        if (args.length < 4)
            throw new InnerException("PLG-MoeGenerator", "参数数量不正确，请确保参数是这类格式: !moevoice [voice file] p private]/[g group] \"numbers...\"", "");
        if (!args[2].equals("p") && !args[2].equals("g"))
            throw new InnerException("PLG-MoeGenerator", "参数格式不正确，请正确的指定群聊或是私聊 群聊: g，私聊: p", "");
        OutMessage out = new OutMessage();
        R r = new R();
        String fileName = args[1];

        for (int i = 3; i < args.length; i++) {
            if (args[2].equals("p"))
                out.setUser_id(Long.parseLong(args[i]));
            else
                out.setGroup_id(Long.parseLong(args[i]));
            out.setMessage("[CQ:record,file=" + LouiseConfig.BOT_CACHE_LOCATION + "voice/" + fileName + ".wav]");
            r.sendMessage(out);
        }
        return null;
    }

    @Override
    public JSONObject service() {
        return null;
    }
}
