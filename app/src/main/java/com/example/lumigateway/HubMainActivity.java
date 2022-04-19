package com.example.lumigateway;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.XaapiException;
import com.example.device.SlaveDevice;
import com.example.device.SmartTv;
import com.example.device.XiaomiGateway;
import com.example.device.XiaomiGatewayLight;
import com.example.device.XiaomiMotionSensor;
import com.kyleduo.switchbutton.SwitchButton;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HubMainActivity extends AppCompatActivity {
    private static final String TAG = "HubMainActivity";
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

        View deviceTv = findViewById(R.id.device_tv);
        deviceTv.setVisibility(View.GONE);
        SmartTv testTVDevice = new SmartTv(deviceTv, new SmartTv.OnTvDeviceEvent() {
            @Override
            public void onAddProfile(SmartTv smartTv) {
                Log.d(TAG, "onAddProfile: smartTv");
                mMyProfile.onAddProfileTV(smartTv);
            }
        });

        TextView gatewayInfo = findViewById(R.id.device_gateway_info);
        TextView titleBrightness = findViewById(R.id.title_brightness);
        TextView titleColor = findViewById(R.id.title_color);
        SeekBar brightness = findViewById(R.id.seekbar_brightness);
        SeekBar colorR = findViewById(R.id.seekbar_color_r);
        SeekBar colorG = findViewById(R.id.seekbar_color_g);
        SeekBar colorB = findViewById(R.id.seekbar_color_b);

        gatewayInfo.setVisibility(View.INVISIBLE);
        titleBrightness.setVisibility(View.INVISIBLE);
        titleColor.setVisibility(View.INVISIBLE);
        brightness.setVisibility(View.INVISIBLE);
        colorR.setVisibility(View.INVISIBLE);
        colorG.setVisibility(View.INVISIBLE);
        colorB.setVisibility(View.INVISIBLE);
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
                    mGateway.addGatewayView(this);
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
        SmartTv smartTv;

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

        public void onAddProfileTV(SmartTv smartTv) {
            this.smartTv = smartTv;
        }

        private void executeAllProfile() {
            Iterator<SlaveDevice> iterator = myProfile.values().iterator();
            while (iterator.hasNext()) {
                SlaveDevice device = iterator.next();
                Log.d(TAG, "executeAllProfile: " + device.getShortId());
                device.executeProfile();
            }
            if (smartTv != null) {
                smartTv.turnOnTv();
            }
        }
    }
}

