package com.example.lumigateway;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import com.example.XaapiException;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
            startGateway();
        });

    }

    private XiaomiGateway startGateway() {
        XiaomiGateway gateway = mGateway;
        if (gateway == null) {
            new Thread(() -> {
                try {
                    mGateway = XiaomiGateway.discover((sid, deviceInfo) -> {
                        Log.d(TAG, "onSubDevice - sid: " + sid);
                        mDeviceListAdapter.addDeviceList(deviceInfo);
                        mSlaveDeviceAdapter.addDeviceList(deviceInfo);
                        runOnUiThread(() -> {
                            mDeviceListAdapter.notifyDataSetChanged();
                            mSlaveDeviceAdapter.notifyDataSetChanged();
                        });
                    });
                } catch (IOException | XaapiException e) {
                    e.printStackTrace();
                }

                if(mGateway != null && mGateway.getSid() != null) {
                    Log.d(TAG, "onSubDevice - start startReceivingUpdates");
                    Executor executor = new ThreadPoolExecutor(1, 4, REPORT_PERIOD, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
                    mGateway.startReceivingUpdates(executor);
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

