package com.example.hongyi.metawearprototype;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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

public class MainActivity extends AppCompatActivity implements ServiceConnection{

    private static final String LOG_TAG = "MetawearPrototype", ACCEL_DATA_1 = "Accel_1 Data", ACCEL_DATA_2 = "Accel_2 Data";
    private MetaWearBleService.LocalBinder ServiceBinder;
    private final String SENSOR_MAC_1 = "D7:06:C0:09:F7:7F", SENSOR_MAC_2 = "F6:E0:22:68:49:AF";
    private MetaWearBoard mxBoard_1, mxBoard_2;
    private Accelerometer accelModule_1,accelModule_2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class), this, Context.BIND_AUTO_CREATE);

        findViewById(R.id.start_accel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accelModule_1.enableAxisSampling();
                accelModule_2.start();
                accelModule_2.enableAxisSampling();
                accelModule_1.start();
            }
        });

        findViewById(R.id.stop_accel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accelModule_1.stop();
                accelModule_1.disableAxisSampling();
                accelModule_2.stop();
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
                try {
                    accelModule_1 = mxBoard_1.getModule(Accelerometer.class);
                    accelModule_1.setOutputDataRate(12.5f);
                    accelModule_1.routeData().fromAxes().stream(ACCEL_DATA_1).commit()
                            .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                @Override
                                public void success(RouteManager result) {
                                   result.subscribe(ACCEL_DATA_1, new RouteManager.MessageHandler() {
                                       @Override
                                       public void process(Message message) {
                                           Log.i(ACCEL_DATA_1, message.getData(CartesianFloat.class).toString());
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

                try {
                    accelModule_2 = mxBoard_2.getModule(Accelerometer.class);
                    accelModule_2.setOutputDataRate(12.5f);
                    accelModule_2.routeData().fromAxes().stream(ACCEL_DATA_2).commit()
                            .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                                @Override
                                public void success(RouteManager result) {
                                    result.subscribe(ACCEL_DATA_2, new RouteManager.MessageHandler() {
                                        @Override
                                        public void process(Message message) {
                                            Log.i(ACCEL_DATA_2, message.getData(CartesianFloat.class).toString());
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
}
