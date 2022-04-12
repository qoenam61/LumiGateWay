package com.example.lumigateway;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.recyclerview.widget.RecyclerView;

import com.example.XaapiException;
import com.example.device.SlaveDevice;
import com.example.device.XiaomiSocket;

import java.util.ArrayList;

public class SubDeviceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final static String TAG = "SubDeviceAdapter";
    private ArrayList<SlaveDevice> mDeviceList = new ArrayList<>();

    public void addDeviceList(SlaveDevice deviceInfo) {
        this.mDeviceList.add(deviceInfo);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if (viewType == SlaveDevice.Type.Sensor_Motion_AQ2.ordinal()) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.motion_sendor, parent, false);
            return new SmartMotionSensorViewHolder(view);
        } else if (viewType == SlaveDevice.Type.XiaomiSocket.ordinal()) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.smart_plug, parent, false);
            return new SmartPlugViewHolder(view);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_list_item, parent, false);
            return new DefaultViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        SlaveDevice device = mDeviceList.get(position);
        if (holder instanceof SmartPlugViewHolder) {
            ((SmartPlugViewHolder)holder).onBind(device);
        } else if (holder instanceof SmartMotionSensorViewHolder) {
            ((SmartMotionSensorViewHolder)holder).onBind(device);
        } else {
            ((DefaultViewHolder)holder).onBind(device);
        }
    }

    @Override
    public int getItemCount() {
        return mDeviceList != null ? mDeviceList.size() : 0;
    }

    @Override
    public int getItemViewType(int position) {
        return mDeviceList != null ? mDeviceList.get(position).getType().ordinal() : SlaveDevice.Type.Others.ordinal();
    }

    class DefaultViewHolder extends RecyclerView.ViewHolder {
        public DefaultViewHolder(View itemView) {
            super(itemView);
        }

        void onBind(SlaveDevice device) {

        }
    }

    class SmartPlugViewHolder extends RecyclerView.ViewHolder {
        Button buttonOn;
        Button buttonOff;

        public SmartPlugViewHolder(View itemView) {
            super(itemView);
            buttonOn = itemView.findViewById(R.id.device_smart_plug_on);
            buttonOff = itemView.findViewById(R.id.device_smart_plug_off);
        }

        void onBind(SlaveDevice device) {

            XiaomiSocket socket = (XiaomiSocket)device;

            buttonOn.setOnClickListener(v -> {
                try {
                    Log.d(TAG, "onClick: turnOn");
                    socket.turnOn();
                } catch (XaapiException e) {
                    e.printStackTrace();
                }
            });

            buttonOff.setOnClickListener(v -> {
                try {
                    Log.d(TAG, "onClick: turnOff");
                    socket.turnOff();
                } catch (XaapiException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    class SmartMotionSensorViewHolder extends RecyclerView.ViewHolder {
        public SmartMotionSensorViewHolder(View itemView) {
            super(itemView);
        }

        void onBind(SlaveDevice device) {

        }
    }
}
