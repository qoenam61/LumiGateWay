package com.example.device;

import android.util.Log;

import com.example.XaapiException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class XiaomiDoorWindowSensor extends SlaveDevice implements IInteractiveDevice {
    private static final String TAG = "XiaomiDoorWindowSensor";

    public enum Action {
        Open,
        Close
    }

    private Action lastAction;
    private HashMap<SubscriptionToken, Consumer<String>> actionsCallbacks = new HashMap<>();

    XiaomiDoorWindowSensor(XiaomiGateway gateway, String sid) {
        super(gateway, sid, Type.XiaomiDoorWindowSensor);
    }

    @Override
    void update(String data) {
        try {
            JsonObject o = JSON_PARSER.parse(data).getAsJsonObject();
            if (o.has("status")) {
                String action = o.get("status").getAsString();
                switch(action) {
                    case "open":
                        lastAction = Action.Open;
                        break;
                    case "close":
                        lastAction = Action.Close;
                        break;
                    default:
                        Log.d(TAG, "Unexpected action: " + action);
//                        throw new XaapiException("Unexpected action: " + action);
                }
                notifyWithAction(action);
            }
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Map<SubscriptionToken, Consumer<String>> getActionsCallbacks() {
        return actionsCallbacks;
    }

    public Action getLastAction() {
        return lastAction;
    }
}
