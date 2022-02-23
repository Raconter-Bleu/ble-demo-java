package cn.eciot.ble_demo_java;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    static class DeviceInfo {
        String name;
        int rssi;
        DeviceInfo(String name,int rssi){
            this.name = name;
            this.rssi = rssi;
        }
    }
    static class Adapter extends ArrayAdapter<DeviceInfo> {
//        private Context myContext;
        private int myResource;
        public Adapter(@NonNull Context context, int resource, List<DeviceInfo> deviceListData) {
            super(context, resource,deviceListData);
//            myContext = context;
            myResource = resource;
        }
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            DeviceInfo deviceInfo = getItem(position);
            String name = "";
            int rssi = 0;
            if(deviceInfo!=null) {
                name = deviceInfo.name;
                rssi = deviceInfo.rssi;
            }
            @SuppressLint("ViewHolder") View view = LayoutInflater.from(getContext()).inflate(myResource,parent,false);
            ImageView headImg = view.findViewById(R.id.iv_type);
            if (name.substring(0, 1).equals("@") && (name.length() == 11) ) {
                headImg.setImageResource(R.drawable.ecble);
            } else {
                headImg.setImageResource(R.drawable.ble);
            }
            ((TextView)view.findViewById(R.id.tv_name)).setText(name);
            ((TextView)view.findViewById(R.id.tv_rssi)).setText((""+rssi));
            ImageView rssiImg = view.findViewById(R.id.iv_rssi);
            if(rssi >= -41) rssiImg.setImageResource(R.drawable.s5);
            else if(rssi >= -55) rssiImg.setImageResource(R.drawable.s4);
            else if(rssi >= -65) rssiImg.setImageResource(R.drawable.s3);
            else if(rssi >= -75) rssiImg.setImageResource(R.drawable.s2);
            else rssiImg.setImageResource(R.drawable.s1);
            return view;
        }
    }

    List<DeviceInfo> deviceListData = new ArrayList<>();
    ListView listView = null;
    Adapter listViewAdapter = null;
    ProgressDialog connectDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        uiInit();
        permissionsInit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        deviceListData.clear();
        listViewAdapter.notifyDataSetChanged();
        bluetoothInit();
    }

    @Override
    protected void onStop() {
        super.onStop();
        ECBLE.stopBluetoothDevicesDiscovery();
    }

    public void uiInit(){
        SwipeRefreshLayout swipRefreshLayout = findViewById(R.id.swipe_layout);
        swipRefreshLayout.setColorSchemeColors(0x01a4ef);
        swipRefreshLayout.setOnRefreshListener(()->{
            deviceListData.clear();
            listViewAdapter.notifyDataSetChanged();
            ECBLE.stopBluetoothDevicesDiscovery();
            new Handler().postDelayed(()->{
                swipRefreshLayout.setRefreshing(false);
                bluetoothInit();
            },1000);
        });

        ListView listView = findViewById(R.id.list_view);
        listViewAdapter = new Adapter(this, R.layout.list_item, deviceListData);
        listView.setAdapter(listViewAdapter);
        listView.setOnItemClickListener((AdapterView<?> adapterView, View view, int i, long l)->{
            showConnectDialog();
            ECBLE.easyConnect(this,deviceListData.get(i).name,(boolean ok , int errCode)->{
                hideConnectDialog();
                if (ok) {
                    runOnUiThread(()->{
                        startActivities(new Intent[]{new Intent().setClass(this, DeviceActivity.class)});
                    });

                } else {
                    showToast("Connect fail");
                }
            });
        });
        listRefresh();
    }

    public void listRefresh(){
        new Handler().postDelayed(()->{
            listViewAdapter.notifyDataSetChanged();
            listRefresh();
        },500);
    }

    public void showAlert(String title,String content,Runnable callback){
        runOnUiThread(()->{
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(content)
                    .setPositiveButton("OK",  (dialogInterface , i)->{
                        new Thread(callback).start();
                    })
                    .setCancelable(false)
                    .create().show();
        });
    }

    public void showConnectDialog(){
        runOnUiThread(()->{
            if(connectDialog==null){
                connectDialog = new ProgressDialog(MainActivity.this);
                connectDialog.setMessage("connecting...");
            }
            connectDialog.show();
        });
    }

    public void hideConnectDialog(){
        runOnUiThread(()->{
            connectDialog.dismiss();
        });
    }

    public void showToast(String text){
        runOnUiThread(()->{
            Toast.makeText(this,text,Toast.LENGTH_SHORT).show();
        });
    }

    public void bluetoothInit() {
        int res = ECBLE.bluetoothAdapterInit(this);
        if (res == 1) {
            showAlert("warn", "The device does not support Bluetooth",()->{
                System.exit(0);//退出app
            });
            return;
        }
        if (res == 2) {
            showAlert("warn", "The locate function switch is off",()->{
                System.exit(0);//退出app
            });
            return;
        }
        if (res == 3) {
            showAlert("warn", "bluetooth is off",()->{
                System.exit(0);//退出app
            });
            return;
        }

        ECBLE.startBluetoothDevicesDiscovery((String name, int rssi)->{
            runOnUiThread(()->{
                Log.e("Discovery", name + "|" + rssi);
                boolean isExist = false;
                for (DeviceInfo item : deviceListData) {
                    if( item.name.equals(name) ) {
                        item.rssi = rssi;
                        isExist = true;
                        break;
                    }
                }
                if (!isExist) {
                    deviceListData.add(new DeviceInfo(name, rssi));
                }
            });
        });
    }

    public void permissionsInit(){
        String[] perms = {Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION};
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, do the thing
            Log.e("permissions","ok");
        } else {
            // 没有权限，进行权限请求
            EasyPermissions.requestPermissions(this,"permissions initialize",0, perms);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        new AppSettingsDialog.Builder(this)
                .setTitle("warn")
                .setRationale("Grant permission,Restart App")
                .build().show();
    }

    @AfterPermissionGranted(0)
    private void requestPermissions() {
        showAlert("warn", "Permission OK, Restart App",()->{
            System.exit(0);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE){
            // Do something after user returned from app settings screen, like showing a Toast.
            showAlert("warn","Restart App",()->{
                System.exit(0);
            });
        }
    }
}