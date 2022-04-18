package com.example.command;

import com.example.device.SlaveDevice;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class WriteCommand implements ICommand {
    private SlaveDevice device;
    private JsonObject data;
    private String what;

    public WriteCommand(SlaveDevice device, JsonObject data, String key) {
        this.device = device;
        this.data = data;
        data.addProperty("key", key);
        what = "{{\"cmd\":\"write\", \"sid\":\""+ device.getSid() +"\", \"data\":" + data + "}}";
    }

    @Override
    public byte[] toBytes() {
        return what.getBytes(StandardCharsets.US_ASCII);
    }

    public String getString() {
        return what;
    }
}
