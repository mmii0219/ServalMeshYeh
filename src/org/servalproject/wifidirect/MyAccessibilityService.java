package org.servalproject.wifidirect;

/**
 * Created by Leaf on 2015/9/14.
 */
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.servalproject.R;

public class MyAccessibilityService extends AccessibilityService {
    private boolean isInit = false;
    private String connectChar1;
    private String connectChar2;
    private String connectChar3;
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event!=null) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null && event.getClassName().equals("android.widget.Button")
                    && event.getPackageName().equals("android")) {
                Log.d("Leaf1102", "event:"+event+" source:"+source);
                connectChar1 = getString(R.string.connectChinese1);
                connectChar2 = getString(R.string.connectChinese2);
                connectChar3 = getString(R.string.connectChinese3);
                if(source.getText()==null) return;
                if (source.getText().toString().equals(connectChar1) ||
                        source.getText().toString().equals(connectChar2) ||
                        source.getText().toString().equals(connectChar3)) {
                    source.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d("Leaf0922", "Click it");
                }
            }
        }
    }

    @Override
    public void onInterrupt() {

    }
    @Override
    public void onServiceConnected()
    {
        if (isInit) {
            return;
        }
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        if(info!=null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
            setServiceInfo(info);
        }
        isInit = true;
    }
}