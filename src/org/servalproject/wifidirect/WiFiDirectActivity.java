/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.servalproject.wifidirect;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.servalproject.R;
import org.servalproject.servaldna.ChannelSelector;
import org.servalproject.wifidirect.DeviceListFragment.DeviceActionListener;

import java.util.HashMap;
import java.util.Map;

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
public class WiFiDirectActivity extends Activity implements ChannelListener, DeviceActionListener {

    public static final String TAG = "wifidirectdemo";
    private WifiP2pManager manager;
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;
    public TextView text;
    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;
    final HashMap<String, String> buddies = new HashMap<String, String>();
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private WifiP2pDnsSdServiceInfo serviceInfo;
    private int debug_flag = 0;
    private Handler mServiceBroadcastingHandler;

    private int test = 0;

    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_direct);

        // add necessary intent values to be matched.

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        text = (TextView) findViewById(R.id.textView);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        
        /*Bundle bundle=getIntent().getExtras();
        String MAC= null;
        MAC = bundle.get("MAC").toString();
        if( MAC != null) {
        	WifiP2pConfig p2pconfig = new WifiP2pConfig();
        	p2pconfig.groupOwnerIntent = 0;
        	p2pconfig.deviceAddress = MAC;
        	p2pconfig.wps.setup = WpsInfo.PBC;
        	connect(p2pconfig);
        }*/
        
        //startRegistration();
        //discoverService();
    }

    private void discoverService() {
        manager.setDnsSdResponseListeners(channel,
                new WifiP2pManager.DnsSdServiceResponseListener() {
                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                                                        String registrationType, WifiP2pDevice srcDevice) {
                        Log.d("Leaf0419", "onBonjourServiceAvailable ");
                    }
                }, new WifiP2pManager.DnsSdTxtRecordListener() {
                    @Override
                    public void onDnsSdTxtRecordAvailable(
                            String fullDomainName, Map<String, String> record,
                            WifiP2pDevice device) {
                        Log.d("Leaf0419", "DnsSdTxtRecord available -" + device.toString());
                    }
                });
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
    }

    private void startRegistration() {
        //  Create a string map containing information about your service.
        Map record = new HashMap();
        record.put("listenport", String.valueOf(5555));
        record.put("buddyname", "John Doe" + test);
        record.put("available", "visible");
        Log.d("Leaf0419", "startRegistration " + record.toString());
        serviceInfo = WifiP2pDnsSdServiceInfo.newInstance("Wi-Fi_Info", "_presence._tcp", record);
        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                manager.addLocalService(channel, serviceInfo,
                        new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                // service broadcasting started
                                Log.d("Leaf0419", "addLocalService onSuccess");
                            }

                            @Override
                            public void onFailure(int error) {
                                Log.d("Leaf0419", "addLocalService onFailure");
                            }
                        });
            }

            @Override
            public void onFailure(int error) {
                Log.d("Leaf0419", "clearLocalServices onFailure");
            }
        });
    }

    /**
     * register the BroadcastReceiver with the intent values to be matched
     */
    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }


    @Override
    public void onDestroy() {

        /*if (manager != null && serviceInfo != null && serviceRequest != null) {
            manager.removeLocalService(channel, serviceInfo, null);
            manager.removeServiceRequest(channel, serviceRequest, null);
            manager.clearLocalServices(channel, null);
            manager.clearServiceRequests(channel, null);
        }*/
        super.onDestroy();
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
        DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_list);
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        if (fragmentDetails != null) {
            fragmentDetails.resetViews();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_items, menu);
        return true;
    }


    private void startServiceDiscovery() {
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
                                                        Log.d("Leaf0419", "discoverServices onSuccess");
                                                    }
                                                    @Override
                                                    public void onFailure(int error) {
                                                        Log.d("Leaf0419", "discoverServices onFailure");
                                                    }
                                                });
                                    }
                                    @Override
                                    public void onFailure(int error) {
                                        Log.d("Leaf0419", "addServiceRequest onFailure");
                                    }
                                });
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.d("Leaf0419", "removeServiceRequest onFailure");
                    }
                });
    }
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.atn_direct_enable:
                //test++;
                    if (manager != null && channel != null) {
                        manager.createGroup(channel, null);
                    } else {
                        Log.e(TAG, "channel or manager is null");
                    }
                return true;

            case R.id.atn_direct_discover:
                if (!isWifiP2pEnabled) {
                    Toast.makeText(WiFiDirectActivity.this, R.string.p2p_off_warning,
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager()
                        .findFragmentById(R.id.frag_list);
                //fragment.onInitiateDiscovery();
                //startServiceDiscovery();
                manager.stopPeerDiscovery(channel,new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d("Leaf0419", "stopPeerDiscovery onSuccess");
                        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

                            @Override
                            public void onSuccess() {
                                Log.d("Leaf0419", "discoverPeers onSuccess");
                            }

                            @Override
                            public void onFailure(int reasonCode) {
                                Log.d("Leaf0419", "discoverPeers onFailure");
                            }
                        });
                        /*manager.addServiceRequest(channel, serviceRequest,
                                new WifiP2pManager.ActionListener() {
                                    @Override
                                    public void onSuccess() {
                                        manager.discoverServices(channel,
                                                new WifiP2pManager.ActionListener() {
                                                    @Override
                                                    public void onSuccess() {
                                                        Log.d("Leaf0419", "discoverServices onSuccess");
                                                    }

                                                    @Override
                                                    public void onFailure(int error) {
                                                        Log.d("Leaf0419", "discoverServices onFailure");
                                                    }
                                                });
                                    }

                                    @Override
                                    public void onFailure(int error) {
                                        Log.d("Leaf0419", "addServiceRequest onFailure");
                                    }
                                });*/
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Log.d("Leaf0419", "stopPeerDiscovery onFailure");
                    }
                });

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void showDetails(WifiP2pDevice device) {
        Log.d("WifiDirectActivity", "Leaf_showDetails");
        DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.showDetails(device);

    }

    @Override
    public void connect(WifiP2pConfig config) {
        manager.connect(channel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // Leaf0716
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now
            	Log.d("Wang", "p2p connect success");
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(WiFiDirectActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void disconnect() {
        final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.resetViews();

        manager.removeGroup(channel, new ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
            }

            @Override
            public void onSuccess() {
                if (fragment != null)
                    if (fragment.getView() != null)
                        fragment.getView().setVisibility(View.GONE);
            }

        });
    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            resetData();
            text.setText("No Ap Passwd");
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void cancelDisconnect() {

        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (manager != null) {
            final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager()
                    .findFragmentById(R.id.frag_list);
            if (fragment.getDevice() == null
                    || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
                    || fragment.getDevice().status == WifiP2pDevice.INVITED) {

                manager.cancelConnect(channel, new ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(WiFiDirectActivity.this, "Aborting connection",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(WiFiDirectActivity.this,
                                "Connect abort request failed. Reason Code: " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

    }
}
