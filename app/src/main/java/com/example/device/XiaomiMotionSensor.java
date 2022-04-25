package com.example.device;

import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class XiaomiMotionSensor extends SlaveDevice implements IInteractiveDevice {
    private final static String TAG = "XiaomiMotionSensor";

    public enum Action {
        Unknown, Motion
    }

    private Action lastAction;
    private HashMap<SubscriptionToken, Consumer<String>> actionsCallbacks = new HashMap<>();
    private HashMap<SubscriptionToken, Runnable> motionCallbacks = new HashMap<>();

    XiaomiMotionSensor(XiaomiGateway gateway, String sid) {
        super(gateway, sid, Type.XiaomiMotionSensor);
    }

    XiaomiMotionSensor(XiaomiGateway gateway, String sid, short shortId) {
        super(gateway, sid, Type.XiaomiMotionSensor, shortId);
    }

    @Override
    void update(String data) {
        try {
            JsonObject o = JSON_PARSER.parse(data).getAsJsonObject();
            if (o.has("status")) {
                String action = o.get("status").getAsString();
                Log.d(TAG, "update - data: " + data + " action: " + action);
                switch(action) {
                    case "motion":
                        lastAction = Action.Motion;
                        notifyWithMotion();
                        notifyWithAction(action);
                    default:
                        Thread.sleep(1000);
                        Log.d(TAG, "update - Unknown action: " + action);
                        lastAction = Action.Unknown;
                        notifyWithMotion();
                        action = "clear";
                        notifyWithAction(action);
                }
            }
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void executeProfile() {

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
