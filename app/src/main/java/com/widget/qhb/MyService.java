package com.widget.qhb;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.logging.Handler;

public class MyService extends AccessibilityService {
    private boolean canGet = false;//能否点击红包
    private boolean enableKeyguard = true;//默认有屏幕锁

    //窗口状态
    private static final int WINDOW_NONE = 0;
    private static final int WINDOW_LUCKYMONEY_RECEIVEUI = 1;
    private static final int WINDOW_LUCKYMONEY_DETAIL = 2;
    private static final int WINDOW_LAUNCHER = 3;
    private static final int WINDOW_OTHER = -1;
    //当前窗口
    private int mCurrentWindow = WINDOW_NONE;

    //锁屏、解锁相关
    private KeyguardManager km;
    private KeyguardManager.KeyguardLock kl;
    //唤醒屏幕相关
    private PowerManager pm;
    private PowerManager.WakeLock wl = null;

    //播放提示声音
    private MediaPlayer player;
    private boolean openBack = false;

    public void playSound(Context context) {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        //夜间不播放提示音
//        if(hour > 7 && hour < 22) {
        player.start();
//        }
    }

    //唤醒屏幕和解锁
    private void wakeAndUnlock(boolean unLock) {
        if (unLock) {
            //若为黑屏状态则唤醒屏幕
            if (!pm.isScreenOn()) {
                //获取电源管理器对象，ACQUIRE_CAUSES_WAKEUP这个参数能从黑屏唤醒屏幕
                wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "bright");
                //点亮屏幕
                wl.acquire();
                Log.i("demo", "亮屏");
            }
            //若在锁屏界面则解锁直接跳过锁屏
            if (km.inKeyguardRestrictedInputMode()) {
                //设置解锁标志，以判断抢完红包能否锁屏
                enableKeyguard = false;
                //解锁
                kl.disableKeyguard();
                Log.i("demo", "解锁");
            }
        } else {
            //如果之前解过锁则加锁以恢复原样
            if (!enableKeyguard) {
                //锁屏
                kl.reenableKeyguard();
                Log.i("demo", "加锁");
            }
            //若之前唤醒过屏幕则释放之使屏幕不保持常亮
            if (wl != null) {
                wl.release();
                wl = null;
                Log.i("demo", "关灯");
            }
        }
    }

    //通过文本查找节点
    public AccessibilityNodeInfo findNodeInfosByText(AccessibilityNodeInfo nodeInfo, String text) {
        List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText(text);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    //模拟点击事件
    public void performClick(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return;
        }
        if (nodeInfo.isClickable()) {
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            performClick(nodeInfo.getParent());
        }
    }

    //模拟返回事件
    public void performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    //实现辅助功能
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        Log.i("demo", Integer.toString(eventType));
        switch (eventType) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED://*******通知栏中存在红包消息时
                List<CharSequence> texts = event.getText();
                if (!texts.isEmpty()) {
                    for (CharSequence text : texts) {
                        String content = text.toString();
                        Log.i("demo", "text:" + content);
                        //收到红包提醒
                        if (content.contains("[微信红包]")) {//  if (content.contains("[微信红包]")) {//
                            //模拟打开通知栏消息
                            if (event.getParcelableData() != null && event.getParcelableData() instanceof Notification) {
                                //播放提示音
                                playSound(this);
                                //若是微信红包则解锁并自动打开
//                                wakeAndUnlock(true);
                                Log.i("demo", "canGet=true");
                                canGet = true;
                                try {
                                    Notification notification = (Notification) event.getParcelableData();
                                    PendingIntent pendingIntent = notification.contentIntent;
                                    pendingIntent.send();//***********发送Notification的点击事件
                                } catch (PendingIntent.CanceledException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        }
                    }
                }
                break;
            //第二步：监听是否进入微信红包消息界面
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED://*******在微信红包应用程序更换UI布局界面时
                String className = event.getClassName().toString();
                if (className.equals("com.tencent.mm.ui.LauncherUI")) {
                    mCurrentWindow = WINDOW_LAUNCHER;
                    //开始抢红包
                    Log.i("demo", "准备抢红包...");
                    getPacket();
                    if (openBack) {
                        performBack();
                        openBack = false;
                    }
                } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI")) {//聊天界面
                    mCurrentWindow = WINDOW_LUCKYMONEY_RECEIVEUI;
                    openBack = false;
                    //开始打开红包
                    Log.i("demo", "打开红包");
                    openPacket();//点击“开”
//                    wakeAndUnlock(false);
                } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI")) {//开红包后的界面
                    mCurrentWindow = WINDOW_LUCKYMONEY_DETAIL;
                    openBack = true;
                    //返回以方便下次收红包
                    Log.i("demo", "返回");
                    performBack();
                } else {
                    mCurrentWindow = WINDOW_OTHER;
                    openBack = false;
                }
                break;

            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED://*******当前界面内容发生改变时
                Log.i("demo", "TYPE_WINDOW_CONTENT_CHANGED");
                if (mCurrentWindow != WINDOW_LAUNCHER) { //不在聊天界面或聊天列表，不处理
                    return;
                }
                if (canGet) {
                    getPacket();
                }
                break;
        }
    }

    //找到红包并点击
    @SuppressLint("NewApi")
    private void getPacket() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo == null) {
            return;
        }
        // 找到领取红包的点击事件
        List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText("微信红包");//领取红包名字改变了，不是固定的

        if (list != null) {
//            if (list.isEmpty()) {
//                Log.i("demp", "领取列表为空");
//                // 从消息列表查找红包
//                AccessibilityNodeInfo node = findNodeInfosByText(nodeInfo, "[微信红包]");
//                if (node != null) {
//                    canGet = true;
//                    performClick(node);
//                }
//            } else {
            if (canGet) {
                //最新的红包领起
                AccessibilityNodeInfo node = list.get(list.size() - 1);
                performClick(node);
                Log.i("demo", "canGet=false");
                canGet = false;
            }
//            }
        }
    }

    //打开红包，模拟点击“拆包”
    @SuppressLint("NewApi")
    private void openPacket() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo == null) {
            return;
        }

        Log.i("demo", "查找打开按钮...");
        AccessibilityNodeInfo targetNode = null;

        //如果红包已经被抢完则直接返回
        targetNode = findNodeInfosByText(nodeInfo, "看看大家的手气");
        if (targetNode != null) {
            performBack();
            return;
        }
        //***********不通过控件Id查找“开按钮”
        //通过组件名查找开红包按钮，还可通过组件id直接查找但需要知道id且id容易随版本更新而变化，旧版微信还可直接搜“開”字找到按钮
        if (targetNode == null) {
            Log.i("demo", "打开按钮中...");
            for (int i = 0; i < nodeInfo.getChildCount(); i++) {
                AccessibilityNodeInfo node = nodeInfo.getChild(i);
                if ("android.widget.Button".equals(node.getClassName())) {
                    targetNode = node;
                    break;
                }
            }
        }
        //若查找到打开按钮则模拟点击
        if (targetNode != null) {
            final AccessibilityNodeInfo n = targetNode;
            performClick(n);
        }
    }

    @Override
    public void onInterrupt() {
        Toast.makeText(this, "抢红包服务被中断啦~", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i("demo", "开启");
        //获取电源管理器对象
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        //得到键盘锁管理器对象
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        //初始化一个键盘锁管理器对象
        kl = km.newKeyguardLock("unLock");
        //初始化音频
        player = MediaPlayer.create(this, R.raw.hbll);

        Toast.makeText(this, "_已开启抢红包服务_", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("demo", "关闭");
//        wakeAndUnlock(false);
        Toast.makeText(this, "_已关闭抢红包服务_", Toast.LENGTH_LONG).show();
    }

}
