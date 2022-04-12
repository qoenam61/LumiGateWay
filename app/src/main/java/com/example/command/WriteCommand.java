package com.example.command;

import com.example.device.SlaveDevice;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;

public class WriteCommand implements ICommand {
    private SlaveDevice device;
    private JsonObject data;
    private String what;

    public WriteCommand(SlaveDevice device, JsonObject data, String key) {
        this.device = device;
        this.data = data;
        what = "{{\"cmd\":\"write\", \"sid\":\""+ device.getSid() +"\", \"data\":" + data + "}}";
        data.addProperty("key", key);
    }

    @Override
    public byte[] toBytes() {
        return what.getBytes(StandardCharsets.US_ASCII);
    }

    public String getString() {
        return what;
    }
}
