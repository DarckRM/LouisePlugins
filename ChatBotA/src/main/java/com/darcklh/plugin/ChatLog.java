package com.darcklh.plugin;

import java.util.ArrayList;

/**
 * @author DarckLH
 * @date 2023/4/2 10:56
 * @Description 用户和 ChatBot 的对话记录
 */
public class ChatLog {
    private int chat_id;
    private long user_id;
    private int chat_length;
    private ArrayList<Chat> chat_logs;

    public long getUser_id() {
        return user_id;
    }

    public void setUser_id(long user_id) {
        this.user_id = user_id;
    }

    public int getChat_length() {
        return chat_length;
    }

    public void setChat_length(int chat_length) {
        this.chat_length = chat_length;
    }

    public ArrayList<Chat> getChat_logs() {
        return chat_logs;
    }

    public void setChat_logs(ArrayList<Chat> chat_logs) {
        this.chat_logs = chat_logs;
    }

    public int getChat_id() {
        return chat_id;
    }

    public void setChat_id(int chat_id) {
        this.chat_id = chat_id;
    }
}
