package org.servalproject.wifidirect;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import org.servalproject.Control;
import org.servalproject.R;

import java.util.Collection;
import java.util.Iterator;

/**
 * Created by Leaf on Leaf0820.
 */
public class AutoWiFiDirect extends BroadcastReceiver {

    private WifiP2pManager manager;
    private Channel channel;
    private WiFiDirectActivity activity;
    private Control control;
    static boolean Isconnect;
    private String myDeviceName;
    
    //

    /**
     * @param manager WifiP2pManager system service
     * @param channel Wifi p2p channel
     * @param control control associated with the receiver
     */

    public AutoWiFiDirect(WifiP2pManager manager, Channel channel,
                          Control control, Boolean Isconnect, String myDeviceName) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.control = control;
        this.Isconnect = Isconnect;
        this.myDeviceName = myDeviceName;
    }
    

    /*
     * (non-Javadoc)
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
     * android.content.Intent)
     */

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            Log.d("Leaf0820", "STATE CHANGE");

        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            Log.d("Leaf1112", "PEER CHANGED");
            //control.fun_connect_peer();
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            Log.d("Leaf1112", "CONNECTION CHANGE");
            NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            Isconnect = networkInfo.isConnected();
            control.Isconnect = Isconnect;
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            //Log.d("Leaf0820", "DEVICE CHANGE");
            WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            //Log.d("Leaf1117", "Broadcast, name:"+device.deviceName);
            myDeviceName = device.deviceName;
            control.myDeviceName = myDeviceName;
        }
    }
}
