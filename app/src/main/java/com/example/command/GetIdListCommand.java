package com.example.command;

import java.nio.charset.StandardCharsets;

public class GetIdListCommand implements ICommand {
    public GetIdListCommand() {}

    String cmd = "{\"cmd\":\"get_id_list\"}";

    @Override
    public byte[] toBytes() {
        return  cmd.getBytes(StandardCharsets.US_ASCII);
    }

    public String getCmdString() {
        return cmd;
    }
}
