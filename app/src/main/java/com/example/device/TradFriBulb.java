package com.example.device;

import android.util.Log;

import com.example.XaapiException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class TradFriBulb extends SlaveDevice implements IInteractiveDevice {
    private static final String TAG = "TradFriBulb";

    public enum Action {
        On,
        Off,
        Unknown // probably device is offline
    }

    private XiaomiSocket.Action lastAction;
    private HashMap<SubscriptionToken, Consumer<String>> actionsCallbacks = new HashMap<>();

    private String mBrightness;

    public TradFriBulb(XiaomiGateway gateway, String sid, Type type) {
        super(gateway, sid, Type.TradFriBulb);
    }

    public TradFriBulb(XiaomiGateway gateway, String sid, short shortId) {
        super(gateway, sid, Type.TradFriBulb, shortId);
    }

    public TradFriBulb(XiaomiGateway gateway, String sid, Type type, short shortId) {
        super(gateway, sid, Type.TradFriBulb, shortId);
    }

    @Override
    void update(String data) {
        try {
            JsonObject o = JSON_PARSER.parse(data).getAsJsonObject();
            if (o.has("status")) {
                String action = o.get("status").getAsString();
                switch(action) {
                    case "on":
                        lastAction = XiaomiSocket.Action.On;
                        break;
                    case "off":
                        lastAction = XiaomiSocket.Action.Off;
                        break;
                    case "unknown":
                        lastAction = XiaomiSocket.Action.Unknown;
                        break;
                    default:
                        throw new XaapiException("Unknown action: " + action);
                }
                notifyWithAction(action);
            } else if (o.has("brightness")) {
                String brightness = o.get("brightness").getAsString();
                mBrightness = brightness;
            }
        } catch (XaapiException e) {
            e.printStackTrace();
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void executeProfile() {
        Log.d(TAG, "executeProfile: ");
        new Thread(() -> {
            try {
                turnOn();

                Thread.sleep(3000);

                int brightness = 100;
                while (true) {
                    if (brightness <= 20) {
                        break;
                    }
                    brightness -= 20;
                    changeBrightness(brightness);
                    Thread.sleep(1000);

                }
            } catch (XaapiException | InterruptedException e) {
                Log.e(TAG, "onBind: ", e);
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public Map<SubscriptionToken, Consumer<String>> getActionsCallbacks() {
        return actionsCallbacks;
    }

    public XiaomiSocket.Action getLastAction() {
        return lastAction;
    }

    public void turnOn() throws XaapiException {
        JsonObject on = new JsonObject();
        on.addProperty("status", "on");
        gateway.sendDataToDevice(this, on);
    }

    public void turnOff() throws XaapiException {
        JsonObject off = new JsonObject();
        off.addProperty("status", "off");
        gateway.sendDataToDevice(this, off);
    }

    public void changeBrightness(int brightness) throws XaapiException {
        JsonObject bright = new JsonObject();
        bright.addProperty("brightness", String.valueOf(brightness));
        gateway.sendDataToDevice(this, bright);
    }
}
