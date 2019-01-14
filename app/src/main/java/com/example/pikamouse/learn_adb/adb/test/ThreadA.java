package com.example.pikamouse.learn_adb.adb.test;

/**
 * create by jiangfeng 2019/1/14
 */
public class ThreadA {

    public static void main(String[] args) throws InterruptedException{
        ThreadB b = new ThreadB();
        //启动计算线程
        b.start();
        //线程A拥有b对象上的锁。线程为了调用wait()或notify()方法，该线程必须是那个对象锁的拥有者
        //Sychronized同步代码块会生成monitorenter和monitorexit指令，当jvm执行到monitorenter指令时，
        //并且对象b的monitor中的计数器count为0，那线程可以获取到对象的monitor并将计数器count加1。
        synchronized (b) {
            System.out.println("等待对象b完成计算。。。");
            //当前线程A等待
            b.wait();
            System.out.println("b对象计算的总和是：" + b.total);
        }
    }
}



/**
 * 计算1+2+3 ... +100的和
 *
 */
class ThreadB extends Thread {
    int total;

    public void run() {
        synchronized (this) {
            for (int i = 0; i < 101; i++) {
                total += i;
            }
            //（完成计算了）唤醒在此对象监视器上等待的单个线程，在本例中线程A被唤醒
            notify();
            System.out.println("计算完成");
        }
    }
}
