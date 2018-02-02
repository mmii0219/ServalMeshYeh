package org.servalproject;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.ScanResult;
import android.content.BroadcastReceiver;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.format.Time;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.servald.IPeer;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.ServalDFailureException;
import org.servalproject.ui.Networks;
import org.servalproject.wifidirect.AutoWiFiDirect;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * Control service responsible for turning Serval on and off and changing the
 * Wifi radio mode.
 */
public class Control extends Service {
    private ServalBatPhoneApplication app;
    private boolean servicesRunning = false;
    private boolean serviceRunning = false;
    private SimpleWebServer webServer;
    private int peerCount = -1;
    private PowerManager.WakeLock cpuLock;
    private WifiManager.MulticastLock multicastLock = null;
    private static final String TAG = "Control";
    // Leaf0818
    private WifiP2pManager manager;
    private Channel channel;
    private final IntentFilter intentFilter = new IntentFilter();
    private BroadcastReceiver receiver = null;
    private BroadcastReceiver receiver_scan = null;
    public static boolean Isconnect = false;
    public String myDeviceName = null;

    private Thread t_findPeer = null;
    private Thread t_checkGO = null;
    private Thread t_wifi_connect = null;
    private Thread t_reconnection_wifiAp = null;
    private Thread t_collectIP = null;
    private Thread t_send_peer_count = null;
    private Thread t_receive_peer_count = null;
    private boolean isRunning = false;
    static public boolean Auto = false;
    private MyBinder mBinder = new MyBinder();
    // Leaf1104
    public int STATE;
    private WifiManager wifi = null;
    private String GOpasswd = null;
    private String WiFiApName = null;
    private String Cluster_Name = null;
    private String Internet_Accessibility = null;

    private ConnectivityManager mConnectivityManager = null;
    private NetworkInfo mNetworkInfo = null;
    private WifiP2pDnsSdServiceRequest serviceRequest = null;
    private WifiP2pDnsSdServiceInfo serviceInfo = null;
    private Map record = null;
    private Map record_re = null;
    // Leaf0616
    private int result_size = 0;
    private boolean pre_connect = false;
    private List<ScanResult> wifi_scan_results;
    private String WMNETAP = "WMNETTT"; // change to other name if you want to debug without AAP case, default is WMNET
    private String key = "lab741lab741";
    public String s_status = "";
    private long start_time, total_time, sleep_time;
    private static int IP_port_for_IPModify = 2555;
    private static int IP_port_for_peer_counting = 2666;
    private ServerSocket ss = null;
    private Map<String, Integer> IPTable = new HashMap<String, Integer>();
    private Map<String, Integer> PeerTable = new HashMap<String, Integer>();
    private Socket sc; // for CollectIP_server
    private DatagramSocket receiveds; // for receive_peer_count
    private int NumRound;


    public enum StateFlag {
        NOCONNECTION(0), GO_FORMATION(2), DETECTGAW(1), ADD_SERVICE(3), DISCOVERY_SERVICE(4), REMOVE_GROUP(5), WIFI_CONNECT(6), WAITING(7);
        private int index;

        StateFlag(int idx) {
            this.index = idx;
        }

        public int getIndex() {
            return index;
        }
    }

    // <aqua0722>
    private Thread t_native = null;
    private Thread t_register = null;
    private String PublicIP = "140.114.77.81";
    //private final String AnchorAP_SSID = "WMNET";
    //private final String AnchorAP_PWD = "lab741lab741";
    //private int forwardingPort=-1;
    private String GDIPandFP = "";
    private LocalServerSocket Localserver;
    private LocalSocket Localreceiver;
    private BufferedOutputStream Localout;
    public int ROLE;

    public enum RoleFlag {
        WAIT(0), GATEWAY_CANDIDATE(1), MANET_DEVICE(2), FOREIGN_DEVICE(3);
        private int value;

        private RoleFlag(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    // </aqua0722>
    public void onNetworkStateChanged() {
        if (serviceRunning) {
            Log.d("Leaf", "onNetworkStateChanged()");
            new AsyncTask<Object, Object, Object>() {
                @Override
                protected Object doInBackground(Object... params) {
                    modeChanged();
                    return null;
                }
            }.execute();
        }
    }


    private synchronized void startServices() {
        if (servicesRunning)
            return;
        Log.d(TAG, "Starting services");
        servicesRunning = true;
        cpuLock.acquire();
        multicastLock.acquire();
        try {
            app.server.isRunning();
        } catch (ServalDFailureException e) {
            app.displayToastMessage(e.getMessage());
            Log.e(TAG, e.getMessage(), e);
        }
        peerCount = 0;
        updateNotification();
        try {
            ServalDCommand.configActions(
                    ServalDCommand.ConfigAction.del, "interfaces.0.exclude",
                    ServalDCommand.ConfigAction.sync
            );
        } catch (ServalDFailureException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        try {
            if (webServer == null)
                webServer = new SimpleWebServer(8080);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private synchronized void stopServices() {
        if (!servicesRunning)
            return;

        Log.d(TAG, "Stopping services");
        servicesRunning = false;
        multicastLock.release();
        try {
            ServalDCommand.configActions(
                    ServalDCommand.ConfigAction.set, "interfaces.0.exclude", "on",
                    ServalDCommand.ConfigAction.sync
            );
        } catch (ServalDFailureException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        peerCount = -1;
        if (webServer != null) {
            webServer.interrupt();
            webServer = null;
        }

        this.stopForeground(true);
        cpuLock.release();
    }

    private synchronized void modeChanged() {
        boolean wifiOn = app.nm.isUsableNetworkConnected();

        Log.d(TAG, "modeChanged(" + wifiOn + ")");

        // if the software is disabled, or the radio has cycled to sleeping,
        // make sure everything is turned off.
        if (!serviceRunning)
            wifiOn = false;

        if (multicastLock == null) {
            WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            multicastLock = wm.createMulticastLock("org.servalproject");
        }

        if (wifiOn == true || Isconnect == true) {
            Log.d("Leaf0709", "Start Sevice");
            startServices();
        } else {
            stopServices();
        }
    }

    private void updateNotification() {
        if (!servicesRunning)
            return;

        Notification notification = new Notification(
                R.drawable.ic_serval_logo, getString(R.string.app_name),
                System.currentTimeMillis());

        Intent intent = new Intent(app, Main.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        notification.setLatestEventInfo(Control.this, getString(R.string.app_name),
                app.getResources().getQuantityString(R.plurals.peers_label, peerCount, peerCount),
                PendingIntent.getActivity(app, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT));

        notification.flags = Notification.FLAG_ONGOING_EVENT;
        this.startForeground(-1, notification);
    }

    private synchronized void startService() {
        Log.e("Leaf", "modeChanged");
        app.controlService = this;
        app.setState(State.Starting);
        try {
            this.modeChanged();
            app.setState(State.On);
        } catch (Exception e) {
            app.setState(State.Off);
            Log.e("BatPhone", e.getMessage(), e);
            app.displayToastMessage(e.getMessage());
        }
    }

    private synchronized void stopService() {
        Log.e("Leaf", "Control_stopService()");
        app.setState(State.Stopping);
        app.nm.onStopService();
        stopServices();
        app.setState(State.Off);
        app.controlService = null;
    }

    public void updatePeerCount(int peerCount) {
        if (this.peerCount == peerCount)
            return;
        this.peerCount = peerCount;
        updateNotification();
    }

    class Task extends AsyncTask<State, Object, Object> {
        @Override
        protected Object doInBackground(State... params) {
            if (app.getState() == params[0])
                return null;

            if (params[0] == State.Off) {
                stopService();
            } else {
                startService();
            }
            return null;
        }
    }

    private int Newcompare(String a, String b) {
        int alength = a.length();
        int blength = b.length();
        char[] A = a.toCharArray();
        char[] B = b.toCharArray();
        int i, j;
        int result = 0;
        for (i = 0, j = 0; i < alength && j < blength; i++, j++) {
            if (A[i] != B[j]) {
                return A[i] - B[j];
            }
        }
        if (alength > blength) {
            return 1;
        } else if (alength < blength) {
            return -1;
        }
        return result;
    }

    public class WiFi_Connect extends Thread {
        int TryNum;

        public void run() {
            try {
                String SSID = record.get("SSID").toString();
                String key = record.get("PWD").toString();
                String Name = record.get("Name").toString();
                String In_ac = record.get("In_ac").toString();
                String PEER = record.get("PEER").toString();
                //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: choosing peer, step 1";
                s_status = "State: choosing peer";
                Log.d("Leaf0419", "State: choosing peer, step 1");
                if (Isconnect == true && STATE == StateFlag.DISCOVERY_SERVICE.getIndex()) {
                    Log.d("Leaf0419", "State: choosing peer, step 2,  with: " + SSID);
                    if (Newcompare(Name, Cluster_Name) == 0) {
                        return;
                    }
                    if (Newcompare(In_ac, "inter") != 0 && Newcompare(Internet_Accessibility, "inter") == 0) {
                        return;
                    } else if ((Newcompare(In_ac, "inter") != 0 && Newcompare(Internet_Accessibility, "inter") != 0)
                            || (Newcompare(In_ac, "inter") == 0 && Newcompare(Internet_Accessibility, "inter") == 0)) {
                        int peercount = count_peer();
                        if (Integer.valueOf(PEER) < peercount) {
                            return;
                        } else if (Integer.valueOf(PEER) == peercount) {
                            if (Newcompare(Name, Cluster_Name) <= 0) {
                                return;
                            }
                        }
                    }
                    Log.d("Leaf0419", "State: choosing peer, step 3");
                    STATE = StateFlag.REMOVE_GROUP.getIndex();
                    try {
                        // Leaf0616
                        if (mConnectivityManager != null) {
                            mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                            if (mNetworkInfo != null) {
                                if (mNetworkInfo.isConnected() == true) {
                                    wifi.disconnect();
                                    Thread.sleep(1000);
                                }
                            }
                        }
                        List<WifiConfiguration> list = wifi.getConfiguredNetworks();
                        for (WifiConfiguration i : list) {
                            wifi.removeNetwork(i.networkId);
                            wifi.saveConfiguration();
                        }

                        STATE = StateFlag.WIFI_CONNECT.getIndex();
                        // Try to connect Ap
                        WifiConfiguration wc = new WifiConfiguration();
                        total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                        //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: choosing peer done, try to associate with" + ": SSID name: " + SSID + " , passwd: " + key;
                        s_status = "State: choosing peer done, try to associate with" + ": SSID name: " + SSID + " , passwd: " + key;
                        Log.d("Leaf0419", "State: choosing peer done, try to associate with" + ": SSID name: " + SSID + " , passwd: " + key);
                        wc.SSID = "\"" + SSID + "\"";
                        wc.preSharedKey = "\"" + key + "\"";
                        wc.hiddenSSID = true;
                        wc.status = WifiConfiguration.Status.ENABLED;
                        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                        wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                        wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                        TryNum = 4;
                        wifi.startScan();
                        Thread.sleep(5000);
                        int res = wifi.addNetwork(wc);
                        boolean temp = wifi.enableNetwork(res, true);
                        if (mConnectivityManager != null) {
                            mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                            if (mNetworkInfo != null) {
                                while (!mNetworkInfo.isConnected() && TryNum >= 0) {
                                    //res = wifi.addNetwork(wc);
                                    temp = wifi.enableNetwork(res, true);
                                    Thread.sleep(5000);
                                    TryNum--;
                                    total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                                    //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: associating GO, enable true:?" + temp +" remainder #attempt:"+ TryNum;
                                    s_status = "State: associating GO, enable true:?" + temp + " remainder #attempt:" + TryNum;
                                    Log.d("Leaf0419", "State: associating GO, enable true:?" + temp + " remainder #attempt:" + TryNum);
                                    mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                }
                                if (mNetworkInfo.isConnected() == true) {
                                    // renew service record information
                                    Cluster_Name = Name;
                                    Internet_Accessibility = In_ac;
                                    if (manager != null) {
                                        manager.removeGroup(channel, null);
                                    }
                                    Thread.sleep(3000);
                                    // check whether change IP
                                    // EditLeaf0802
                                    String message = wifiIpAddress();
                                    IPTable = new HashMap<String, Integer>();
                                    IPTable.put(message, 0);
                                    Log.d("Leaf0419", "State: set IPTable: " + Integer.valueOf(message.substring(message.lastIndexOf(".") + 1)));
                                    TryNum = 0;
                                    while (TryNum < 5) {
                                        try {
                                            Socket Client_socket = new Socket("192.168.49.1", IP_port_for_IPModify);
                                            PrintWriter out = new PrintWriter(Client_socket.getOutputStream());
                                            Log.d("Leaf0419", "Send message: " + message);
                                            out.println(message);
                                            out.flush();
                                            BufferedReader in = new BufferedReader(new InputStreamReader(Client_socket.getInputStream()));
                                            message = in.readLine();
                                            Log.d("Leaf0419", "Receive message: " + message);
                                            String[] s = message.split(":");
                                            Log.d("Leaf0419", "Split result: " + s[0] + " " + s[1]);
                                            if (Newcompare(s[0], "YES") == 0) {
                                                boolean result = setIpWithTfiStaticIp(s[1]);
                                                Log.d("Leaf0419", "Modify the static IP address: " + result);
                                            }
                                            TryNum = 5;
                                            in.close();
                                            out.close();
                                            Client_socket.close();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        TryNum++;
                                    }
                                    TryNum = 0;
                                    while (peerCount <= 0 && TryNum < 15) {
                                        total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                                        //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: acquiring the newest information";
                                        s_status = "State: acquiring the newest information";
                                        Log.d("Leaf0419", "State: acquiring the newest information");
                                        peerCount = ServalDCommand.peerCount();
                                        Thread.sleep(1000);
                                        TryNum++;
                                    }
                                    STATE = StateFlag.GO_FORMATION.getIndex();
                                } else {
                                    STATE = StateFlag.ADD_SERVICE.getIndex();
                                }
                            } else {
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                                Log.d("Leaf0419", "State: associating GO, mNetworkInfo is null");
                            }
                        } else {
                            STATE = StateFlag.ADD_SERVICE.getIndex();
                            Log.d("Leaf0419", "State: associating GO, mConnectivityManager is null");
                        }
                    } catch (Exception e) {
                        STATE = StateFlag.ADD_SERVICE.getIndex();
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                STATE = StateFlag.ADD_SERVICE.getIndex();
                e.printStackTrace();
            }
        }
    }

    private void discoverService() {
        manager.setDnsSdResponseListeners(channel,
                new WifiP2pManager.DnsSdServiceResponseListener() {
                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                                                        String registrationType, WifiP2pDevice srcDevice) {

                    }
                }, new WifiP2pManager.DnsSdTxtRecordListener() {
                    @Override
                    public void onDnsSdTxtRecordAvailable(
                            String fullDomainName, Map<String, String> re_record,
                            WifiP2pDevice device) {
                        total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                        //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: advertising service, receive frame";
                        s_status = "State: advertising service, receive frame";
                        Log.d("Leaf0419", "State: advertising service, receive frame");
                        record = re_record;
                        if (t_wifi_connect != null) {
                            if (t_wifi_connect.isAlive()) {
                                return;
                            }
                        }
                        if (t_wifi_connect == null) {
                            t_wifi_connect = new WiFi_Connect();
                            t_wifi_connect.start();
                        } else {
                            if (!t_wifi_connect.isAlive()) {
                                t_wifi_connect = new WiFi_Connect();
                                t_wifi_connect.start();
                            }
                        }
                        return;
                    }
                });
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
    }

    private void startRegistration() {
        record_re = new HashMap();
        int peercount = count_peer();
        try {
            peerCount = ServalDCommand.peerCount();
        } catch (ServalDFailureException e) {
            e.printStackTrace();
        }
        if (Cluster_Name == null) {
            Cluster_Name = WiFiApName;
        }
        if (Internet_Accessibility == null) {
            Internet_Accessibility = "intra";
        }
        // Leaf 0616
        if (mConnectivityManager != null) {
            mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (mNetworkInfo != null) {
                if (mNetworkInfo.isConnected() == false) {
                    Cluster_Name = WiFiApName;
                    Internet_Accessibility = "intra";
                } else {
                    WifiInfo wifiInfo = wifi.getConnectionInfo();
                    String temp = "\"" + WMNETAP + "\"";
                    if (Newcompare(wifiInfo.getSSID().toString(), temp) == 0) {
                        Internet_Accessibility = "inter";
                        Cluster_Name = WiFiApName;
                    } else {
                        if (peerCount <= 0) {
                            Cluster_Name = WiFiApName;
                            Internet_Accessibility = "intra";
                        }
                    }
                }
            }
        }
        record_re.put("Name", Cluster_Name);
        record_re.put("SSID", WiFiApName);
        record_re.put("PWD", GOpasswd);
        record_re.put("In_ac", Internet_Accessibility);
        record_re.put("PEER", String.valueOf(peercount));
        total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
        //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: advertising service with " + record_re.toString();
        s_status = "State: advertising service with " + record_re.toString();
        Log.d("Leaf0419", "State: advertising service with " + record_re.toString());
        serviceInfo = WifiP2pDnsSdServiceInfo.newInstance("Wi-Fi_Info", "_presence._tcp", record_re);
        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                manager.addLocalService(channel, serviceInfo,
                        new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                // service broadcasting started
                                Log.d("Leaf0419", "State: advertising service, addLocalService onSuccess");
                                STATE = StateFlag.DISCOVERY_SERVICE.getIndex();
                            }

                            @Override
                            public void onFailure(int error) {
                                Log.d("Leaf0419", "State: advertising service, addLocalService onFailure");
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                            }
                        });
            }

            @Override
            public void onFailure(int error) {
                Log.d("Leaf0419", "State: advertising service, clearLocalServices onFailure");
                STATE = StateFlag.ADD_SERVICE.getIndex();
            }
        });
    }

    // Leaf1105
    public class Reconnection_wifiAp extends Thread {
        ServerSocket GO_serversocket, Client_sersocket;
        Socket GO_socket, Client_socket;
        boolean can_I_connectAP;
        int TryNum;

        public void run() {
            while (isRunning) {
                try {
                    Thread.sleep(1000);
                    if (Auto) {
                        Log.d("Leaf0419", "STATE: " + STATE);
                        if (STATE >= StateFlag.REMOVE_GROUP.getIndex()) continue;

                        // Leaf0616
                        if (STATE == StateFlag.DETECTGAW.getIndex()) {
                            total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                            //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: detecting gateway";
                            s_status = "State: detecting gateway";
                            Log.d("Leaf0419", "State: detecting gateway");
                            wifi.startScan();
                            sleep(5000);
                            sleep_time = sleep_time + 5;
                            for (can_I_connectAP = false; result_size >= 1; result_size--) {
                                if (Newcompare(wifi_scan_results.get(result_size - 1).SSID.toString(), WMNETAP) == 0) {
                                    can_I_connectAP = true;
                                    break;
                                }
                            }
                            if (can_I_connectAP == true) {
                                if (mConnectivityManager != null) {
                                    mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                    if (mNetworkInfo != null) {
                                        if (mNetworkInfo.isConnected() == true) {
                                            wifi.disconnect();
                                            Thread.sleep(1000);
                                        }
                                    }
                                }
                                TryNum = 4;

                                WifiConfiguration wc = new WifiConfiguration();
                                String SSID = wifi_scan_results.get(result_size - 1).SSID.toString();
                                total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                                //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: detecting gateway, try to associate with" + ": SSID name: " + SSID + " , passwd: " + key;
                                s_status = "State: detecting gateway, try to associate with" + ": SSID name: " + SSID + " , passwd: " + key;
                                Log.d("Leaf0419", "State: detecting gateway, try to associate with" + ": SSID name: " + SSID + " , passwd: " + key);
                                wc.SSID = "\"" + SSID + "\"";
                                wc.preSharedKey = "\"" + key + "\"";
                                wc.hiddenSSID = true;
                                wc.status = WifiConfiguration.Status.ENABLED;
                                wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                                wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                                wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                                wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                                wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                                wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);

                                int res = wifi.addNetwork(wc);
                                boolean temp = wifi.enableNetwork(res, true);
                                if (mConnectivityManager != null) {
                                    mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                    if (mNetworkInfo != null) {
                                        while (!mNetworkInfo.isConnected() && TryNum >= 0) {
                                            //res = wifi.addNetwork(wc);
                                            temp = wifi.enableNetwork(res, true);
                                            Thread.sleep(5000);
                                            sleep_time = sleep_time + 5;
                                            TryNum--;
                                            total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                                            //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: associating GO, enable true:?" + temp +" remainder #attempt:"+ TryNum;
                                            s_status = "State: associating GO, enable true:?" + temp + " remainder #attempt:" + TryNum;
                                            Log.d("Leaf0419", "State: associating GO, enable true:?" + temp + " remainder #attempt:" + TryNum);
                                            mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                        }
                                    }
                                }
                            }
                            total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                            //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: detecting gateway done, can I connect to WMnet?: "+can_I_connectAP;
                            s_status = "State: detecting gateway done, can I connect to WMnet?: " + can_I_connectAP;
                            Log.d("Leaf0419", "State: detecting gateway done, can I connect to WMnet?: " + can_I_connectAP);
                            if (Isconnect == true) {
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                            }
                        }
                        mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                        if (pre_connect == true && mNetworkInfo.isConnected() == false) {
                            STATE = StateFlag.DETECTGAW.getIndex();
                            pre_connect = mNetworkInfo.isConnected();
                            continue;
                        }
                        pre_connect = mNetworkInfo.isConnected();
                        if (Isconnect == false) {
                            STATE = StateFlag.GO_FORMATION.getIndex();
                        }

                        if (STATE == StateFlag.GO_FORMATION.getIndex()) {
                            total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                            //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: creating GO";
                            s_status = "State: creating GO";
                            Log.d("Leaf0419", "State: creating GO");
                            if (Isconnect == false) {
                                if (manager != null) {
                                    STATE = StateFlag.WAITING.getIndex();
                                    manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                                        @Override
                                        public void onSuccess() {
                                            STATE = StateFlag.ADD_SERVICE.getIndex();
                                        }

                                        @Override
                                        public void onFailure(int error) {
                                            Log.d("Leaf0419", "createGroup onFailure");
                                            STATE = StateFlag.GO_FORMATION.getIndex();
                                        }
                                    });
                                }
                            } else {
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                            }
                            continue;
                        }
                        if (STATE == StateFlag.ADD_SERVICE.getIndex()) {
                            total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                            //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: advertising service";
                            s_status = "State: advertising service";
                            Log.d("Leaf0419", "State: advertising service");
                            // startRegistration
                            manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                                @Override
                                public void onGroupInfoAvailable(WifiP2pGroup group) {
                                    if (group != null) {
                                        GOpasswd = group.getPassphrase();
                                        WiFiApName = group.getNetworkName();
                                    }
                                }
                            });
                            Thread.sleep(1000);
                            STATE = StateFlag.WAITING.getIndex();
                            startRegistration();
                            discoverService();

                        }
                        if (STATE == StateFlag.DISCOVERY_SERVICE.getIndex()) {
                            total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                            //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: discovering service";
                            s_status = "State: discovering service";
                            Log.d("Leaf0419", "State: discovering service");
                            manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    Log.d("Leaf0419", "State: discovering service, stopPeerDiscovery onSuccess");
                                    manager.removeServiceRequest(channel, serviceRequest,
                                            new WifiP2pManager.ActionListener() {
                                                @Override
                                                public void onSuccess() {
                                                    manager.addServiceRequest(channel, serviceRequest,
                                                            new WifiP2pManager.ActionListener() {
                                                                @Override
                                                                public void onSuccess() {
                                                                    manager.discoverServices(channel,
                                                                            new WifiP2pManager.ActionListener() {
                                                                                @Override
                                                                                public void onSuccess() {
                                                                                    Log.d("Leaf0419", "State: discovering service, discoverServices onSuccess");
                                                                                }

                                                                                @Override
                                                                                public void onFailure(int error) {
                                                                                    Log.d("Leaf0419", "State: discovering service, discoverServices onFailure " + error);
                                                                                    manager.discoverPeers(channel, null);
                                                                                    STATE = StateFlag.DETECTGAW.getIndex();
                                                                                }
                                                                            });
                                                                }

                                                                @Override
                                                                public void onFailure(int error) {
                                                                    Log.d("Leaf0419", "State: discovering service, addServiceRequest onFailure ");
                                                                    STATE = StateFlag.DETECTGAW.getIndex();
                                                                }
                                                            });
                                                }

                                                @Override
                                                public void onFailure(int reason) {
                                                    Log.d("Leaf0419", "State: discovering service, removeServiceRequest onFailure");
                                                    STATE = StateFlag.DETECTGAW.getIndex();
                                                }
                                            });
                                }

                                @Override
                                public void onFailure(int reasonCode) {
                                    Log.d("Leaf0419", "State: discovering service, stopPeerDiscovery onFailure");
                                    STATE = StateFlag.DETECTGAW.getIndex();
                                }
                            });
                            NumRound++;
                            Thread.sleep(15000);
                            sleep_time = sleep_time + 15;
                            if (STATE == StateFlag.DETECTGAW.getIndex()) {
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                                /*wifi.setWifiEnabled(false);
                                Thread.sleep(500);
                                wifi.setWifiEnabled(true);
                                Thread.sleep(1000);*/
                            } else if (STATE == StateFlag.DISCOVERY_SERVICE.getIndex()) {
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (Client_socket != null) {
                        try {
                            Client_socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (Client_sersocket != null) {
                        try {
                            Client_sersocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (GO_socket != null) {
                        try {
                            GO_socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (GO_serversocket != null) {
                        try {
                            GO_serversocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    // <aqua0722>
    public class NativeCall extends Thread {
        private byte[] buffer = new byte[256];
        String SSID_Time_info;
        String ip;
        WifiInfo wifiInfo;
        private NetworkInfo nativeInfo;
        private long currentTime;

        public void run() {
            try {
                Localserver = new LocalServerSocket("wifi.ap.info");
                while (isRunning) {
                    Localreceiver = Localserver.accept();
                    if (Localreceiver != null) {
                        //Log.d("Leaf0324", "Control, get wifiinfo: Localreceiver != null" );
                        nativeInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                        wifiInfo = wifi.getConnectionInfo();
                        Localout = new BufferedOutputStream(Localreceiver.getOutputStream());
                        if (wifiInfo != null) {
                            SSID_Time_info = wifiInfo.getSSID();
                        }
                        if (nativeInfo != null) {
                            if (!nativeInfo.isConnected()) {
                                SSID_Time_info = "NoWiFi";
                            }
                        }
                        if (wifiInfo == null || nativeInfo == null) {
                            SSID_Time_info = "NoWiFi";
                        }
                        //Edit by aqua0711
                        SSID_Time_info = String.valueOf(ROLE);
                        SSID_Time_info = SSID_Time_info + "#";
                        if (ROLE == RoleFlag.FOREIGN_DEVICE.getValue() && GDIPandFP != "") {
                            SSID_Time_info = SSID_Time_info + GDIPandFP;
                        }
                        Log.d("aqua0711", "Control, get wifiinfo: " + SSID_Time_info);
                        if (Localout != null) {
                            Localout.write(SSID_Time_info.getBytes());
                            Localout.flush();
                            Localout.close();
                        }
                        Localreceiver.close();
                    }
                }
            } catch (IOException e) {
                Log.e(getClass().getName(), e.getMessage());
            } finally {
                try {
                    if (Localout != null) {
                        Localout.close();
                    }
                    if (Localreceiver != null) {
                        Localreceiver.close();
                    }
                    if (Localserver != null) {
                        Localserver.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class RegisterToServer extends Thread {
        private Socket socket = null;
        private int r;
        private byte[] buffer = new byte[128];
        String message, temp, SSID;
        // Register server IP and portserve
        String ip = "140.114.77.81";
        int port = 9999;
        int FP;
        private NetworkInfo RegisterInfo;
        private BufferedOutputStream out;
        private BufferedInputStream in;
        private WifiInfo wifiInfo;

        public void run() {
            while (isRunning) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                RegisterInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                wifiInfo = wifi.getConnectionInfo();
                try {
                    if (RegisterInfo == null || wifiInfo == null) continue;
                    SSID = wifiInfo.getSSID();
                    if (RegisterInfo.isConnected() && SSID.equals("\"WMNET\"")) {
                        Log.d("aqua0711", "Gateway device");
                        ROLE = RoleFlag.GATEWAY_CANDIDATE.getValue();
                        socket = new Socket();
                        if (socket != null) {
                            socket.bind(null);
                            socket.connect((new InetSocketAddress(ip, port)), 500);
                            FP = getForwardingPort();
                            Log.d("Leaf0324", "Control, register message");
                            Log.d("aqua0711", String.valueOf(FP));
                            out = new BufferedOutputStream(socket.getOutputStream());
                            in = new BufferedInputStream(socket.getInputStream());
                            message = "Rgs:" + FP;
                            if (out != null) {
                                out.write(message.getBytes());
                                out.flush();
                                Log.d("Leaf0324", "Control, register done");
                                if (in != null) {
                                    message = "";
                                    while (true) {

                                        r = in.read(buffer);
                                        if (r == -1) break;
                                        temp = new String(buffer, 0, r);
                                        message = message.concat(temp);
                                    }
                                    Log.d("Leaf0324", "Control, receive: " + message);
                                    in.close();
                                    out.close();
                                }
                            }
                            socket.close();
                        }
                    } else if (RegisterInfo.isConnected() && SSID.equals("\"WMNET-705\"")) {
                        Log.d("aqua0711", "Foreign device");
                        ROLE = RoleFlag.FOREIGN_DEVICE.getValue();
                        socket = new Socket();
                        if (socket != null) {
                            socket.bind(null);
                            socket.connect((new InetSocketAddress(ip, port)), 500);
                            Log.d("Leaf0324", "Control, request message");
                            out = new BufferedOutputStream(socket.getOutputStream());
                            in = new BufferedInputStream(socket.getInputStream());
                            message = "Req:";
                            if (out != null) {
                                out.write(message.getBytes());
                                out.flush();
                                Log.d("Leaf0324", "Control, register done");
                                if (in != null) {
                                    message = "";
                                    while (true) {
                                        r = in.read(buffer);
                                        if (r == -1) break;
                                        temp = new String(buffer, 0, r);
                                        message = message.concat(temp);
                                    }
                                    Log.d("Leaf0324", "Control, receive: " + message);
                                    in.close();
                                    out.close();
                                }
                            }
                            socket.close();
                        }
                        GDIPandFP = message + ";";
                        Log.d("aqua0711", "GD IP and Port : " + GDIPandFP);


                    } else {
                        Log.d("aqua0711", "Normal device");
                        ROLE = RoleFlag.MANET_DEVICE.getValue();
                    }
                } catch (IOException e) {
                    Log.e(getClass().getName(), e.getMessage());
                    GDIPandFP = "No GD;";
                } finally {
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }


        }
    }

    private String wifiIpAddress() {
        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        int ipAddress = wm.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endianif needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e("WIFIIP", "Unable to get host address.");
            ipAddressString = null;
        }

        return ipAddressString;
    }

    private int getForwardingPort() {
        String wlan0ip = wifiIpAddress();
        int lastDot = wlan0ip.lastIndexOf('.');
        int RightMostInt = Integer.valueOf(wlan0ip.substring(lastDot + 1));
        //Log.d("aqua0720", "IP rightest: "+RightMostInt);
        return RightMostInt + 10000;
    }
    // </aqua0722>

    // EditLeaf 0727
    public class CollectIP_server extends Thread {
        private BufferedReader in;
        private PrintWriter out;
        private String message, temp;
        private int i;

        public void run() {
            try {
                ss = new ServerSocket(IP_port_for_IPModify);
                while (true) {
                    sc = ss.accept();
                    in = new BufferedReader(new InputStreamReader(sc.getInputStream()));
                    message = in.readLine();
                    Log.d("Leaf0419", "Receive IP: " + message);
                    if (IPTable.containsKey(message)) {
                        temp = message;
                        for (i = 2; i < 254; i++) {
                            temp = "192.168.49." + String.valueOf(i);
                            if (IPTable.containsKey(temp) == false) break;
                        }
                        IPTable.put(temp, 0);
                        message = "YES:" + temp;
                    } else {
                        IPTable.put(message, 0);
                        message = "NO:X";
                    }
                    out = new PrintWriter(sc.getOutputStream());
                    out.println(message);
                    Log.d("Leaf0419", "Send the message: " + message);
                    out.flush();

                    out.close();
                    in.close();
                    sc.close();
                }

            } catch (IOException e) {
                Log.e(getClass().getName(), e.getMessage());
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                    if (sc != null) {
                        sc.close();
                    }
                    if (ss != null) {
                        ss.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // EditLeaf 0812
    public class Receive_peer_count extends Thread {
        private byte[] lMsg;
        private DatagramPacket receivedp, senddp;
        private DatagramSocket sendds;
        private Iterator iterator;
        private String message, tempkey;
        private String[] temp;

        public void run() {
            lMsg = new byte[8192];
            receivedp = new DatagramPacket(lMsg, lMsg.length);
            receiveds = null;

            try {
                receiveds = new DatagramSocket(IP_port_for_peer_counting);
                while (true) {
                    //for testing
                    //ds.setSoTimeout(100000);
                    receiveds.receive(receivedp);
                    message = new String(lMsg, 0, receivedp.getLength());
                    temp = message.split("#");
                    if (temp[0] != null && temp[1] != null && temp[2] != null && WiFiApName != null) {
                        // 0: source SSID     1: cluster name    2: TTL
                        if (Newcompare(temp[0], WiFiApName) != 0) {
                            // TTL -1
                            temp[2] = String.valueOf(Integer.valueOf(temp[2]) - 1);
                            // update peer table
                            if (Newcompare(temp[1], Cluster_Name) == 0) {
                                PeerTable.put(temp[0], 5);
                            }
                            // relay packet
                            if (Integer.valueOf(temp[2]) > 0) {
                                message = temp[0] + "#" + temp[1] + "#" + temp[2];
                                sendds = null;
                                sendds = new DatagramSocket();
                                // broadcast
                                senddp = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName("192.168.49.255"), IP_port_for_peer_counting);
                                sendds.send(senddp);
                                Log.d("Leaf0419", "(Relay)Send the message: " + message + " to broadcast");
                                // unicast
                                iterator = IPTable.keySet().iterator();
                                while (iterator.hasNext()) {
                                    tempkey = iterator.next().toString();
                                    senddp = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName(tempkey), IP_port_for_peer_counting);
                                    sendds.send(senddp);
                                    Log.d("Leaf0419", "(Relay)Send the message: " + message + " to " + tempkey);
                                }
                                sendds.close();
                            }

                        }
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            } finally {
                if (receiveds != null) {
                    receiveds.close();
                }
                if (sendds != null) {
                    sendds.close();
                }
            }
        }
    }

    // EditLeaf0812
    public int count_peer() {
        //By Leaf
        int result = 0;
        Iterator iterator = PeerTable.keySet().iterator();
        String tempkey;
        while (iterator.hasNext()) {
            tempkey = iterator.next().toString();
            result++;
        }
        Log.d("Leaf0419", "The peer count result is : " + result);
        //By Serval Mesh
        /*try {
            result = ServalDCommand.peerCount();
        }catch (ServalDFailureException e) {
            e.printStackTrace();
        }*/
        return result;
    }

    // EditLeaf 0812
    public class Send_peer_count extends Thread {
        private DatagramPacket dp;
        private DatagramSocket sendds;
        private Iterator iterator;
        private String message, tempkey;

        public void run() {
            try {
                sendds = null;
                sendds = new DatagramSocket();
                while (true) {
                    try {
                        if (Isconnect) {
                            message = WiFiApName + "#" + Cluster_Name + "#" + "5";
                            // broadcast
                            dp = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName("192.168.49.255"), IP_port_for_peer_counting);
                            sendds.send(dp);
                            Log.d("Leaf0419", "(Proactive)Send the message: " + message + " to broadcast");
                            // unicast
                            iterator = IPTable.keySet().iterator();
                            while (iterator.hasNext()) {
                                tempkey = iterator.next().toString();
                                dp = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName(tempkey), IP_port_for_peer_counting);
                                sendds.send(dp);
                                Log.d("Leaf0419", "(Proactive)Send the message: " + message + " to " + tempkey);
                            }

                            //update peer table
                            iterator = PeerTable.keySet().iterator();
                            while (iterator.hasNext()) {
                                tempkey = iterator.next().toString();
                                PeerTable.put(tempkey, PeerTable.get(tempkey) - 1);
                                if (PeerTable.get(tempkey) <= 0) {
                                    PeerTable.remove(tempkey);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Thread.sleep(500);
                }
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            } finally {
                if (sendds != null) {
                    sendds.close();
                }
            }
        }
    }

    @Override
    public void onCreate() {
        // Leaf0818
        Log.d("Leaf1110", "Control_onCreate()");
        this.app = (ServalBatPhoneApplication) this.getApplication();
        PowerManager pm = (PowerManager) app
                .getSystemService(Context.POWER_SERVICE);
        cpuLock = pm
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Services");
        super.onCreate();

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        // Leaf1201
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        // Leaf1202
        mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        // Leaf0616
        registerReceiver(receiver_scan = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                wifi_scan_results = wifi.getScanResults();
                result_size = wifi_scan_results.size();
                Log.d("Leaf0419", "State: detecting gateway, get the scan result" + wifi_scan_results.toString());
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        // Experiment
        NumRound = 1;
        sleep_time = 0;
        total_time = 0;
        start_time = Calendar.getInstance().getTimeInMillis();

    }

    @Override
    public void onDestroy() {
        Log.d("Leaf1110", "Control Services Destroy");
        new Task().execute(State.Off);
        app.controlService = null;
        serviceRunning = false;
        if (receiver != null)
            unregisterReceiver(receiver);
        if (receiver_scan != null)
            unregisterReceiver(receiver_scan);
        isRunning = false;
        if (t_findPeer != null)
            t_findPeer.interrupt();
        if (t_checkGO != null)
            t_checkGO.interrupt();
        if (t_reconnection_wifiAp != null)
            t_reconnection_wifiAp.interrupt();
        if (t_collectIP != null)
            t_collectIP.interrupt();
        if (t_send_peer_count != null)
            t_send_peer_count.interrupt();
        if (t_receive_peer_count != null)
            t_receive_peer_count.interrupt();

        // <aqua0722>
        if (t_native != null)
            t_native.interrupt();
        if (t_register != null)
            t_register.interrupt();

        t_native = null;
        t_register = null;
        // </aqua0722>
        receiver = null;
        t_findPeer = null;
        t_checkGO = null;
        t_reconnection_wifiAp = null;
        t_collectIP = null;
        t_send_peer_count = null;
        t_receive_peer_count = null;
        if (receiveds != null)
            receiveds.close();
        try {
            if (sc != null)
                sc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (manager != null && serviceInfo != null && serviceRequest != null) {
            manager.removeLocalService(channel, serviceInfo, null);
            manager.removeServiceRequest(channel, serviceRequest, null);
            manager.clearLocalServices(channel, null);
            manager.clearServiceRequests(channel, null);
        }

        // EditLeaf0802
        try {
            if (ss != null)
                ss.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Leaf0818
        Log.d("Leaf1110", "Control Services StartCommand");
        State existing = app.getState();
        // Don't attempt to start the service if the current state is invalid
        // (ie Installing...)
        if (existing != State.Off && existing != State.On) {
            Log.v("Control", "Unable to process request as app state is "
                    + existing);
            return START_NOT_STICKY;
        }
        if (receiver == null) {
            receiver = new AutoWiFiDirect(manager, channel, this, Isconnect, myDeviceName);
            registerReceiver(receiver, intentFilter);
        }
        isRunning = true;
        if (t_reconnection_wifiAp == null) {
            t_reconnection_wifiAp = new Reconnection_wifiAp();
            t_reconnection_wifiAp.start();
        }
        if (t_collectIP == null) {
            t_collectIP = new CollectIP_server();
            t_collectIP.start();
        }
        // Following two threads is for counting peers by our module,
        // since Serval Mesh has already supported a similar function,
        // you can decide whether utilized following code
        /*if (t_send_peer_count == null) {
            t_send_peer_count = new Send_peer_count();
            t_send_peer_count.start();
        }
        if (t_receive_peer_count == null) {
            t_receive_peer_count = new Receive_peer_count();
            t_receive_peer_count.start();
        }*/
        // <aqua0722>
        if (t_native == null) {
            t_native = new NativeCall();
            t_native.start();
        }
        if (t_register == null) {
            t_register = new RegisterToServer();
            t_register.start();
        }
        // </aqua0722>
        new Task().execute(State.On);
        serviceRunning = true;
        STATE = StateFlag.DETECTGAW.getIndex();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    public class MyBinder extends Binder {
        public Control getService() {
            return Control.this;
        }
    }


    // Following code is for setting static IP address
    private boolean setIpWithTfiStaticIp(String IP) {
        WifiConfiguration wifiConfig = null;
        WifiInfo connectionInfo = wifi.getConnectionInfo();

        List<WifiConfiguration> configuredNetworks = wifi.getConfiguredNetworks();
        for (WifiConfiguration conf : configuredNetworks) {
            if (conf.networkId == connectionInfo.getNetworkId()) {
                wifiConfig = conf;
                break;
            }
        }
        try {
            setIpAssignment("STATIC", wifiConfig);
            setIpAddress(InetAddress.getByName(IP), 24, wifiConfig);
            wifi.updateNetwork(wifiConfig); // apply the setting
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void setIpAssignment(String assign, WifiConfiguration wifiConf)
            throws SecurityException, IllegalArgumentException,
            NoSuchFieldException, IllegalAccessException {
        setEnumField(wifiConf, assign, "ipAssignment");
    }


    private static void setIpAddress(InetAddress addr, int prefixLength,
                                     WifiConfiguration wifiConf) throws SecurityException,
            IllegalArgumentException, NoSuchFieldException,
            IllegalAccessException, NoSuchMethodException,
            ClassNotFoundException, InstantiationException,
            InvocationTargetException {
        Object linkProperties = getField(wifiConf, "linkProperties");
        if (linkProperties == null)
            return;
        Class<?> laClass = Class.forName("android.net.LinkAddress");
        Constructor<?> laConstructor = laClass.getConstructor(new Class[]{
                InetAddress.class, int.class});
        Object linkAddress = laConstructor.newInstance(addr, prefixLength);


        ArrayList<Object> mLinkAddresses = (ArrayList<Object>) getDeclaredField(
                linkProperties, "mLinkAddresses");
        mLinkAddresses.clear();
        mLinkAddresses.add(linkAddress);
    }

    private static Object getField(Object obj, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getField(name);
        Object out = f.get(obj);
        return out;
    }

    private static Object getDeclaredField(Object obj, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object out = f.get(obj);
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnumField(Object obj, String value, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getField(name);
        f.set(obj, Enum.valueOf((Class<Enum>) f.getType(), value));
    }
}
