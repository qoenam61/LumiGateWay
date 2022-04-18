package com.example.command;

import com.example.device.XiaomiGateway;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;

public class WriteSelfCommand implements ICommand {
    private XiaomiGateway gateway;
    private JsonObject data;
    String what;

    public WriteSelfCommand(XiaomiGateway gateway, JsonObject data, String key) {
        this.gateway = gateway;
        this.data = data;
        data.addProperty("key", key);
        what = "{{\"cmd\":\"write\", \"sid\":\""+ gateway.getSid() +"\", \"data\":" + data + "}}";
    }

    @Override
    public byte[] toBytes() {
        return what.getBytes(StandardCharsets.US_ASCII);
    }

    public String getString() {
        return what;
    }
}
