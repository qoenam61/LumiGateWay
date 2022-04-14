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
    private HashMap<SubscriptionToken, Runnable> motionCallbacks = new HashMap<>();

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
                        notifyWithMotion();
                        break;
                    case "close":
                        lastAction = Action.Close;
                        notifyWithMotion();
                        break;
                    default:
                        Log.d(TAG, "Unexpected action: " + action);
//                        throw new XaapiException("Unexpected action: " + action);
                }
                notifyWithAction(action);
            }
        }/* catch (XaapiException e) {
            e.printStackTrace();
        } */catch (JsonSyntaxException e) {
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

    public SubscriptionToken subscribeForMotion(Runnable callback) {
        SubscriptionToken token = new SubscriptionToken();
        motionCallbacks.put(token, callback);
        return token;
    }

    public void unsubscribeForMotion(SubscriptionToken token) {
        motionCallbacks.remove(token);
    }

    private void notifyWithMotion() {
        Iterator<SubscriptionToken> iterator = motionCallbacks.keySet().iterator();
        while (iterator.hasNext()) {
            SubscriptionToken token = iterator.next();
            Objects.requireNonNull(motionCallbacks.get(token)).run();
        }
    }
}
