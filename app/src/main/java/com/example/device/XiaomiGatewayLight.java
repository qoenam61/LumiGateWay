package com.example.device;

import android.graphics.Color;

import com.example.XaapiException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.function.Consumer;

public class XiaomiGatewayLight extends BuiltinDevice {

    private byte brightness;
    private byte previousNonZeroBrightness = 100;
    private int color = Color.BLACK; // TODO decide if this is an appropriate default value
    private int colorR = Color.RED; // TODO decide if this is an appropriate default value
    private int colorG = Color.GREEN; // TODO decide if this is an appropriate default value
    private int colorB = Color.BLUE; // TODO decide if this is an appropriate default value

    private HashMap<IInteractiveDevice.SubscriptionToken, Consumer<Byte>> brightnessCallbacks = new HashMap<>();
    private HashMap<IInteractiveDevice.SubscriptionToken, Consumer<Integer>> colorCallbacks = new HashMap<>();

    public XiaomiGatewayLight(XiaomiGateway gateway) {
        super(gateway, Type.XiaomiGatewayLight);
    }

    @Override
    void update(String data) {
        try {
            JsonObject o = JSON_PARSER.parse(data).getAsJsonObject();
            int rgb = o.get("rgb").getAsInt();
            byte previousBrightnessValue = brightness;
            brightness = (byte)(rgb >>> 24);
            int previousColorValue = color;
            color = rgb & 0x00FFFFFF;
            if(brightness != previousBrightnessValue) {
                notifyWithBrightnessChange(brightness);
            }
            if(color != previousColorValue) {
                notifyWithColorChange(color);
            }
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        }
    }

    public boolean getOn() {
        return getBrightness() > 0;
    }

    public byte getBrightness() {
        return brightness; // TODO query from device
    }

    public Color getColor() {
        return null; // TODO query from device
//        return color; // TODO query from device
    }

    public IInteractiveDevice.SubscriptionToken subscribeForBrightnessChange(Consumer<Byte> callback) {
        IInteractiveDevice.SubscriptionToken token = new IInteractiveDevice.SubscriptionToken();
        brightnessCallbacks.put(token, callback);
        return token;
    }

    public void unsubscribeForBrightnessChange(IInteractiveDevice.SubscriptionToken token) {
        brightnessCallbacks.remove(token);
    }

    private void notifyWithBrightnessChange(byte value) {
        for(Consumer<Byte> c : brightnessCallbacks.values()) {
            c.accept(value);
        }
    }

    public IInteractiveDevice.SubscriptionToken subscribeForColorChange(Consumer<Integer> callback) {
        IInteractiveDevice.SubscriptionToken token = new IInteractiveDevice.SubscriptionToken();
        colorCallbacks.put(token, callback);
        return token;
    }

    public void unsubscribeForColorChange(IInteractiveDevice.SubscriptionToken token) {
        colorCallbacks.remove(token);
    }

    private void notifyWithColorChange(int value) {
        for(Consumer<Integer> c : colorCallbacks.values()) {
            c.accept(value);
        }
    }

    public void setOn(boolean on) throws XaapiException {
        if(on) {
            setBrightness(previousNonZeroBrightness, true);
        } else {
            setBrightness((byte)0, true);
        }
    }

    public void setBrightness(byte brightness, boolean send) throws XaapiException {
        if (send) {
            writeBrightnessAndColor(brightness, this.color);
        }
        if(this.brightness != 0) {
            previousNonZeroBrightness = this.brightness;
        }
        this.brightness = brightness;
//        setColor(0x00FFFF); // BLUE
    }

    public void setColor(int r, int g, int b) throws XaapiException {
        int rColor = r == -1 ? colorR : r;
        int gColor = g == -1 ? colorG : g;
        int bColor = b == -1 ? colorB : b;
        colorR = rColor;
        colorG = gColor;
        colorB = bColor;

        color = Color.rgb(colorR, colorG, colorB);
        writeBrightnessAndColor(this.brightness, color);
    }

    public void setColor(int color) {
        colorR = Color.red(color);
        colorG = Color.green(color);
        colorB = Color.blue(color);
    }

    private void writeBrightnessAndColor(byte brightness, int color) throws XaapiException {
        // TODO verify brightness in range 0..100
        JsonObject rgb = new JsonObject();
//        int rgbValue = brightness << 24;
        int rgbValue = brightness << 24 | color;
        rgb.addProperty("rgb", rgbValue);
        gateway.sendDataToDevice(this, rgb);
    }
}
