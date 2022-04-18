package com.example.lumigateway;

import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.XaapiException;
import com.example.device.IInteractiveDevice;
import com.example.device.SlaveDevice;
import com.example.device.TradFriBulb;
import com.example.device.XiaomiDoorWindowSensor;
import com.example.device.XiaomiMotionSensor;
import com.example.device.XiaomiSocket;
import com.kyleduo.switchbutton.SwitchButton;

import java.util.ArrayList;
import java.util.function.Consumer;

public class SlaveDeviceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final static String TAG = "SlaveDeviceAdapter";

    private final ArrayList<SlaveDevice> mDeviceList = new ArrayList<>();

    private Activity mActivity;

    public SlaveDeviceAdapter(Activity activity) {
        mActivity = activity;
    }

    public SlaveDeviceAdapter(Activity activity, OnSlaveDeviceEvent listener) {
        mActivity = activity;
        mListener = listener;
    }

    public void addDeviceList(SlaveDevice deviceInfo) {
        this.mDeviceList.add(deviceInfo);
    }

    public interface OnSlaveDeviceEvent {
        void onSmartPlug(SlaveDevice device, String s);
        void onSmartMotionSensor(SlaveDevice device, String s);
        void onSmartDoorSensor(SlaveDevice device, String s);
        void onAddProfile(SlaveDevice device, short shortId, boolean enable);
    }

    private OnSlaveDeviceEvent mListener;

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if (viewType == SlaveDevice.Type.Sensor_Motion_AQ2.ordinal()
                || viewType == SlaveDevice.Type.XiaomiMotionSensor.ordinal()) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.smart_motion_sendor, parent, false);
            return new SmartMotionSensorViewHolder(view);
        } else if (viewType == SlaveDevice.Type.XiaomiSocket.ordinal()) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.smart_plug, parent, false);
            return new SmartPlugViewHolder(view);
        } else if (viewType == SlaveDevice.Type.XiaomiDoorWindowSensor.ordinal()) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.smart_door_sensor, parent, false);
            return new SmartDoorSensorViewHolder(view);
        } else if (viewType == SlaveDevice.Type.TradFriBulb.ordinal()) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.tradfri_bulb, parent, false);
            return new TradFriBulbViewHolder(view);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_list_item, parent, false);
            return new DefaultViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Log.d(TAG, "onBindViewHolder - position: " + position);
        SlaveDevice device = mDeviceList.get(position);
        if (holder instanceof SmartPlugViewHolder) {
            ((SmartPlugViewHolder)holder).onBind(device);
        } else if (holder instanceof SmartMotionSensorViewHolder) {
            ((SmartMotionSensorViewHolder)holder).onBind(device);
        } else if (holder instanceof SmartDoorSensorViewHolder) {
            ((SmartDoorSensorViewHolder)holder).onBind(device);
        } else if (holder instanceof TradFriBulbViewHolder) {
            ((TradFriBulbViewHolder)holder).onBind(device);
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
        TextView deviceSid;
        Button buttonOn;
        Button buttonOff;
        XiaomiSocket socket;
        SwitchButton autoProfile;

        public SmartPlugViewHolder(View itemView) {
            super(itemView);
            deviceSid = itemView.findViewById(R.id.device_sid);
            buttonOn = itemView.findViewById(R.id.device_smart_plug_on);
            buttonOff = itemView.findViewById(R.id.device_smart_plug_off);
            autoProfile = itemView.findViewById(R.id.button_auto_profile);

            buttonOn.setOnClickListener(v -> {
                smartPlugOnOff(socket, true);
            });

            buttonOff.setOnClickListener(v -> {
                smartPlugOnOff(socket, false);
            });
        }

        void onBind(SlaveDevice device) {
            socket = (XiaomiSocket)device;
            deviceSid.setText(device.getSid());
            socket.subscribeForActions(new Consumer<String>() {
                @Override
                public void accept(String s) {
                    Log.d(TAG, "accept: " + s);
                    mListener.onSmartPlug(device, s);
                }
            });

            autoProfile.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (mListener != null && socket != null) {
                        mListener.onAddProfile(socket, socket.getShortId(), isChecked);
                    }
                }
            });
        }

        private void smartPlugOnOff(XiaomiSocket socket, boolean on) {
            new Thread(() -> {
                try {
                    Log.d(TAG, "onClick: turnOn");
                    if (on) {
                        socket.turnOn();
                    } else {
                        socket.turnOff();
                    }
                } catch (XaapiException e) {
                    Log.e(TAG, "onBind: ", e);
                    e.printStackTrace();
                }
            }).start();
        }
    }



    class SmartMotionSensorViewHolder extends RecyclerView.ViewHolder {
        TextView deviceSid;
        TextView reportText;
        XiaomiMotionSensor sensor;
        View motionSign;
        public SmartMotionSensorViewHolder(View itemView) {
            super(itemView);
            deviceSid = itemView.findViewById(R.id.device_sid);
            reportText = itemView.findViewById(R.id.device_motion_sensor);
            motionSign = itemView.findViewById(R.id.motion_sign);
        }

/*        void onBind(SlaveDevice device) {
            sensor = (XiaomiMotionSensor)device;
            deviceSid.setText(device.getSid());
            IInteractiveDevice.SubscriptionToken token = sensor.subscribeForMotion(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "run: subscribeForMotion");
                        mActivity.runOnUiThread(() -> {
                            if (sensor.getLastAction() == XiaomiMotionSensor.Action.Motion) {
                                reportText.setText("Motion");
                                motionSign.setBackgroundColor(Color.BLUE);
                            } else {
                                reportText.setText("Unknown");
                                motionSign.setBackgroundColor(Color.RED);
                            }
                        });
                }
            });
        }*/

        void onBind(SlaveDevice device) {
            sensor = (XiaomiMotionSensor)device;
            deviceSid.setText(device.getSid());
            IInteractiveDevice.SubscriptionToken token = sensor.subscribeForActions(new Consumer<String>() {
                @Override
                public void accept(String s) {
                    Log.d(TAG, "SmartMotionSensorViewHolder - " + sensor.getShortId() + " action: " + s);
                    mListener.onSmartMotionSensor(sensor, s);
                    mActivity.runOnUiThread(() -> {
                        if (s.equals("motion")) {
                            reportText.setText("Motion");
                            motionSign.setBackgroundColor(Color.BLUE);
                        } else {
                            reportText.setText("Unknown");
                            motionSign.setBackgroundColor(Color.RED);
                        }
                    });
                }
            });
        }
    }

    class SmartDoorSensorViewHolder extends RecyclerView.ViewHolder {
        TextView deviceSid;
        TextView reportText;
        XiaomiDoorWindowSensor sensor;
        View motionSign;

        public SmartDoorSensorViewHolder(View itemView) {
            super(itemView);
            deviceSid = itemView.findViewById(R.id.device_sid);
            reportText = itemView.findViewById(R.id.device_door_sensor);
            motionSign = itemView.findViewById(R.id.door_sign);
        }

        void onBind(SlaveDevice device) {
            sensor = (XiaomiDoorWindowSensor) device;
            deviceSid.setText(device.getSid());
            IInteractiveDevice.SubscriptionToken token = sensor.subscribeForActions(new Consumer<String>() {
                @Override
                public void accept(String s) {
                    Log.d(TAG, "SmartDoorSensorViewHolder - " + sensor.getShortId() + " action: " + s);
                    mListener.onSmartDoorSensor(sensor, s);
                    mActivity.runOnUiThread(() -> {
                        if (s.equals("close")) {
                            reportText.setText("Close");
                            motionSign.setBackgroundColor(Color.BLUE);
                        } else {
                            reportText.setText("Open");
                            motionSign.setBackgroundColor(Color.RED);
                        }
                    });
                }
            });
        }

/*        void onBind(SlaveDevice device) {
            sensor = (XiaomiDoorWindowSensor)device;
            deviceSid.setText(device.getSid());
            IInteractiveDevice.SubscriptionToken token = sensor.subscribeForMotion(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "run: subscribeForMotion");
                    mActivity.runOnUiThread(() -> {
                        if (sensor.getLastAction() == XiaomiDoorWindowSensor.Action.Close) {
                            reportText.setText("Close");
                            motionSign.setBackgroundColor(Color.BLUE);
                        } else {
                            reportText.setText("Open");
                            motionSign.setBackgroundColor(Color.RED);
                        }
                    });
                }
            });
        }*/
    }

    class TradFriBulbViewHolder extends RecyclerView.ViewHolder {
        TextView deviceSid;
        Button buttonOn;
        Button buttonOff;
        TradFriBulb tradFriBulb;
        SwitchButton autoProfile;
        SeekBar seekBar;

        public TradFriBulbViewHolder(View itemView) {
            super(itemView);
            deviceSid = itemView.findViewById(R.id.device_sid);
            buttonOn = itemView.findViewById(R.id.device_smart_plug_on);
            buttonOff = itemView.findViewById(R.id.device_smart_plug_off);
            autoProfile = itemView.findViewById(R.id.button_auto_profile);
            seekBar = itemView.findViewById(R.id.seekbar_brightness);

            buttonOn.setOnClickListener(v -> {
                tradfriBulbOnOff(tradFriBulb, true);
            });

            buttonOff.setOnClickListener(v -> {
                tradfriBulbOnOff(tradFriBulb, false);
            });
        }

        void onBind(SlaveDevice device) {
            tradFriBulb = (TradFriBulb)device;
            deviceSid.setText(device.getSid());
            tradFriBulb.subscribeForActions(new Consumer<String>() {
                @Override
                public void accept(String s) {
                    Log.d(TAG, "accept: " + s);
                    mListener.onSmartPlug(device, s);
                }
            });

            autoProfile.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (mListener != null && tradFriBulb != null) {
                        mListener.onAddProfile(tradFriBulb, tradFriBulb.getShortId(), isChecked);
                    }
                }
            });

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private int progress;
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    this.progress = progress;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    new Thread(() -> {
                        try {
                            tradFriBulb.changeBrightness(progress);
                        } catch (XaapiException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            });
        }

        private void tradfriBulbOnOff(TradFriBulb bulb, boolean on) {
            new Thread(() -> {
                try {
                    Log.d(TAG, "onClick: turnOn");
                    if (on) {
                        bulb.turnOn();
                    } else {
                        bulb.turnOff();
                    }
                } catch (XaapiException e) {
                    Log.e(TAG, "onBind: ", e);
                    e.printStackTrace();
                }
            }).start();
        }
    }

    public ArrayList<SlaveDevice> getDeviceList() {
        return mDeviceList;
    }
}
