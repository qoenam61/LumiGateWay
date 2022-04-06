package com.example.command;

import java.nio.charset.StandardCharsets;

public class WhoisCommand implements ICommand {

    private String whois = "{\"cmd\":\"whois\"}";

    @Override
    public byte[] toBytes() {
        return new String(whois).getBytes(StandardCharsets.US_ASCII);
    }

    public String getString() {
        return whois;
    }
}
