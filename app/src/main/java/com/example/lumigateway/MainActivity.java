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

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private DeviceListAdapter mDeviceListAdapter;
    private SubDeviceAdapter mSubDeviceAdapter;

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
        mSubDeviceAdapter = new SubDeviceAdapter();
        subDeviceRecyclerView.setAdapter(mSubDeviceAdapter);

        Button button = findViewById(R.id.send_button);
        button.setOnClickListener(view -> {
            new Thread(() -> {
                try {
                    Log.d(TAG, "onCreate: discover");

                    // Device Query Test 실행 코드
                    XiaomiGateway gateway = XiaomiGateway.discover((sid, deviceInfo) -> {
                        Log.d(TAG, "[DEBUG] onSubDevice - sid: " + sid);
                        mDeviceListAdapter.addDeviceList(deviceInfo);
                        mSubDeviceAdapter.addDeviceList(deviceInfo);
                        runOnUiThread(() -> {
                            mDeviceListAdapter.notifyDataSetChanged();
                            mSubDeviceAdapter.notifyDataSetChanged();
                        });
                    });


                } catch (IOException e) {
                    Log.d(TAG, "onCreate: IOException", e);
                    e.printStackTrace();
                } catch (XaapiException e) {
                    Log.d(TAG, "onCreate: XaapiException", e);
                    e.printStackTrace();
                }
            }).start();
        });

    }
}

