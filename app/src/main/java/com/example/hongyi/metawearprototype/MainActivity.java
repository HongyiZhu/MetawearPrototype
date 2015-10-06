package com.example.hongyi.metawearprototype;

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
import android.view.View;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.module.Accelerometer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements ServiceConnection{

    private static final String LOG_TAG = "MetawearPrototype", ACCEL_DATA_1 = "Accel_1 Data", ACCEL_DATA_2 = "Accel_2 Data", LOG_ERR = "http_err";
    private MetaWearBleService.LocalBinder ServiceBinder;
    private final String SENSOR_MAC_1 = "D7:06:C0:09:F7:7F", SENSOR_MAC_2 = "F6:E0:22:68:49:AF";
    private MetaWearBoard mxBoard_1, mxBoard_2;
    private Accelerometer accelModule_1,accelModule_2;
    private long startTimestamp;
    private float sampleFreq = 12.5f;
    private int minuteCount = (int) (8 * sampleFreq);
    private float sampleInterval = 1000 / sampleFreq;
    private int[] datacount = {0, 0};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class), this, Context.BIND_AUTO_CREATE);

        findViewById(R.id.start_accel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accelModule_1.enableAxisSampling();
                accelModule_2.enableAxisSampling();
                startTimestamp = System.currentTimeMillis();
                datacount[0] = 0;
                datacount[1] = 0;
                accelModule_1.start();
                accelModule_2.start();
            }
        });

        findViewById(R.id.stop_accel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accelModule_1.stop();
                accelModule_2.stop();
                accelModule_1.disableAxisSampling();
                accelModule_2.disableAxisSampling();
            }
        });
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        getApplicationContext().unbindService(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
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

        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothDevice btDevice_1 = btManager.getAdapter().getRemoteDevice(SENSOR_MAC_1);
        BluetoothDevice btDevice_2 = btManager.getAdapter().getRemoteDevice(SENSOR_MAC_2);

        mxBoard_1 = ServiceBinder.getMetaWearBoard(btDevice_1);
        mxBoard_1.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
            @Override
            public void connected() {
                Log.i(LOG_TAG, "Board_1 connected");
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
                                           long timeOffset = (long) (datacount[0] * sampleInterval);
                                           CartesianFloat result = message.getData(CartesianFloat.class);
                                           float x = result.x();
                                           float y = result.y();
                                           float z = result.z();
//                                           httpTransmission(pointTime, devicename, x, y, z);
//                                           getDataAsync task = new getDataAsync();
//                                           task.execute(Long.toString(pointTime),devicename, String.valueOf(x), String.valueOf(y), String.valueOf(z));
                                           cache.add(Long.toString(timeOffset) + "," + String.valueOf(x) +
                                                   "," + String.valueOf(y) + "," + String.valueOf(z));
                                           if (cache.size() == minuteCount) {
                                               StringBuilder data = new StringBuilder();
                                               data.append(devicename);
                                               data.append("@");
                                               data.append(Long.toString(startTimestamp));
                                               data.append("@");
                                               Boolean first = true;
                                               for (String str : cache ) {
                                                   if (first) {
                                                       data.append(str);
                                                       first = false;
                                                   } else {
                                                       data.append(";");
                                                       data.append(str);
                                                   }
                                               }
                                               postDataAsync task = new postDataAsync();
                                               task.execute(data.toString());
                                               cache.clear();
                                           }
                                           Log.i(ACCEL_DATA_1, String.valueOf(datacount[0]));
                                       }
                                   });
                                }
                            });
                } catch (UnsupportedModuleException e) {
                    Log.i(LOG_TAG, "Cannot find accel_module_1", e);
                }
            }

            @Override
            public void disconnected() {
                Log.i(LOG_TAG, "Board_1 disconnected");
            }
        });
        mxBoard_2 = ServiceBinder.getMetaWearBoard(btDevice_2);
        mxBoard_2.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
            @Override
            public void connected() {
                Log.i(LOG_TAG, "Board_2 connected");
                final String devicename = SENSOR_MAC_2.replace(":", "");

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
                                            long timeOffset = (long) (datacount[1] * sampleInterval);
                                            CartesianFloat result = message.getData(CartesianFloat.class);
                                            float x = result.x();
                                            float y = result.y();
                                            float z = result.z();
//                                            httpTransmission(pointTime, devicename, x, y, z);
//                                            getDataAsync task = new getDataAsync();
//                                            task.execute(Long.toString(pointTime),devicename, String.valueOf(x), String.valueOf(y), String.valueOf(z));
                                            cache.add(Long.toString(timeOffset) + "," + String.valueOf(x) +
                                                    "," + String.valueOf(y) + "," + String.valueOf(z));
                                            if (cache.size() == minuteCount) {
                                                StringBuilder data = new StringBuilder();
                                                data.append(devicename);
                                                data.append("@");
                                                data.append(Long.toString(startTimestamp));
                                                data.append("@");
                                                Boolean first = true;
                                                for (String str : cache ) {
                                                    if (first) {
                                                        data.append(str);
                                                        first = false;
                                                    } else {
                                                        data.append(";");
                                                        data.append(str);
                                                    }
                                                }
                                                postDataAsync task = new postDataAsync();
                                                task.execute(data.toString());
                                                cache.clear();
                                            }
                                            Log.i(ACCEL_DATA_2, String.valueOf(datacount[1]));
                                        }
                                    });
                                }
                            });
                } catch (UnsupportedModuleException e) {
                    Log.i(LOG_TAG, "Cannot find accel_module_2", e);
                }
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

    private class postDataAsync extends AsyncTask<String, Boolean, String> {
        String urlbase = "http://192.168.137.1/postdata.php";
        @Override
        protected String doInBackground(String... params) {
            ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
            if (netInfo != null && netInfo.isConnected()) {
                try {
                    URL url = new URL(urlbase);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("content-type", "text/plain; charset=utf-8");
                    conn.setDoOutput(true);

                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                    writer.write(params[0]);
                    writer.flush();
                    writer.close();
                    os.close();

                    conn.connect();

                    int response = conn.getResponseCode();
                    if (response != 200) {
                        Log.e(LOG_ERR, "Post error: " + params[0]);
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
