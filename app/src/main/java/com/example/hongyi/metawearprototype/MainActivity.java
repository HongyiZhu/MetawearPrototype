package com.example.hongyi.metawearprototype;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.module.Accelerometer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

/**
 * R
 * D7:06:C0:09:F7:7F
 * F6:E0:22:68:49:AF
 *
 * RG
 * E1:B1:1A:7D:8C:35
 * C7:1C:99:0F:9D:00
 */
public class MainActivity extends AppCompatActivity implements ServiceConnection{

    private static final String LOG_TAG = "MetawearPrototype",
            LOG_ERR = "http_err";

    private MetaWearBleService.LocalBinder ServiceBinder;
    private final ArrayList<String> SENSOR_MAC = new ArrayList<>();
    private final ArrayList<BoardObject> boards = new ArrayList<>();

    private void initParams() {
        SENSOR_MAC.add("D7:06:C0:09:F7:7F"); //R
        SENSOR_MAC.add("E1:B1:1A:7D:8C:35"); //RG
        SENSOR_MAC.add("F6:E0:22:68:49:AF"); //R
        SENSOR_MAC.add("F5:AB:48:BC:10:6B"); //RPro
        SENSOR_MAC.add("EA:B2:F1:47:04:E7"); //RPro
//        SENSOR_MAC.add("C7:1C:99:0F:9D:00"); //RG
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class), this, Context.BIND_AUTO_CREATE);
        initParams();
    }

    @Override
    public void onDestroy() {
        for (int i = 0; i < SENSOR_MAC.size(); i++) {
//            accelModule.get(i).stop();
//            accelModule.get(i).disableAxisSampling();
//            mxBoard.get(i).disconnect();
            boards.get(i).accel_module.stop();
            boards.get(i).accel_module.disableAxisSampling();
            boards.get(i).ActiveDisconnect = true;
            boards.get(i).board.disconnect();
        }
//        accelModule_1.stop();
//        accelModule_2.stop();
//        accelModule_1.disableAxisSampling();
//        accelModule_2.disableAxisSampling();
//        mxBoard_1.disconnect();
//        mxBoard_2.disconnect();
        super.onDestroy();
        getApplicationContext().unbindService(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public void changeText(final String MAC, final String text) {
        TextView tv = null;
        TextView tv2 = null;
        if (SENSOR_MAC.size() >= 1 && MAC.equals(SENSOR_MAC.get(0))) {
            tv = (TextView) findViewById(R.id.textView_01);
            tv2 = (TextView) findViewById(R.id.textView_02);
        } else if (SENSOR_MAC.size() >= 2 && MAC.equals(SENSOR_MAC.get(1))) {
            tv = (TextView) findViewById(R.id.textView_11);
            tv2 = (TextView) findViewById(R.id.textView_12);
        } else if (SENSOR_MAC.size() >= 3 && MAC.equals(SENSOR_MAC.get(2))) {
            tv = (TextView) findViewById(R.id.textView_21);
            tv2 = (TextView) findViewById(R.id.textView_22);
        } else if (SENSOR_MAC.size() >= 4 && MAC.equals(SENSOR_MAC.get(3))) {
            tv = (TextView) findViewById(R.id.textView_31);
            tv2 = (TextView) findViewById(R.id.textView_32);
        } else if (SENSOR_MAC.size() >= 5 && MAC.equals(SENSOR_MAC.get(4))) {
            tv = (TextView) findViewById(R.id.textView_41);
            tv2 = (TextView) findViewById(R.id.textView_42);
        }
        final TextView finalTv = tv;
        final TextView finalTv2 = tv2;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                finalTv.setText(text);
                finalTv2.setText(MAC);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ServiceBinder = (MetaWearBleService.LocalBinder) service;

        BluetoothAdapter btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (!btAdapter.isEnabled()) {
            btAdapter.enable();
        }
        while (!btAdapter.isEnabled()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        for (int i = 0; i < SENSOR_MAC.size(); i++){
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            BluetoothDevice btDevice = btAdapter.getRemoteDevice(SENSOR_MAC.get(i));
            boards.add(new BoardObject(ServiceBinder.getMetaWearBoard(btDevice), SENSOR_MAC.get(i), 12.5f));
        }

        for (int i = 0; i < SENSOR_MAC.size();i++){
            changeText(boards.get(i).MAC_ADDRESS, boards.get(i).CONNECTING);
            boards.get(i).board.connect();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    public String getJSON (String name, ArrayList<String> data) {
        JSONObject jsonstring = new JSONObject();
        JSONArray logs = new JSONArray();
        try {
            jsonstring.put("s", name);
            for (String s : data) {
                JSONObject temp = new JSONObject();
                temp.put("t", s.split(",")[0]);
                temp.put("x", s.split(",")[1]);
                temp.put("y", s.split(",")[2]);
                temp.put("z", s.split(",")[3]);
                logs.put(temp);
            }
            jsonstring.put("logs", logs);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonstring.toString();
    }

    public class postDataAsync extends AsyncTask<String, Boolean, String> {
        String urlbase = "http://data.silverlink247.com/logs";
        @Override
        protected String doInBackground(String... params) {
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
            if (netInfo != null && netInfo.isConnected()) {
                try {
                    URL url = new URL(urlbase);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("content-type", "application/json");
                    conn.setDoOutput(true);

                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                    writer.write(params[0]);
                    writer.flush();
                    writer.close();
                    os.close();

                    conn.connect();

                    int response = conn.getResponseCode();
                    if (200 <= response && response < 300) {
                        Log.i(LOG_TAG, "Post succeed: " + params[0]);
                    } else {
                        Log.e(LOG_ERR, "Post error code: " + response + " " + params[0]);
                    }
                } catch (MalformedURLException e) {
                    Log.e(LOG_ERR, "Illegal URL");
                } catch (IOException e) {
                    Log.e(LOG_ERR, "Connection error " + params[0]);
                }
            } else {
                Log.e(LOG_ERR, "No active connection");
            }
            return null;
        }
    }

    public class BoardObject {
        private final String CONNECTED = "Connected.\nStreaming Data",
                DISCONNECTED = "Lost connection.\nReconnecting",
                FAILURE = "Connection error.\nReconnecting",
                CONNECTING = "Connecting",
                LOG_TAG = "Board_Log";
        public MetaWearBoard board;
        public Accelerometer accel_module;
        public long[] startTimestamp;
        public ArrayList<String> dataCache;
        public int dataCount;
        public String MAC_ADDRESS;
        private float sampleFreq;
        private int uploadCount;
        private float sampleInterval;
        public boolean ActiveDisconnect = false;
        private final String devicename;

        public BoardObject(MetaWearBoard mxBoard, final String MAC, float freq) {
            this.board = mxBoard;
            this.MAC_ADDRESS = MAC;
            this.dataCount = 0;
            this.dataCache = new ArrayList<>();
            this.startTimestamp = new long[1];
            this.sampleFreq = freq;
            this.uploadCount = (int) (8 * sampleFreq);
            this.sampleInterval = 1000 / sampleFreq;
            this.devicename = MAC_ADDRESS.replace(":", "");
            final String SENSOR_DATA_LOG = "Data:Sensor:" + MAC_ADDRESS;

            this.board.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
                @Override
                public void connected() {
                    changeText(MAC_ADDRESS, CONNECTED);
                    try {
                        startTimestamp[0] = System.currentTimeMillis();
                        accel_module = board.getModule(Accelerometer.class);
                        accel_module.setOutputDataRate(sampleFreq);
                        accel_module.routeData().fromAxes().stream(SENSOR_DATA_LOG).commit()
                                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                    @Override
                                    public void success(RouteManager result) {
                                        result.subscribe(SENSOR_DATA_LOG, new RouteManager.MessageHandler() {
                                            @Override
                                            public void process(Message message) {
                                                dataCount += 1;
                                                long timestamp = startTimestamp[0] + (long) (dataCount * sampleInterval);
                                                double timestamp_in_seconds = timestamp / 1000.0;
                                                CartesianFloat result = message.getData(CartesianFloat.class);
                                                float x = result.x();
                                                int x_int = (int) (x * 1000);
                                                float y = result.y();
                                                int y_int = (int) (y * 1000);
                                                float z = result.z();
                                                int z_int = (int) (z * 1000);
                                                dataCache.add(String.format("%.3f", timestamp_in_seconds) + "," + String.valueOf(x_int) +
                                                        "," + String.valueOf(y_int) + "," + String.valueOf(z_int));
                                                Log.i(SENSOR_DATA_LOG, String.valueOf(dataCount));
                                                if (dataCache.size() == uploadCount) {
                                                    ArrayList<String> temp = (ArrayList<String>) dataCache.clone();
                                                    dataCache.clear();
                                                    startTimestamp[0] = System.currentTimeMillis();
                                                    dataCount = 0;
                                                    String jsonstr = getJSON(devicename, temp);
                                                    postDataAsync task = new postDataAsync();
                                                    task.execute(jsonstr);
                                                }
                                            }
                                        });
                                    }
                                });
                    } catch (UnsupportedModuleException e) {
                        Log.i(LOG_TAG, "Cannot find sensor:" + MAC_ADDRESS, e);
                    }
                    accel_module.enableAxisSampling();
                    startTimestamp[0] = System.currentTimeMillis();
                    dataCount = 0;
                    accel_module.start();
                }

                @Override
                public void disconnected() {
                    if (dataCache.size() != 0) {
                        ArrayList<String> temp = (ArrayList<String>) dataCache.clone();
                        dataCache.clear();
                        startTimestamp[0] = System.currentTimeMillis();
                        dataCount = 0;
                        String jsonstr = getJSON(devicename, temp);
                        postDataAsync task = new postDataAsync();
                        task.execute(jsonstr);
                    }
                    if (!ActiveDisconnect) {
                        changeText(MAC_ADDRESS, DISCONNECTED);
                        board.connect();
                    }
                }

                @Override
                public void failure(int status, Throwable error) {
                    changeText(MAC_ADDRESS, FAILURE);
                    try {
                        Thread.sleep((long) (3000 * Math.random()));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (dataCache.size() != 0) {
                        ArrayList<String> temp = (ArrayList<String>) dataCache.clone();
                        dataCache.clear();
                        startTimestamp[0] = System.currentTimeMillis();
                        dataCount = 0;
                        String jsonstr = getJSON(devicename, temp);
                        postDataAsync task = new postDataAsync();
                        task.execute(jsonstr);
                    }
                    board.connect();
                }
            });

        }
    }
}