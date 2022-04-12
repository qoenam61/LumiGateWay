package com.example.lumigateway;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.device.SlaveDevice;

import java.util.ArrayList;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.DeviceListHolder> {

    private ArrayList<SlaveDevice> mDeviceList = new ArrayList<>();

    public void addDeviceList(SlaveDevice deviceInfo) {
        this.mDeviceList.add(deviceInfo);
    }

    @Override
    public DeviceListHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_list_item, parent, false);
        return new DeviceListHolder(view);
    }

    @Override
    public void onBindViewHolder(DeviceListHolder holder, int position) {
        holder.onBind(mDeviceList.get(position));
    }

    @Override
    public int getItemCount() {
        return mDeviceList != null ? mDeviceList.size() : 0;
    }

    class DeviceListHolder extends RecyclerView.ViewHolder {

        TextView deviceInfo;

        public DeviceListHolder(View itemView) {
            super(itemView);
            deviceInfo = itemView.findViewById(R.id.device_item);
        }

        void onBind(SlaveDevice device) {
            deviceInfo.setText("sid: " + device.getSid() + ", model : " + device.getType().name());
        }
    }
}
