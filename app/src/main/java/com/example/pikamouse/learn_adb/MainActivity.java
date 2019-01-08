package com.example.pikamouse.learn_adb;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.example.pikamouse.learn_adb.adb.AdbManager;
import com.example.pikamouse.learn_adb.adb.Callback;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "jiangfeng";
    private static final float SECOND_IN_NANOS = 1000000000f;
    private static final int NORMAL_FRAME_RATE = 1;


    private TextView mStartAdb;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private float mMaxMemory;
    private Context mContext;
    private ActivityManager mActivityManager;
    private boolean mAboveAndroidO; // 是否是8.0及其以上
    private String mPackageName;
    private static final int MSG_CPU = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mStartAdb = findViewById(R.id.tv_start_adb);
        mStartAdb.setOnClickListener(this);
        init(this);
    }
    private void excuteCpuData() {
        if (mAboveAndroidO) {
            //8.0之后由于权限问题只能通过adb的方式获取
            AdbManager.getInstance().performAdbRequest("shell:dumpsys cpuinfo | grep '" + mPackageName + "'",
                    new Callback() {
                        @Override
                        public void onSuccess(String adbResponse) {
                            Log.d(TAG, "response is " + adbResponse);
                        }
                        @Override
                        public void onFail(String failString) {
                            Log.d(TAG, "failString is " + failString);
                        }
                    });
        } else {

        }
    }

    public void init(Context context) {
        mContext = context;
        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAboveAndroidO = true;
            mPackageName = context.getPackageName();
            AdbManager.getInstance().init(context);
        }
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("handler-thread");
            mHandlerThread.start();
        }
        if (mHandler == null) {
            mHandler = new Handler(mHandlerThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    if (msg.what == MSG_CPU) {
                        excuteCpuData();
                        mHandler.sendEmptyMessageDelayed(MSG_CPU, NORMAL_FRAME_RATE * 1000);
                    }
                }
            };
        }
    }
    public void startMonitorCPUInfo() {
        mHandler.sendEmptyMessageDelayed(MSG_CPU, NORMAL_FRAME_RATE * 1000);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.tv_start_adb:
                startMonitorCPUInfo();
                break;
            default:
                break;
        }
    }
}
