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
//    private Color color = Color.BLACK; // TODO decide if this is an appropriate default value

    private HashMap<IInteractiveDevice.SubscriptionToken, Consumer<Byte>> brightnessCallbacks = new HashMap<>();
    private HashMap<IInteractiveDevice.SubscriptionToken, Consumer<Color>> colorCallbacks = new HashMap<>();

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
//            Color previousColorValue = color;
//            color = new Color(rgb & 0x00FFFFFF);
//            if(brightness != previousBrightnessValue) {
//                notifyWithBrightnessChange(brightness);
//            }
//            if(! color.equals(previousColorValue)) {
//                notifyWithColorChange(color);
//            }
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

    public IInteractiveDevice.SubscriptionToken subscribeForColorChange(Consumer<Color> callback) {
        IInteractiveDevice.SubscriptionToken token = new IInteractiveDevice.SubscriptionToken();
        colorCallbacks.put(token, callback);
        return token;
    }

    public void unsubscribeForColorChange(IInteractiveDevice.SubscriptionToken token) {
        colorCallbacks.remove(token);
    }

    private void notifyWithColorChange(Color value) {
        for(Consumer<Color> c : colorCallbacks.values()) {
            c.accept(value);
        }
    }

    public void setOn(boolean on) throws XaapiException {
        if(on) {
            setBrightness(previousNonZeroBrightness);
        } else {
            setBrightness((byte)0);
        }
    }

    public void setBrightness(byte brightness) throws XaapiException {
//        writeBrightnessAndColor(brightness, this.color);
        if(this.brightness != 0) {
            previousNonZeroBrightness = this.brightness;
        }
        this.brightness = brightness;
    }

    public void setColor(Color color) throws XaapiException {
        writeBrightnessAndColor(this.brightness, color);
//        this.color = color;
    }

    private void writeBrightnessAndColor(byte brightness, Color color) throws XaapiException {
        // TODO verify brightness in range 0..100
        JsonObject rgb = new JsonObject();
        int rgbValue = brightness << 24;
//        int rgbValue = brightness << 24 | color.getRGB();
        rgb.addProperty("rgb", rgbValue);
        gateway.sendDataToDevice(this, rgb);
    }
}
