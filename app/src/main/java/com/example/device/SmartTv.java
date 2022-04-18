package com.example.device;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.example.lumigateway.R;
import com.kyleduo.switchbutton.SwitchButton;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class SmartTv {
    private static final String TAG = "SmartTv";
    //To WOL on TV
    private static final String IP_WOL_DEVICE = "192.168.45.53";
    private static final String IP_MAGIC_WOL_DEVICE = "192.168.45.255";
    private static final String MAC_WOL_DEVICE = "64:CB:E9:AD:A5:32";

    private View deviceTv;
    private TextView deviceInfo;
    private Button deviceTunOn;

    public interface OnTvDeviceEvent {
        void onAddProfile(SmartTv device);
    }

    private OnTvDeviceEvent mListener;

    public SmartTv(View view, OnTvDeviceEvent listener) {
        mListener = listener;
        deviceTv = view.findViewById(R.id.device_tv);
        deviceTv.setVisibility(View.VISIBLE);

        deviceInfo = view.findViewById(R.id.device_tv_info);
        deviceInfo.setText("Ip : " + IP_WOL_DEVICE + ", Mac : " + MAC_WOL_DEVICE);

        deviceTunOn = view.findViewById(R.id.button_device_tv);

        deviceTunOn.setOnClickListener(v -> {
            turnOnTv();
        });

        SwitchButton autoProfile = view.findViewById(R.id.button_auto_profile);
        autoProfile.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG, "onCheckedChanged - isChecked: " + isChecked);
                if (isChecked) {
                    mListener.onAddProfile(SmartTv.this);
                }
            }
        });
    }

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