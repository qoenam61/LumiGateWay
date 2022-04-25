package com.example.device;

import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.widget.CompoundButton;

import com.example.XaapiException;
import com.example.lumigateway.R;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.kyleduo.switchbutton.SwitchButton;

import java.util.HashMap;
import java.util.function.Consumer;

import static com.example.device.XiaomiMotionSensor.MOTION_TIME;

public class XiaomiGatewayLight extends BuiltinDevice {
    private static final String TAG = "XiaomiGatewayLight";

    private byte brightness;
    private byte previousNonZeroBrightness = 100;
    private int color = Color.BLACK; // TODO decide if this is an appropriate default value
    private int colorR = Color.RED; // TODO decide if this is an appropriate default value
    private int colorG = Color.GREEN; // TODO decide if this is an appropriate default value
    private int colorB = Color.BLUE; // TODO decide if this is an appropriate default value

    private HashMap<IInteractiveDevice.SubscriptionToken, Consumer<Byte>> brightnessCallbacks = new HashMap<>();
    private HashMap<IInteractiveDevice.SubscriptionToken, Consumer<Integer>> colorCallbacks = new HashMap<>();

    private Activity mActivity;
    private boolean mAutoProfileCheck;

    public XiaomiGatewayLight(XiaomiGateway gateway) {
        super(gateway, Type.XiaomiGatewayLight);
        mActivity = gateway.getActivity();
        SwitchButton button = mActivity.findViewById(R.id.button_auto_profile_for_gateway);
        button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mAutoProfileCheck = isChecked;
            }
        });
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
        int rgbValue = brightness << 24 | color;
        rgb.addProperty("rgb", rgbValue);
        gateway.sendDataToDevice(this, rgb);
    }

    public void executeProfile(boolean motion) {
        if (!mAutoProfileCheck) {
            return;
        }
        new Thread(() -> {
            int i = 0;
            while (i < 1) {
                byte brightness = (byte) (Math.random() * 100);
                int r = (int) (Math.random() * 255);
                int g = (int) (Math.random() * 255);
                int b = (int) (Math.random() * 255);

                try {

                    if (motion) {
                        writeBrightnessAndColor(brightness, Color.rgb(r, g, b));
                    } else {
                        setBrightness((byte)0, true);
                    }
                    Thread.sleep(MOTION_TIME);
                } catch (XaapiException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                i++;
            }
        }).start();


    }
}
