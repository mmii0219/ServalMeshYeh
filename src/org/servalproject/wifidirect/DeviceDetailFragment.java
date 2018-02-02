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

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import org.servalproject.wifidirect.DeviceListFragment.DeviceActionListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import org.servalproject.R;
/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {
    private int ServerClientflag;
    private static int SwitchFlag = 0;
    private EditText edit1;
    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View mContentView = null;
    private WifiP2pDevice device;
    private static WifiP2pInfo info;
    private WifiP2pManager P2pManager;
    private static String ip;
    ProgressDialog progressDialog = null;
    private static AsyncTask<Void, Void, String> ServerWork;
    private static AsyncTask<Void, Void, String> ClientWork;
    private static ServerSocket serverSocket;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        Log.d("Leaf","Leaf_ onCreateView");
        mContentView = inflater.inflate(R.layout.device_detail, null);
        edit1 = (EditText) mContentView.findViewById(R.id.btn_edit);
        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(edit1.getWindowToken(), 0);
                WifiP2pConfig config = new WifiP2pConfig();
                if(!edit1.getText().toString().equals("")) {
                    config.groupOwnerIntent = Integer.valueOf(edit1.getText().toString());
                }
                else config.groupOwnerIntent = 0;
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
//                        new DialogInterface.OnCancelListener() {
//
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
//                            }
//                        }
                );
                ((DeviceActionListener) getActivity()).connect(config);

            }
        });

        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((DeviceActionListener) getActivity()).disconnect();
                    }
                });

        mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                        // goto onActivityResult
                    }
                });

        return mContentView;
    }
    private static byte[] getLocalIPAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        if (inetAddress instanceof Inet4Address) { // fix for Galaxy Nexus. IPv4 is easy to use :-)
                            return inetAddress.getAddress();
                        }
                        //return inetAddress.getHostAddress().toString(); // Galaxy Nexus returns IPv6
                    }
                }
            }
        } catch (SocketException ex) {
            //Log.e("AndroidNetworkAddressFactory", "getLocalIPAddress()", ex);
        } catch (NullPointerException ex) {
            //Log.e("AndroidNetworkAddressFactory", "getLocalIPAddress()", ex);
        }
        return null;
    }

    private static String getDottedDecimalIP(byte[] ipAddr) {
        //convert to dotted decimal notation:
        String ipAddrStr = "";
        for (int i=0; i<ipAddr.length; i++) {
            if (i > 0) {
                ipAddrStr += ".";
            }
            ipAddrStr += ipAddr[i]&0xFF;
        }
        return ipAddrStr;
    }

    // ip = getDottedDecimalIP(getLocalIPAddress());
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);

        // The owner IP is now known.

        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(getResources().getString(R.string.group_owner_text)
                + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
                : getResources().getString(R.string.no)));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        // Leaf edit 20150604

        Log.d("Leaf", "Leaf__onConnectionInfoAvailable");
        if (info.groupFormed && info.isGroupOwner) {
            Log.d("Leaf", "Leaf__onConnectionInfoAvailable_groupGroupOwner");
            // Leaf edit 20150603
            ServerClientflag = 0;
            //ServerWork = new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text)).execute();
            //mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);

        } else if (info.groupFormed) {
            Log.d("Leaf", "Leaf__onConnectionInfoAvailable_groupFormed");
            ServerClientflag = 1;
            // The other device acts as the client. In this case, we enable the
            // get file button
            //mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
            ((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
                    .getString(R.string.client_text));
            //ClientWork = new ClientIPTransmit(getActivity(), mContentView.findViewById(R.id.status_text)).execute();
        }
        // hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
        mContentView.findViewById(R.id.btn_edit).setVisibility(View.GONE);
    }

    /**
     * Updates the UI with device data
     *
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(device.deviceAddress);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(device.toString());
        Log.d("DeviceDetailFragment", device.toString());
    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        Log.d("Leaf", "Leaf__resetViews()");
        if(serverSocket!=null) {
            if (!serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if(ServerWork!=null)ServerWork.cancel(true);
        if(ClientWork!=null)ClientWork.cancel(true);
        SwitchFlag = 0;
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        mContentView.findViewById(R.id.btn_edit).setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */

    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;

        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                if(SwitchFlag == 0) {
                    serverSocket = new ServerSocket(8980);
                    Log.d(WiFiDirectActivity.TAG, "Server3: Socket opened");
                    Socket client = serverSocket.accept();
                    Log.d(WiFiDirectActivity.TAG, "Stuck");
                    String s = "";
                    ObjectInputStream objectInputStream = new ObjectInputStream(client.getInputStream());
                    Object object = null;
                    try {
                        object = objectInputStream.readObject();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    ip = object.toString();
                    objectInputStream.close();
                    if (!serverSocket.isClosed()) {
                        try {
                            serverSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    SwitchFlag = 1;
                }
                serverSocket = new ServerSocket(8988);
                Log.d(WiFiDirectActivity.TAG, "Server: Socket opened");
                Socket client = serverSocket.accept();

                Log.d(WiFiDirectActivity.TAG, "Server: connection done");
                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");
                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();
                if(serverSocket!=null) {
                    if (!serverSocket.isClosed()) {
                        Log.d(WiFiDirectActivity.TAG, "server: copying files " + f.toString());
                        InputStream inputstream = client.getInputStream();
                        copyFile(inputstream, new FileOutputStream(f));
                        try {
                            client.close();
                            serverSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                //Leaf 20150604 multihop
                String fileUri = "file://" + f.getAbsolutePath();
                String host = ip;
                Socket socket = new Socket();
                int port = 8990;
                try {
                    Log.d(WiFiDirectActivity.TAG, "Opening client socket - ");
                    socket.bind(null);
                    Log.d(WiFiDirectActivity.TAG, ip);
                    socket.connect((new InetSocketAddress(host, port)), 5000);

                    Log.d(WiFiDirectActivity.TAG, "Client socket - " + socket.isConnected());
                    OutputStream stream = socket.getOutputStream();
                    ContentResolver cr = context.getContentResolver();
                    InputStream is = null;
                    try {
                        is = cr.openInputStream(Uri.parse(fileUri));
                    } catch (FileNotFoundException e) {
                        Log.d(WiFiDirectActivity.TAG, e.toString());
                    }
                    DeviceDetailFragment.copyFile(is, stream);
                    Log.d(WiFiDirectActivity.TAG, "Client: Data written");
                } catch (IOException e) {
                    Log.e(WiFiDirectActivity.TAG, e.getMessage());
                } finally {
                    if (socket != null) {
                        if (socket.isConnected()) {
                            try {
                                socket.close();
                            } catch (IOException e) {
                                // Give up
                                e.printStackTrace();
                            }
                        }
                    }
                }
                return f.getAbsolutePath();
            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                return null;
            }catch (Exception e){
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if(result!=null) {
                if (result.equals("setip")) {
                    statusText.setText("Client IP = " + ip);
                } else{
                    Log.d("Leaf", "Leaf__onPoset");
                    statusText.setText("File copied - " + result);
                    Intent intent = new Intent();
                    intent.setAction(android.content.Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse("file://" + result), "image/*");
                    context.startActivity(intent);
                }
            }
        }

        @Override
        protected void onPreExecute() {

        }

    }

    // Leaf edit 20150603
    public static class ClientIPTransmit extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;

        /**
         * @param context
         * @param statusText
         */
        public ClientIPTransmit(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                if(SwitchFlag==0) {
                    Socket socket = new Socket();
                    socket.bind(null);
                    Log.d(WiFiDirectActivity.TAG, "Leaf transmit ip to Server");
                    Log.d(WiFiDirectActivity.TAG, "Leaf __" + info.groupOwnerAddress.getHostAddress());
                    socket.connect((new InetSocketAddress(info.groupOwnerAddress.getHostAddress(), 8980)), 5000);
                    Log.d(WiFiDirectActivity.TAG, "Stuck");
                    OutputStream os = socket.getOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(os);
                    ip = getDottedDecimalIP(getLocalIPAddress());
                    oos.writeObject(ip);
                    oos.close();
                    os.close();
                    socket.close();
                    SwitchFlag = 1;
                }
                serverSocket = new ServerSocket(8990);
                Log.d(WiFiDirectActivity.TAG, "Server: Socket opened");
                Socket client = serverSocket.accept();
                Log.d(WiFiDirectActivity.TAG, "Server: connection done");
                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");
                File dirs = new File(f.getParent());
                if (!serverSocket.isClosed()) {
                    if (!dirs.exists())
                        dirs.mkdirs();
                    f.createNewFile();

                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                Log.d(WiFiDirectActivity.TAG, "server: copying files " + f.toString());
                InputStream inputstream = client.getInputStream();
                copyFile(inputstream, new FileOutputStream(f));
                return f.getAbsolutePath();
            }catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
                return null;
            }catch (Exception e){
                return null;
            }
        }
        @Override
        protected void onPostExecute(String result) {
            if(result!=null) {
                if (result.equals("setip")) {
                    statusText.setText("Client IP = " + ip);
                } else{
                    Log.d("Leaf", "Leaf__onPoset");
                    statusText.setText("File copied - " + result);
                    Intent intent = new Intent();
                    intent.setAction(android.content.Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse("file://" + result), "image/*");
                    context.startActivity(intent);
                }
            }
        }
        @Override
        protected void onPreExecute() {

        }

    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        long startTime=System.currentTimeMillis();

        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
            long endTime=System.currentTimeMillis()-startTime;
            Log.v("","Time taken to transfer all bytes is : "+endTime);

        } catch (IOException e) {
            Log.d(WiFiDirectActivity.TAG, e.toString());
            return false;
        }
        return true;
    }

}
