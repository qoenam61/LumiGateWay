package com.example.lumigateway;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.XaapiException;
import com.example.device.SlaveDevice;
import com.example.device.XiaomiGateway;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private DeviceListAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recyclerview_read_device);
        RecyclerView.LayoutManager manager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(manager);
        mAdapter = new DeviceListAdapter();
        recyclerView.setAdapter(mAdapter);

        Button button = findViewById(R.id.send_button);
        button.setOnClickListener(view -> {
            new Thread(() -> {
                try {
                    Log.d(TAG, "onCreate: discover");

                    // Device Query Test 실행 코드
                    XiaomiGateway gateway = XiaomiGateway.discover();
                    Map<String, SlaveDevice> map = gateway.getKnownDevices();
                    ArrayList<SlaveDevice> list = new ArrayList<>(map.values());
                    Log.d(TAG, "[DEBUG] onCreate - list.size(): " + list.size());

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.setDeviceList(list);
                            mAdapter.notifyDataSetChanged();
                        }
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

