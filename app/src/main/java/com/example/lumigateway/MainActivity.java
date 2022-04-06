package com.example.lumigateway;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.example.XaapiException;
import com.example.device.XiaomiGateway;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = findViewById(R.id.send_button);

        TextView sendView = findViewById(R.id.send_packet);
        TextView receiveView = findViewById(R.id.recv_packet);

        button.setOnClickListener(view -> {
            new Thread(() -> {
                try {
                    Log.d(TAG, "onCreate: discover");

                    // Device Query Test 실행 코드
                    XiaomiGateway gateway = XiaomiGateway.discover();

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