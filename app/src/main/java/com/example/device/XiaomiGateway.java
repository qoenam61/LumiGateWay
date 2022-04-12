package com.example.device;

import android.util.Log;

import com.example.XaapiException;
import com.example.channel.DirectChannel;
import com.example.channel.IncomingMulticastChannel;
import com.example.command.GetIdListCommand;
import com.example.command.ReadCommand;
import com.example.command.WhoisCommand;
import com.example.command.WriteCommand;
import com.example.command.WriteSelfCommand;
import com.example.reply.GatewayHeartbeat;
import com.example.reply.GetIdListReply;
import com.example.reply.ReadReply;
import com.example.reply.Reply;
import com.example.reply.Report;
import com.example.reply.SlaveDeviceHeartbeat;
import com.example.reply.WhoisReply;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

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

public class XiaomiGateway {
    private static final String TAG = "XiaomiGateway";
    private static final String GROUP = "224.0.0.50";
    private static final int PORT = 9898;
    private static final int PORT_DISCOVERY = 4321;
    private static final byte[] IV =
        {     0x17, (byte)0x99, 0x6d, 0x09, 0x3d, 0x28, (byte)0xdd, (byte)0xb3,
        (byte)0xba,       0x69, 0x5a, 0x2e, 0x6f, 0x58,       0x56,       0x2e};

    private static final Gson GSON = new Gson();

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

    private boolean TEST_FOR_APP_DEV = true;

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

    public static XiaomiGateway discover(onFoundSubDevice listener) throws IOException, XaapiException {
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

        return new XiaomiGateway(reply, listener);
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
        mSubDeviceListener = listener;
        this.mWhoisReply = reply;
        this.incomingMulticastChannel = new IncomingMulticastChannel(GROUP, PORT);
        this.directChannel = new DirectChannel(reply.ip, PORT);
        Log.d(TAG, "XiaomiGateway: queryDevices");
        queryDevices();
        Log.d(TAG, "XiaomiGateway: configureBuiltinDevices");
        configureBuiltinDevices();
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
        configureCipher(password);
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
    }

    private void configureCipher(String password) throws XaapiException {
        try {
            cipher = Cipher.getInstance("AES/CBC/NoPadding");
            final SecretKeySpec keySpec = new SecretKeySpec(password.getBytes(), "AES");
            final IvParameterSpec ivSpec = new IvParameterSpec(IV);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
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

            //TEST CODE
            if (TEST_FOR_APP_DEV) {
                Log.d(TAG, "[TEST_FOR_APP_DEV] queryDevices: testXiaomiSocket");
                SlaveDevice device = testXiaomiSocket();
                knownDevices.put("123456789", device);
                mSubDeviceListener.onSubDevice("123456789", device);
            }
        } catch (IOException e) {
            throw new XaapiException("Unable to query devices: " + e.getMessage());
        }
    }

    public SlaveDevice getDevice(String sid) {
        SlaveDevice device = knownDevices.get(sid);
        assert(device.getSid().equals(sid));
        return device;
    }

    public String getSid() {
        return sid;
    }

    public XiaomiGatewayLight getBuiltinLight() {
        return builtinLight;
    }

    public XiaomiGatewayIlluminationSensor getBuiltinIlluminationSensor() {
        return builtinIlluminationSensor;
    }

    private boolean isMyself(String sid) {
        return sid.equals(this.sid);
    }

    private void updateKey(String token) throws XaapiException {
        if(cipher != null) {
            try {
                String keyAsHexString = Utility.toHexString(cipher.doFinal(token.getBytes(StandardCharsets.US_ASCII)));
                key = Optional.of(keyAsHexString);
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
        if(key.isPresent()) {
            try {
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
        if(key.isPresent()) {
            try {
                directChannel.send(new WriteSelfCommand(this, data, key.get()).toBytes());
                // TODO add handling for expired key
            } catch (IOException e) {
                throw new XaapiException("Network error: " + e.getMessage());
            }
        } else {
            throw new XaapiException("Unable to control device without a key. Did you forget to set a password?");
        }
    }

    public SlaveDevice testXiaomiSocket() {
        return new XiaomiSocket(this, sid);
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
                    XiaomiDoorWindowSensor magnet = new XiaomiDoorWindowSensor(this, sid);
                    magnet.update(reply.data);
                    return magnet;
                case "plug":
                    XiaomiSocket plug = new XiaomiSocket(this, sid);
                    plug.update(reply.data);
                    return plug;
                case "motion":
                    XiaomiMotionSensor motion = new XiaomiMotionSensor(this, sid);
                    motion.update(reply.data);
                    return motion;
                case "switch":
                    XiaomiSwitchButton button = new XiaomiSwitchButton(this, sid);
                    button.update(reply.data);
                    return button;
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
        if(cipher != null) {
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