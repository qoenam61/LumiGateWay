package com.example.command;

import com.example.device.SlaveDevice;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;

public class WriteCommand implements ICommand {
    private SlaveDevice device;
    private JsonObject data;

    public WriteCommand(SlaveDevice device, JsonObject data, String key) {
        this.device = device;
        this.data = data;
        data.addProperty("key", key);
    }

    @Override
    public byte[] toBytes() {
        String what = "{{\"cmd\":\"write\", \"sid\":\""+ device.getSid() +"\", \"data\":" + data + "}}";
        return what.getBytes(StandardCharsets.US_ASCII);
    }
}
