package com.example.pikamouse.learn_adb.adb;

public class ThreadPoolProxyFactory {
    private static ThreadPoolProxy mThreadPoolProxy;

    public static ThreadPoolProxy getThreadPoolProxy() {
        if (mThreadPoolProxy == null) {
            synchronized (ThreadPoolProxyFactory.class) {
                if (mThreadPoolProxy == null) {
                    mThreadPoolProxy = new ThreadPoolProxy(5, 5);
                }
            }
        }
        return mThreadPoolProxy;
    }
}