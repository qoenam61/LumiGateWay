package com.example.lumigateway;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import com.example.XaapiException;
import com.example.device.SlaveDevice;
import com.example.device.XiaomiGateway;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final long REPORT_PERIOD = 1000;
    private DeviceListAdapter mDeviceListAdapter;
    private SlaveDeviceAdapter mSlaveDeviceAdapter;
    private XiaomiGateway mGateway;
    private Executor mExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText passwordView = findViewById(R.id.password);

        RecyclerView deviceListRecyclerView = findViewById(R.id.recyclerview_read_device);
        RecyclerView.LayoutManager deviceListLM = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        deviceListRecyclerView.setLayoutManager(deviceListLM);
        mDeviceListAdapter = new DeviceListAdapter();
        deviceListRecyclerView.setAdapter(mDeviceListAdapter);

        RecyclerView subDeviceRecyclerView = findViewById(R.id.recyclerview_sub_device);
        RecyclerView.LayoutManager subDeviceLM = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        subDeviceRecyclerView.setLayoutManager(subDeviceLM);
        mSlaveDeviceAdapter = new SlaveDeviceAdapter(this);
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

    }

    private XiaomiGateway startGateway(String password) {
        XiaomiGateway gateway = mGateway;
        if (gateway == null) {
            new Thread(() -> {
                try {
                    XiaomiGateway.onFoundSubDevice listener = (sid, deviceInfo) -> {
                        Log.d(TAG, "onSubDevice - sid: " + sid);
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
}

