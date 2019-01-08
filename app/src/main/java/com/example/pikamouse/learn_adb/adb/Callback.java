package com.example.pikamouse.learn_adb.adb;



public interface Callback {
    void onSuccess(String adbResponse);
    void onFail(String failString);
}
