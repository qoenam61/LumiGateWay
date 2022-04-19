package com.example.device;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.XaapiException;
import com.example.channel.DirectChannel;
import com.example.channel.IncomingMulticastChannel;
import com.example.command.GetIdListCommand;
import com.example.command.ReadCommand;
import com.example.command.WhoisCommand;
import com.example.command.WriteCommand;
import com.example.command.WriteSelfCommand;
import com.example.lumigateway.R;
import com.example.reply.GatewayHeartbeat;
import com.example.reply.GetIdListReply;
import com.example.reply.ReadReply;
import com.example.reply.Reply;
import com.example.reply.Report;
import com.example.reply.SlaveDeviceHeartbeat;
import com.example.reply.WhoisReply;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kyleduo.switchbutton.SwitchButton;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static com.example.lumigateway.HubMainActivity.DISMISS_PROGRESS_DIALOG;
import static com.example.lumigateway.HubMainActivity.SHOW_PROGRESS_DIALOG;

public class XiaomiGateway {
    private static final String TAG = "XiaomiGateway";
    private static final String GROUP = "224.0.0.50";
    private static final int PORT = 9898;
    private static final int PORT_DISCOVERY = 4321;
    private static final byte[] IV =
        {     0x17, (byte)0x99, 0x6d, 0x09, 0x3d, 0x28, (byte)0xdd, (byte)0xb3,
        (byte)0xba,       0x69, 0x5a, 0x2e, 0x6f, 0x58,       0x56,       0x2e};

    private static final Gson GSON = new Gson();

    private static Handler mHandler;
    private static Activity mActivity;

    private String sid;
    private Optional<String> key = Optional.empty();
    private Cipher cipher;
    private IncomingMulticastChannel incomingMulticastChannel;
    private DirectChannel directChannel;
    private XiaomiGatewayLight builtinLight;
    private XiaomiGatewayIlluminationSensor builtinIlluminationSensor;
    private Map<String, SlaveDevice> knownDevices = new HashMap<>();
    private boolean continueReceivingUpdates;
    private WhoisReply mWhoisReply;
    private String mPassword;

    private boolean TEST_FOR_APP_DEV = true;

    private boolean mCipherComplete = false;
    private XiaomiSocket mTestXiaomiSocket;
    private XiaomiMotionSensor mTestXiaomiMotionSensor;
    private XiaomiDoorWindowSensor mTestXiaomiDoorSensor;
    private TradFriBulb mTestTradfriBulb;

    private ProgressDialog mProgress;

    public interface onFoundSubDevice {
        void onSubDevice(String sid, SlaveDevice deviceInfo);
    }

    private onFoundSubDevice mSubDeviceListener;

    public static XiaomiGateway discover() throws IOException, XaapiException {
        // TODO discover more than one gateway
        DirectChannel discoveryChannel = new DirectChannel(GROUP, PORT_DISCOVERY);
        WhoisCommand cmd = new WhoisCommand();
        Log.d(TAG, "discover - sending ... " + cmd.getString());
        discoveryChannel.send(cmd.toBytes());
        Log.d(TAG, "discover - replying ... ");
        String replyString = new String(discoveryChannel.receive());
        WhoisReply reply = GSON.fromJson(replyString, WhoisReply.class);
        Log.d(TAG, "discover - reply model: " + reply.model + " ip: " + reply.ip + " port: " + reply.port);
        if(Integer.parseInt(reply.port) != PORT) {
            throw new XaapiException("Gateway occupies unexpected port: " + reply.port);
        }

        return new XiaomiGateway(reply);
    }

    public static XiaomiGateway discover(Activity activity, Handler handler, onFoundSubDevice listener) throws IOException, XaapiException {
        // TODO discover more than one gateway
        mActivity = activity;
        mHandler = handler;

        DirectChannel discoveryChannel = new DirectChannel(GROUP, PORT_DISCOVERY);
        WhoisCommand cmd = new WhoisCommand();
        Log.d(TAG, "discover - sending ... " + cmd.getString());
        discoveryChannel.send(cmd.toBytes());
        Log.d(TAG, "discover - replying ... ");
        String replyString = new String(discoveryChannel.receive());
        WhoisReply reply = GSON.fromJson(replyString, WhoisReply.class);
        Log.d(TAG, "discover - reply model: " + reply.model + " ip: " + reply.ip + " port: " + reply.port);
        if(Integer.parseInt(reply.port) != PORT) {
            throw new XaapiException("Gateway occupies unexpected port: " + reply.port);
        }

        return new XiaomiGateway(reply, listener);
    }

    public static XiaomiGateway discover(onFoundSubDevice listener, String password) throws IOException, XaapiException {
        // TODO discover more than one gateway
        DirectChannel discoveryChannel = new DirectChannel(GROUP, PORT_DISCOVERY);
        WhoisCommand cmd = new WhoisCommand();
        Log.d(TAG, "discover - sending ... " + cmd.getString());
        discoveryChannel.send(cmd.toBytes());
        Log.d(TAG, "discover - replying ... ");
        String replyString = new String(discoveryChannel.receive());
        WhoisReply reply = GSON.fromJson(replyString, WhoisReply.class);
        Log.d(TAG, "discover - reply model: " + reply.model + " ip: " + reply.ip + " port: " + reply.port);
        if(Integer.parseInt(reply.port) != PORT) {
            throw new XaapiException("Gateway occupies unexpected port: " + reply.port);
        }

        return new XiaomiGateway(reply, listener, password);
    }

    public XiaomiGateway(WhoisReply reply) throws IOException, XaapiException{
        this.mWhoisReply = reply;
        this.incomingMulticastChannel = new IncomingMulticastChannel(GROUP, PORT);
        this.directChannel = new DirectChannel(reply.ip, PORT);
        Log.d(TAG, "XiaomiGateway: queryDevices");
        queryDevices();
        Log.d(TAG, "XiaomiGateway: configureBuiltinDevices");
        configureBuiltinDevices();
    }

    public XiaomiGateway(WhoisReply reply, onFoundSubDevice listener) throws IOException, XaapiException{
        mCipherComplete = false;
        mSubDeviceListener = listener;
        this.mWhoisReply = reply;
        this.incomingMulticastChannel = new IncomingMulticastChannel(GROUP, PORT);
        this.directChannel = new DirectChannel(reply.ip, PORT);
        Log.d(TAG, "XiaomiGateway: queryDevices");
        queryDevices();
        Log.d(TAG, "XiaomiGateway: configureBuiltinDevices");
        configureBuiltinDevices();
    }

    public XiaomiGateway(WhoisReply reply, onFoundSubDevice listener, String password) throws IOException, XaapiException{
        mCipherComplete = false;
        mSubDeviceListener = listener;
        this.mWhoisReply = reply;
        this.incomingMulticastChannel = new IncomingMulticastChannel(GROUP, PORT);
        this.directChannel = new DirectChannel(reply.ip, PORT);
        Log.d(TAG, "XiaomiGateway: queryDevices");
        queryDevices();
        Log.d(TAG, "XiaomiGateway: configureBuiltinDevices");
        configureBuiltinDevices();
        configureCipher(password);
        mPassword = password;
    }

    public XiaomiGateway(String ip) throws IOException, XaapiException {
        this.incomingMulticastChannel = new IncomingMulticastChannel(GROUP, PORT);
        this.directChannel = new DirectChannel(ip, PORT);
        queryDevices();
        configureBuiltinDevices();
    }
    public XiaomiGateway(String ip, String password) throws IOException, XaapiException {
        this(ip);
        configureCipher(password);
    }

    public void configurePassword(String password) throws XaapiException {
        if (!password.equals(mPassword)) {
            configureCipher(password);
        } else {
            Log.d(TAG, "configurePassword: same password");
        }
    }

    public void createTestDevice() throws InterruptedException {
        //TEST CODE
        Log.d(TAG, "[TEST_FOR_APP_DEV] queryDevices: testXiaomiSocket");
        SlaveDevice device1 = testXiaomiSocket("11111111", (short) 1111);
        knownDevices.put("11111111", device1);
        mSubDeviceListener.onSubDevice("11111111", device1);

        SlaveDevice device2 = testXiaomiMotionSensor("22222222", (short) 2222);
        knownDevices.put("22222222", device2);
        mSubDeviceListener.onSubDevice("22222222", device2);

        SlaveDevice device3 = testXiaomiDoorSensor("33333333", (short) 3333);
        knownDevices.put("33333333", device3);
        mSubDeviceListener.onSubDevice("33333333", device3);

        SlaveDevice device5 = testTradfriBulb("44444444", (short) 5555);
        knownDevices.put("44444444", device5);
        mSubDeviceListener.onSubDevice("44444444", device5);

        new Thread(() -> {
            try {
                Thread.sleep(10000);

                Thread.sleep(5000);
                getDevice("22222222").update("{\"status\":\"motion\"}");

                Thread.sleep(5000);
                getDevice("11111111").update("{\"status\":\"off\"}");

                Thread.sleep(5000);
                getDevice("11111111").update("{\"status\":\"on\"}");

                Thread.sleep(5000);
                getDevice("33333333").update("{\"status\":\"close\"}");

                Thread.sleep(5000);
                getDevice("33333333").update("{\"status\":\"open\"}");

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public String getPassword() {
        return mPassword;
    }

    public Map<String, SlaveDevice> getKnownDevices() {
        return knownDevices;
    }

    public void setOnSubDevicesListener(onFoundSubDevice listener) {
        mSubDeviceListener = listener;
    }

    private void configureBuiltinDevices() {
        builtinLight = new XiaomiGatewayLight(this);
        builtinIlluminationSensor = new XiaomiGatewayIlluminationSensor(this);

        View deviceGateway = mActivity.findViewById(R.id.gateway);
        SeekBar brightness = deviceGateway.findViewById(R.id.seekbar_brightness);
        SeekBar colorR = deviceGateway.findViewById(R.id.seekbar_color_r);
        SeekBar colorG = deviceGateway.findViewById(R.id.seekbar_color_g);
        SeekBar colorB = deviceGateway.findViewById(R.id.seekbar_color_b);

        builtinLight.subscribeForBrightnessChange(new Consumer<Byte>() {
            @Override
            public void accept(Byte aByte) {
                try {
                    Log.d(TAG, "subscribeForBrightnessChange accept: " + aByte);
                    builtinLight.setBrightness(aByte, false);
                    brightness.setProgress(aByte);
                } catch (XaapiException e) {
                    e.printStackTrace();
                }
            }
        });

        builtinLight.subscribeForColorChange(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) {
                builtinLight.setColor(integer);
                int r = Color.red(integer);
                int g = Color.green(integer);
                int b = Color.blue(integer);
                colorR.setProgress(r);
                colorG.setProgress(g);
                colorB.setProgress(b);
            }
        });
    }

    private void configureCipher(String password) throws XaapiException {
        try {
            mCipherComplete = false;
            Log.d(TAG, "configureCipher - start: " + password);
            cipher = Cipher.getInstance("AES/CBC/NoPadding");
            final SecretKeySpec keySpec = new SecretKeySpec(password.getBytes(), "AES");
            final IvParameterSpec ivSpec = new IvParameterSpec(IV);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            Log.d(TAG, "configureCipher - end: " + password);
            mCipherComplete = true;
        } catch (NoSuchAlgorithmException e) {
            throw new XaapiException("Cipher error: " + e.getMessage());
        } catch (NoSuchPaddingException e) {
            throw new XaapiException("Cipher error: " + e.getMessage());
        } catch (InvalidAlgorithmParameterException e) {
            throw new XaapiException("Cipher error: " + e.getMessage());
        } catch (InvalidKeyException e) {
            throw new XaapiException("Cipher error: " + e.getMessage());
        }
    }

    private void queryDevices() throws XaapiException {
        try {

            mHandler.sendEmptyMessage(SHOW_PROGRESS_DIALOG);

            GetIdListCommand queryDeviceString = new GetIdListCommand();
            Log.d(TAG, "queryDevices - sending ... : " + queryDeviceString.getCmdString());
            directChannel.send(queryDeviceString.toBytes());
            Log.d(TAG, "queryDevices - receiving ... : ");
            String replyString = new String(directChannel.receive());
            Log.d(TAG, "queryDevices - received : " + replyString);

            GetIdListReply reply = GSON.fromJson(replyString, GetIdListReply.class);
            sid = reply.sid;
            for(String sid : GSON.fromJson(reply.data, String[].class)) {
                SlaveDevice device = readDevice(sid);
                knownDevices.put(sid, device);
                mSubDeviceListener.onSubDevice(sid, device);
            }

            readDevice(sid);

            if (TEST_FOR_APP_DEV) {
                createTestDevice();
            }
        } catch (IOException | InterruptedException e) {
            throw new XaapiException("Unable to query devices: " + e.getMessage());
        }
    }

    public SlaveDevice getDevice(String sid) {
        SlaveDevice device = knownDevices.get(sid);
//        assert(getSid().equals(sid));
        return device;
    }

    public String getSid() {
        return sid;
    }

    public XiaomiGatewayLight getBuiltinLight() {
        return builtinLight;
    }

    public Activity getActivity() {
        return mActivity;
    }

    public XiaomiGatewayIlluminationSensor getBuiltinIlluminationSensor() {
        return builtinIlluminationSensor;
    }

    public void addGatewayView(Activity activity) {

        activity.runOnUiThread(() -> {
            View deviceGateway = activity.findViewById(R.id.gateway);
            TextView gatewayInfo = deviceGateway.findViewById(R.id.device_gateway_info);
            TextView titleBrightness = deviceGateway.findViewById(R.id.title_brightness);
            TextView titleColor = deviceGateway.findViewById(R.id.title_color);
            SeekBar brightness = deviceGateway.findViewById(R.id.seekbar_brightness);
            SeekBar colorR = deviceGateway.findViewById(R.id.seekbar_color_r);
            SeekBar colorG = deviceGateway.findViewById(R.id.seekbar_color_g);
            SeekBar colorB = deviceGateway.findViewById(R.id.seekbar_color_b);
            SwitchButton switchButton = deviceGateway.findViewById(R.id.button_auto_profile_for_gateway);

            gatewayInfo.setVisibility(View.VISIBLE);
            titleBrightness.setVisibility(View.VISIBLE);
            titleColor.setVisibility(View.VISIBLE);
            brightness.setVisibility(View.VISIBLE);
            colorR.setVisibility(View.VISIBLE);
            colorG.setVisibility(View.VISIBLE);
            colorB.setVisibility(View.VISIBLE);
            switchButton.setVisibility(View.VISIBLE);


            XiaomiGatewayLight gatewayLight = getBuiltinLight();
            TextView gatewaySid = activity.findViewById(R.id.device_gateway_info);
            gatewaySid.setText("Sid: " + getSid());
            brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private int progress;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    this.progress = progress;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    Log.d(TAG, "onStopTrackingTouch: " + ((byte) progress));
                    new Thread(() -> {
                        try {
                            gatewayLight.setBrightness((byte) progress, true);
                        } catch (XaapiException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            });

            colorR.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private int progress;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    this.progress = progress;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    Log.d(TAG, "onStopTrackingTouch - r: " + progress);
                    new Thread(() -> {
                        try {
                            gatewayLight.setColor(progress, -1, -1);
                        } catch (XaapiException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            });

            colorG.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private int progress;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    this.progress = progress;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    Log.d(TAG, "onStopTrackingTouch - g: " + progress);
                    new Thread(() -> {
                        try {
                            gatewayLight.setColor(-1, progress, -1);
                        } catch (XaapiException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            });

            colorB.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private int progress;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    this.progress = progress;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    Log.d(TAG, "onStopTrackingTouch - b: " + progress);
                    new Thread(() -> {
                        try {
                            gatewayLight.setColor(-1, -1, progress);
                        } catch (XaapiException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            });
        });
    }

    private boolean isMyself(String sid) {
        return sid.equals(this.sid);
    }

    private void updateKey(String token) throws XaapiException {
        if(cipher != null) {
            try {
                String keyAsHexString = Utility.toHexString(cipher.doFinal(token.getBytes(StandardCharsets.US_ASCII)));
                Log.d(TAG, "updateKey - token: " + token + " keyAsHexString: " + keyAsHexString);
                key = Optional.of(keyAsHexString);

                mHandler.sendEmptyMessage(DISMISS_PROGRESS_DIALOG);

            } catch (IllegalBlockSizeException e) {
                throw new XaapiException("Cipher error: " + e.getMessage());
            } catch (BadPaddingException e) {
                throw new XaapiException("Cipher error: " + e.getMessage());
            }
        } else {
            throw new XaapiException("Unable to update key without a cipher. Did you forget to set a password?");
        }
    }

    void sendDataToDevice(SlaveDevice device, JsonObject data) throws XaapiException {
        Log.d(TAG, "sendDataToDevice - key : " + key.isPresent() + " data: " + data);
        if(key.isPresent()) {
            try {
                if (TEST_FOR_APP_DEV) {
                    if (device instanceof XiaomiSocket) {
                        ((XiaomiSocket)device).update(data.toString());
                    } else if (device instanceof XiaomiMotionSensor) {
                        ((XiaomiMotionSensor)device).update(data.toString());
                    } else if (device instanceof XiaomiDoorWindowSensor) {
                        ((XiaomiDoorWindowSensor)device).update(data.toString());
                    } else if (device instanceof TradFriBulb) {
                        ((TradFriBulb)device).update(data.toString());
                    }
                }
                WriteCommand command = new WriteCommand(device, data, key.get());
                Log.d(TAG, "sendDataToDevice - sid: " +device.getSid() + " type: " + device.getType().name() + " data: " + command.getString());
                directChannel.send(command.toBytes());
                // TODO add handling for expired key
            } catch (IOException e) {
                throw new XaapiException("Network error: " + e.getMessage());
            }
        } else {
            if (TEST_FOR_APP_DEV) {
                Log.d(TAG, "[TEST_FOR_APP_DEV] sendDataToDevice - sid: " +device.getSid() + " type: " + device.getType().name() + " data: " );
            }
            throw new XaapiException("Unable to control device without a key. Did you forget to set a password?");
        }
    }

    void sendDataToDevice(BuiltinDevice device /* just a type marker for overloading */, JsonObject data) throws XaapiException {
        assert device.gateway.equals(this);
        Log.d(TAG, "sendDataToDevice(BuiltinDevice) - key : " + key.isPresent());
        if(key.isPresent()) {
            try {
                WriteSelfCommand sendCommand = new WriteSelfCommand(this, data, key.get());
                directChannel.send(sendCommand.toBytes());
                // TODO add handling for expired key
                String replyString = new String(directChannel.receive());
                Log.d(TAG, "sendDataToDevice(BuiltinDevice) - received : " + replyString);
            } catch (IOException e) {
                throw new XaapiException("Network error: " + e.getMessage());
            }
        } else {
            throw new XaapiException("Unable to control device without a key. Did you forget to set a password?");
        }
    }

    public SlaveDevice testXiaomiSocket(String sid, short shortId) {
        XiaomiSocket testXiaomi = mTestXiaomiSocket;
        if (testXiaomi == null) {
            mTestXiaomiSocket = new XiaomiSocket(this, sid, shortId);
        }
        return mTestXiaomiSocket;
    }

    public SlaveDevice testXiaomiMotionSensor(String sid, short shortId) {
        XiaomiMotionSensor testXiaomi = mTestXiaomiMotionSensor;
        if (testXiaomi == null) {
            mTestXiaomiMotionSensor = new XiaomiMotionSensor(this, sid, shortId);
        }
        return mTestXiaomiMotionSensor;
    }

    public SlaveDevice testXiaomiDoorSensor(String sid, short shortId) {
        XiaomiDoorWindowSensor testXiaomi = mTestXiaomiDoorSensor;
        if (testXiaomi == null) {
            mTestXiaomiDoorSensor = new XiaomiDoorWindowSensor(this, sid, shortId);
        }
        return mTestXiaomiDoorSensor;
    }

    public SlaveDevice testTradfriBulb(String sid, short shortId) {
        TradFriBulb testXiaomi = mTestTradfriBulb;
        if (testXiaomi == null) {
            mTestTradfriBulb = new TradFriBulb(this, sid, shortId);
        }
        return mTestTradfriBulb;
    }

    private SlaveDevice readDevice(String sid) throws XaapiException {
        try {
            directChannel.send(new ReadCommand(sid).toBytes());
            String replyString = new String(directChannel.receive());
            ReadReply reply = GSON.fromJson(replyString, ReadReply.class);
            Log.d(TAG, "readDevice - sid: " + sid + " model: " + reply.model + " short_id: " + reply.short_id + " data: " + reply.data);

            switch(reply.model) {
                case "cube":
                    XiaomiCube cube = new XiaomiCube(this, sid);
                    cube.update(reply.data);
                    return cube;
                case "magnet":
                    XiaomiDoorWindowSensor magnet = new XiaomiDoorWindowSensor(this, sid, Short.parseShort(reply.short_id));
                    magnet.update(reply.data);
                    return magnet;
                case "plug":
                    XiaomiSocket plug = new XiaomiSocket(this, sid, Short.parseShort(reply.short_id));
                    plug.update(reply.data);
                    return plug;
                case "motion":
                    XiaomiMotionSensor motion = new XiaomiMotionSensor(this, sid, Short.parseShort(reply.short_id));
                    motion.update(reply.data);
                    return motion;
                case "switch":
                    XiaomiSwitchButton button = new XiaomiSwitchButton(this, sid);
                    button.update(reply.data);
                    return button;
                case "tradfri": // check device type!!
                    TradFriBulb tradFriBulb = new TradFriBulb(this, sid, SlaveDevice.Type.TradFriBulb, Short.parseShort(reply.short_id));
                    tradFriBulb.update(reply.data);
                    return tradFriBulb;
                case "sensor_ht":
                    DefaultSlaveDevice sensorHT = new DefaultSlaveDevice(this, sid, SlaveDevice.Type.Sensor_HT);
                    return sensorHT;
                case "sensor_motion.aq2":
                    DefaultSlaveDevice sensorMotionAq2 = new DefaultSlaveDevice(this, sid, SlaveDevice.Type.Sensor_Motion_AQ2);
                    return sensorMotionAq2;
                case "weather.v1":
                    DefaultSlaveDevice weatherV1 = new DefaultSlaveDevice(this, sid, SlaveDevice.Type.Weather_V1);
                    return weatherV1;
                case "ctrl_neutral1":
                    DefaultSlaveDevice ctrlNeutral1 = new DefaultSlaveDevice(this, sid, SlaveDevice.Type.Ctrl_Neutral1);
                    return ctrlNeutral1;
                default:
                    DefaultSlaveDevice defaultSlaveDevice = new DefaultSlaveDevice(this, sid);
                    return defaultSlaveDevice;
//                    throw new XaapiException("Unsupported device model: " + reply.model);
            }
        } catch (IOException e) {
            throw new XaapiException("Unable to query device " + sid + ": " + e.getMessage());
        }
    }

    public void startReceivingUpdates(Executor executor) {
        continueReceivingUpdates = true;
        executor.execute(() -> {
            while (continueReceivingUpdates) {
                try {
                    String received = new String(incomingMulticastChannel.receive());
                    handleUpdate(GSON.fromJson(received, ReadReply.class), received);
                } catch (SocketTimeoutException e) {
                    // ignore
                } catch (IOException e) {
                    e.printStackTrace();
                    continueReceivingUpdates = false;
                } catch (XaapiException e) {
                    e.printStackTrace();
                    continueReceivingUpdates = false;
                }
            }
        });
    }

    public void stopReceivingUpdates() {
        continueReceivingUpdates = false;
    }

    private void handleUpdate(Reply update, String received) throws XaapiException {
        Log.d(TAG, "handleUpdate - cmd: " + update.cmd + " sid: " + update.sid + " isMyself: " + isMyself(update.sid));
        switch(update.cmd) {
            case "report":
                Report report = GSON.fromJson(received, Report.class);
                if(isMyself(update.sid)) {
                    handleBuiltinReport(report);
                } else {
                    handleReport(report);
                }
                break;
            case "heartbeat":
                if(isMyself(update.sid)) {
                    GatewayHeartbeat gatewayHeartbeat = GSON.fromJson(received, GatewayHeartbeat.class);
                    handleGatewayHeartbeat(gatewayHeartbeat);
                } else {
                    SlaveDeviceHeartbeat slaveDeviceHeartbeat = GSON.fromJson(received, SlaveDeviceHeartbeat.class);
                    handleSlaveDeviceHeartbeat(slaveDeviceHeartbeat);
                }
                break;
            default:
                throw new XaapiException("Unexpected update command: " + update.cmd);
        }
    }

    private void handleReport(Report report) {
        getDevice(report.sid).update(report.data);
    }

    private void handleBuiltinReport(Report report) {
        builtinLight.update(report.data);
        builtinIlluminationSensor.update(report.data);
    }

    private void handleGatewayHeartbeat(GatewayHeartbeat gatewayHeartbeat) throws XaapiException {
        if(cipher != null && mCipherComplete) {
            Log.d(TAG, "handleGatewayHeartbeat - token: " + gatewayHeartbeat.token);
            updateKey(gatewayHeartbeat.token);
        }
    }

    private void handleSlaveDeviceHeartbeat(SlaveDeviceHeartbeat slaveDeviceHeartbeat) {
        // TODO implement
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XiaomiGateway that = (XiaomiGateway) o;
        return Objects.equals(sid, that.sid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sid);
    }
}