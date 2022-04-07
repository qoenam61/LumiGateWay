package com.example.device;

import android.util.Log;

import com.example.XaapiException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

public class DefaultSlaveDevice extends SlaveDevice {
    private static final String TAG = "DefaultSlaveDevice";

    public DefaultSlaveDevice(XiaomiGateway gateway, String sid, Type type) {
        super(gateway, sid, type);
    }

    DefaultSlaveDevice(XiaomiGateway gateway, String sid) {
        super(gateway, sid, Type.Others);
    }


    @Override
    void update(String data) {
        try {
            JsonObject o = JSON_PARSER.parse(data).getAsJsonObject();
            Log.d(TAG, "update: " + o.getAsString());

        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        }
    }
}
