package com.example.hongyi.metawearprototype;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
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
import org.w3c.dom.Text;

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

    private static final String LOG_TAG = "MetawearPrototype", ACCEL_DATA_1 = "Accel_1 Data", ACCEL_DATA_2 = "Accel_2 Data", LOG_ERR = "http_err";
    private static final int ONGOING_NOTIFICATION_ID = 247;
    private static final int REQUEST_ENABLE_BT = 1;
    private MetaWearBleService.LocalBinder ServiceBinder;
    private final String SENSOR_MAC_1 = "D7:06:C0:09:F7:7F", SENSOR_MAC_2 = "E1:B1:1A:7D:8C:35";
    private MetaWearBoard mxBoard_1, mxBoard_2;
    private Accelerometer accelModule_1,accelModule_2;
    private float sampleFreq = 12.5f;
    private int uploadCount = (int) (8 * sampleFreq);
    private float sampleInterval = 1000 / sampleFreq;
    private int[] datacount = {0, 0};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        accelModule_1.stop();
        accelModule_2.stop();
        accelModule_1.disableAxisSampling();
        accelModule_2.disableAxisSampling();
        mxBoard_1.disconnect();
        mxBoard_2.disconnect();
        super.onDestroy();
        getApplicationContext().unbindService(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public void changeText(int num, final String text) {
        TextView tv = null;
        if (num == 1) {
            tv = (TextView) findViewById(R.id.textView_01);
        } else if (num == 2) {
            tv = (TextView) findViewById(R.id.textView_02);
        }
        final TextView finalTv = tv;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                finalTv.setText(text);
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
        BluetoothDevice btDevice_1 = btAdapter.getRemoteDevice(SENSOR_MAC_1);
        BluetoothDevice btDevice_2 = btAdapter.getRemoteDevice(SENSOR_MAC_2);

        mxBoard_1 = ServiceBinder.getMetaWearBoard(btDevice_1);
        mxBoard_1.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
            @Override
            public void failure(int status, Throwable error) {
                mxBoard_1.connect();
            }

            @Override
            public void connected() {
                final long[] startTimestamp1 = new long[1];
//                Log.i(LOG_TAG, "Board_1 connected");
                changeText(1, "Sensor 1: Connected, Streaming Data");
                final String devicename = SENSOR_MAC_1.replace(":", "");
                try {
                    accelModule_1 = mxBoard_1.getModule(Accelerometer.class);
                    accelModule_1.setOutputDataRate(sampleFreq);
                    final ArrayList<String> cache = new ArrayList<String>();

                    accelModule_1.routeData().fromAxes().stream(ACCEL_DATA_1).commit()
                            .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                @Override
                                public void success(RouteManager result) {
                                   result.subscribe(ACCEL_DATA_1, new RouteManager.MessageHandler() {
                                       @Override
                                       public void process(Message message) {
                                           datacount[0] += 1;
                                           long timestamp = startTimestamp1[0] + (long) (datacount[0] * sampleInterval);
                                           double timestamp_in_seconds = timestamp / 1000.0;
                                           CartesianFloat result = message.getData(CartesianFloat.class);
                                           float x = result.x();
                                           int x_int = (int) (x * 1000);
                                           float y = result.y();
                                           int y_int = (int) (y * 1000);
                                           float z = result.z();
                                           int z_int = (int) (z * 1000);
//                                           httpTransmission(pointTime, devicename, x, y, z);
//                                           getDataAsync task = new getDataAsync();
//                                           task.execute(Long.toString(pointTime),devicename, String.valueOf(x), String.valueOf(y), String.valueOf(z));
                                           cache.add(String.format("%.3f", timestamp_in_seconds) + "," + String.valueOf(x_int) +
                                                   "," + String.valueOf(y_int) + "," + String.valueOf(z_int));
                                           Log.i(ACCEL_DATA_1, String.valueOf(datacount[0]));
                                           if (cache.size() == uploadCount) {
                                               ArrayList<String> temp = (ArrayList<String>) cache.clone();
                                               cache.clear();
                                               startTimestamp1[0] = System.currentTimeMillis();
                                               datacount[0] = 0;
                                               String jsonstr = getJSON(devicename, temp);
                                               postDataAsync task = new postDataAsync();
                                               task.execute(jsonstr);
                                           }
                                       }
                                   });
                                }
                            });
                } catch (UnsupportedModuleException e) {
                    Log.i(LOG_TAG, "Cannot find accel_module_1", e);
                }
                accelModule_1.enableAxisSampling();
                startTimestamp1[0] = System.currentTimeMillis();
                datacount[0] = 0;
                accelModule_1.start();
            }

            @Override
            public void disconnected() {
                Log.i(LOG_TAG, "Board_1 disconnected");
            }
        });
        mxBoard_2 = ServiceBinder.getMetaWearBoard(btDevice_2);
        mxBoard_2.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
            @Override
            public void failure(int status, Throwable error) {
                mxBoard_2.connect();
            }

            @Override
            public void connected() {
//                Log.i(LOG_TAG, "Board_2 connected");
                changeText(2, "Sensor 2: Connected, Streaming Data");
                final String devicename = SENSOR_MAC_2.replace(":", "");
                final long[] startTimestamp2 = new long[1];
                try {
                    accelModule_2 = mxBoard_2.getModule(Accelerometer.class);
                    accelModule_2.setOutputDataRate(sampleFreq);
                    final ArrayList<String> cache = new ArrayList<String>();

                    accelModule_2.routeData().fromAxes().stream(ACCEL_DATA_2).commit()
                            .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                @Override
                                public void success(RouteManager result) {
                                    result.subscribe(ACCEL_DATA_2, new RouteManager.MessageHandler() {
                                        @Override
                                        public void process(Message message) {
                                            datacount[1] += 1;
                                            long timestamp = startTimestamp2[0] + (long) (datacount[1] * sampleInterval);
                                            double timestamp_in_seconds = timestamp / 1000.0;
                                            CartesianFloat result = message.getData(CartesianFloat.class);
                                            float x = result.x();
                                            int x_int = (int) (x * 1000);
                                            float y = result.y();
                                            int y_int = (int) (y * 1000);
                                            float z = result.z();
                                            int z_int = (int) (z * 1000);
//                                            httpTransmission(pointTime, devicename, x, y, z);
//                                            getDataAsync task = new getDataAsync();
//                                            task.execute(Long.toString(pointTime),devicename, String.valueOf(x), String.valueOf(y), String.valueOf(z));
                                            cache.add(String.format("%.3f", timestamp_in_seconds) + "," + String.valueOf(x_int) +
                                                    "," + String.valueOf(y_int) + "," + String.valueOf(z_int));
                                            if (cache.size() == uploadCount) {
                                                ArrayList<String> temp = (ArrayList<String>) cache.clone();
                                                cache.clear();
                                                startTimestamp2[0] = System.currentTimeMillis();
                                                datacount[1] = 0;
                                                String jsonstr = getJSON(devicename, temp);
                                                postDataAsync task = new postDataAsync();
                                                task.execute(jsonstr);
                                            }
                                            Log.i(ACCEL_DATA_2, String.valueOf(datacount[1]));
                                        }
                                    });
                                }
                            });
                } catch (UnsupportedModuleException e) {
                    Log.i(LOG_TAG, "Cannot find accel_module_2", e);
                }
                accelModule_2.enableAxisSampling();
                startTimestamp2[0] = System.currentTimeMillis();
                datacount[1] = 0;
                accelModule_2.start();
            }

            @Override
            public void disconnected() {
                Log.i(LOG_TAG, "Board_2 disconnected");
            }
        });

        mxBoard_1.connect();
        mxBoard_2.connect();
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

    private class postDataAsync extends AsyncTask<String, Boolean, String> {
        String urlbase = "https://app.silverlink247.com/sensor_logs";
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

//    private class getDataAsync extends AsyncTask<String, Boolean, String> {
//        String urlbase = "http://192.168.137.1/getdata.php?";
//        @Override
//        protected String doInBackground(String... params) {
//            String myurl = urlbase + "t=" + params[0] +"&d=" + params[1] +
//                    "&x=" + params[2] + "&y=" + params[3] + "&z=" + params[4];
//            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
//            NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
//            if (netInfo != null && netInfo.isConnected()) {
//                try {
//                    URL url = new URL(myurl);
//                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//                    conn.setRequestMethod("GET");
//                    conn.connect();
//                    int responseCode = conn.getResponseCode();
//                    if (responseCode == 200) {
//
//                    } else {
//                        Log.e(LOG_ERR, "Response code is " + responseCode +" on " + myurl);
//                    }
//                } catch (MalformedURLException e) {
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    Log.e(LOG_ERR, "GET failed: " + myurl);
//                }
//            } else {
//                Log.e(LOG_ERR, "No active connection");
//            }
//            return null;
//        }
//    }
//    public void httpTransmission(long t, String d, float x, float y, float z) {
//
//    }
}
