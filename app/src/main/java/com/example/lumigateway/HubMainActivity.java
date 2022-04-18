package com.example.lumigateway;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.example.XaapiException;
import com.example.device.SlaveDevice;
import com.example.device.XiaomiGateway;
import com.example.device.XiaomiMotionSensor;
import com.kyleduo.switchbutton.SwitchButton;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HubMainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final long REPORT_PERIOD = 1000;
    private DeviceListAdapter mDeviceListAdapter;
    private SlaveDeviceAdapter mSlaveDeviceAdapter;
    private XiaomiGateway mGateway;
    private Executor mExecutor;
    private static final String PASSWORD = "1ccocfmmli41trb5";
    private boolean mEnableAutoProfile;
    private MyAutoProfile mMyProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText passwordView = findViewById(R.id.password);
        passwordView.setText(PASSWORD);

        RecyclerView deviceListRecyclerView = findViewById(R.id.recyclerview_read_device);
        RecyclerView.LayoutManager deviceListLM = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        deviceListRecyclerView.setLayoutManager(deviceListLM);
        mDeviceListAdapter = new DeviceListAdapter();
        deviceListRecyclerView.setAdapter(mDeviceListAdapter);

        RecyclerView subDeviceRecyclerView = findViewById(R.id.recyclerview_sub_device);
        RecyclerView.LayoutManager subDeviceLM = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        subDeviceRecyclerView.setLayoutManager(subDeviceLM);

        mMyProfile = new MyAutoProfile();
        mSlaveDeviceAdapter = new SlaveDeviceAdapter(this, mMyProfile);
        subDeviceRecyclerView.setAdapter(mSlaveDeviceAdapter);

        Button button = findViewById(R.id.send_button);
        button.setOnClickListener(view -> {
            String password = passwordView.getText().toString();
            Log.d(TAG, "onCreate - password: " + password);
            XiaomiGateway gateway = startGateway(password);
            if (gateway != null && password != null && !password.isEmpty()) {
                try {
                    Log.d(TAG, "onCreate: configurePassword");
                    mGateway.configurePassword(password);
                } catch (XaapiException e) {
                    e.printStackTrace();
                }
            }
        });

        SwitchButton switchButton = findViewById(R.id.button_auto_profile);
        switchButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mEnableAutoProfile = isChecked;
            }
        });
    }

    private XiaomiGateway startGateway(String password) {
        XiaomiGateway gateway = mGateway;
        if (gateway == null) {
            new Thread(() -> {
                try {
                    XiaomiGateway.onFoundSubDevice listener = (sid, deviceInfo) -> {
                        Log.d(TAG, "onSubDevice - sid: " + sid);
                        if (mDeviceListAdapter == null || mSlaveDeviceAdapter == null) {
                            return;
                        }
                        mDeviceListAdapter.addDeviceList(deviceInfo);
                        mSlaveDeviceAdapter.addDeviceList(deviceInfo);
                        runOnUiThread(() -> {
                            mDeviceListAdapter.notifyDataSetChanged();
                            mSlaveDeviceAdapter.notifyDataSetChanged();
                        });
                    };
                    mGateway = XiaomiGateway.discover(listener);
                    if (password != null && !password.isEmpty()) {
                        mGateway.configurePassword(password);
                    }
                } catch (IOException | XaapiException e) {
                    e.printStackTrace();
                }

                if(mGateway != null && mGateway.getSid() != null) {
                    Log.d(TAG, "onSubDevice - start startReceivingUpdates");
                    mExecutor = new ThreadPoolExecutor(1, 4, REPORT_PERIOD, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
                    mGateway.startReceivingUpdates(mExecutor);
                }
            }).start();
        }
        return mGateway;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGateway != null) {
            mGateway.stopReceivingUpdates();
        }
    }

    class MyAutoProfile implements SlaveDeviceAdapter.OnSlaveDeviceEvent {
        Map<Short, SlaveDevice> myProfile = new HashMap<>();

        @Override
        public void onSmartPlug(SlaveDevice device, String s) {

        }

        @Override
        public void onSmartMotionSensor(SlaveDevice device, String s) {
            Log.d(TAG, "onSmartMotionSensor: " + s + " name: " + XiaomiMotionSensor.Action.Motion.name());
            if (mEnableAutoProfile && XiaomiMotionSensor.Action.Motion.name().equalsIgnoreCase(s)) {
                executeAllProfile();
            }
        }

        @Override
        public void onSmartDoorSensor(SlaveDevice device, String s) {

        }

        @Override
        public void onAddProfile(SlaveDevice device, short shortId, boolean enable) {
            Log.d(TAG, "onAddProfile - shortId: " + shortId + " enable: " + enable);
            if (enable) {
                myProfile.put(shortId, device);
            } else {
                myProfile.remove(shortId);
            }
        }

        private void executeAllProfile() {
            Iterator<SlaveDevice> iterator = myProfile.values().iterator();
            while (iterator.hasNext()) {
                SlaveDevice device = iterator.next();
                Log.d(TAG, "executeAllProfile: " + device.getShortId());
                device.executeProfile();
            }
        }
    }

    class TestTVDevice {
        //To WOL on TV
        private static final String IP_WOL_DEVICE = "192.168.45.53";
        private static final String IP_MAGIC_WOL_DEVICE = "192.168.45.255";
        private static final String MAC_WOL_DEVICE = "64:CB:E9:AD:A5:32";

        //To WOL on TV
        public void turnOnTv() {
            new Thread() {
                @Override
                public void run() {
                    String macStr = MAC_WOL_DEVICE;
                    String ipaddr = IP_WOL_DEVICE;
                    String ipaddrForTurnOn = IP_MAGIC_WOL_DEVICE;
//                if (mLastConnectedDeviceIp != null && !mLastConnectedDeviceIp.isEmpty()) {
//                    ipaddr = mLastConnectedDeviceIp;
//                    ipaddrForTurnOn = getWolIP(ipaddr);
//                }
                    int port = 9;
                    Log.d(TAG, "start TV Turn On");
                    try {
                        byte[] macBytes = getMacBytes(macStr);
                        byte[] bytes = new byte[6 + 16 * macBytes.length];

                        for (int i = 0; i < 6; i++) {
                            bytes[i] = (byte) 0xff;
                        }
                        for (int i = 6; i < bytes.length; i += macBytes.length) {
                            System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
                        }
                        InetAddress address = InetAddress.getByName(ipaddrForTurnOn);
                        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, port);
                        DatagramSocket socket = new DatagramSocket();
                        socket.send(packet);
                        socket.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Fail to send magic packet.", e);
                    }

                    try {
                        Thread.sleep(7000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }

        //To WOL on TV
        private byte[] getMacBytes(String macStr) throws IllegalArgumentException {
            byte[] bytes = new byte[6];
            String[] hex = macStr.split("(\\:|\\-)");
            if (hex.length != 6) {
                throw new IllegalArgumentException("Invalid MAC address.");
            }
            try {
                for (int i = 0; i < 6; i++) {
                    bytes[i] = (byte) Integer.parseInt(hex[i], 16);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid hex digit in MAC address.");
            }
            return bytes;
        }
    }
}

