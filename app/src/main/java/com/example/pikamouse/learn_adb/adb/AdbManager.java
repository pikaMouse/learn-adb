package com.example.pikamouse.learn_adb.adb;

import android.content.Context;


public class AdbManager {
    static class Holder {
        private static AdbManager INSTANCE = new AdbManager();
    }
    private AdbService adbService;

    public static AdbManager getInstance() {
        return Holder.INSTANCE;
    }

    public void init(Context context) {
        if (adbService == null) {
            adbService = new AdbService(context);
        }
    }

    private AdbManager() {
    }

    public void performAdbRequest(String cmd, Callback callback){
        adbService.performAdbRequest(cmd,callback);
    }
}
