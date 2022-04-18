package com.example.command;

import java.nio.charset.StandardCharsets;

public class ReadCommand implements ICommand {
    private String sid;
    private String read;

    public ReadCommand(String sid) {
        this.sid = sid;
        read = "{\"cmd\":\"read\", \"sid\":\""+ sid +"\"}";
    }

    @Override
    public byte[] toBytes() {
        return read.getBytes(StandardCharsets.US_ASCII);
    }

    public String getString() {
        return read;
    }
}
