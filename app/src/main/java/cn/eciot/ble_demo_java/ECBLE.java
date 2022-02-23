package cn.eciot.ble_demo_java;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;

interface ScanCallback{
    void callback(String name, int rssi);
}

interface ConnectCallback{
    void callback(boolean ok , int errCode);
}

interface ConnectionStateChangeCallback{
    void callback(boolean ok);
}

interface GetServicesCallback{
    void callback(List<String> servicesList);
}

interface CharacteristicChangedCallback{
    void callback(String hex,String string);
}

public class ECBLE {
    static class BLEDevice {
        String name;
        int rssi;
        BluetoothDevice bluetoothDevice;
        BLEDevice(String name,int rssi,BluetoothDevice bluetoothDevice){
            this.name = name;
            this.rssi = rssi;
            this.bluetoothDevice = bluetoothDevice;
        }
    }
    static List<BLEDevice> deviceList = new ArrayList<>();
    static BluetoothAdapter bluetoothAdapter = null;
    static ScanCallback scanCallback = (String name, int rssi)->{};
    static BluetoothAdapter.LeScanCallback leScanCallback = (BluetoothDevice bluetoothDevice, int rssi, byte[] bytes)->{
        Log.e("bleDiscovery", bluetoothDevice.getName() + "|" + rssi);
        if(bluetoothDevice.getName()==null){
            return;
        }
        boolean isExist = false;
        for(BLEDevice item : deviceList){
            if (item.name.equals(bluetoothDevice.getName()) ) {
                item.rssi = rssi;
                item.bluetoothDevice = bluetoothDevice;
                isExist = true;
                break;
            }
        }
        if(!isExist){
            deviceList.add(new BLEDevice(bluetoothDevice.getName(), rssi, bluetoothDevice));
        }
        scanCallback.callback(bluetoothDevice.getName(), rssi);
    };
    static boolean scanFlag = false;
    static BluetoothGatt bluetoothGatt = null;
    static BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback(){
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.e("onConnectionStateChange", "status=" + status + "|" + "newState=" + newState);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                connectCallback.callback(false, status);
                connectCallback = (boolean ok , int errCode)->{};
                connectionStateChangeCallback.callback(false);
                connectionStateChangeCallback = (boolean ok)->{};
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                stopBluetoothDevicesDiscovery();
                connectCallback.callback(true, 0);
                connectCallback = (boolean ok , int errCode)->{};
                return;
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bluetoothGatt.close();
                connectCallback.callback(false, 0);
                connectCallback = (boolean ok , int errCode)->{};
                connectionStateChangeCallback.callback(false);
                connectionStateChangeCallback = (boolean ok)->{};
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            bluetoothGatt = gatt;
            List<BluetoothGattService> bluetoothGattServices = gatt.getServices();
            List<String> servicesList = new ArrayList<String>();
            if (bluetoothGattServices == null) getServicesCallback.callback(servicesList);
            else {
                for (BluetoothGattService item : bluetoothGattServices) {
                    Log.e("ble-service", "UUID=:" + item.getUuid().toString());
                    servicesList.add(item.getUuid().toString());
                }
                getServicesCallback.callback(servicesList);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            byte[] bytes = characteristic.getValue();
            if (bytes != null) {
                Log.e("ble-receive", "读取成功[hex]:" + bytesToHexString(bytes));
                Log.e("ble-receive", "读取成功[string]:" + new String(bytes));
                characteristicChangedCallback.callback(bytesToHexString(bytes), new String(bytes));
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (BluetoothGatt.GATT_SUCCESS == status) {
                Log.e("BLEService", "onMtuChanged success MTU = " + mtu);
            } else {
                Log.e("BLEService", "onMtuChanged fail ");
            }
        }
    };
    static ConnectCallback connectCallback = (boolean ok , int errCode)->{};
    static int reconnectTime = 0;
    static ConnectionStateChangeCallback connectionStateChangeCallback = (boolean ok)->{};
    static GetServicesCallback getServicesCallback = (List<String> servicesList)->{};
    static CharacteristicChangedCallback characteristicChangedCallback = (String hex,String string)->{};
    static String ecServerId = "0000FFF0-0000-1000-8000-00805F9B34FB";
    static String ecWriteCharacteristicId = "0000FFF2-0000-1000-8000-00805F9B34FB";
    static String ecReadCharacteristicId = "0000FFF1-0000-1000-8000-00805F9B34FB";

    static private boolean isLocServiceEnable(Context context){
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return gps||network;
    }
    static int bluetoothAdapterInit(Context context){
        if (bluetoothAdapter == null) bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothGatt != null){
            bluetoothGatt.close();
        }
        if (bluetoothAdapter == null) {
            //The device does not support Bluetooth
            return 1;
        }
        if (!isLocServiceEnable(context)) {
            //The locate function switch is off
            return 2;
        }
        if (!getBluetoothAdapterState()) {
            openBluetoothAdapter();
            //bluetooth is off
            return 3;
        }
        return 0;
    }
    static void openBluetoothAdapter(){
        if(bluetoothAdapter!=null){
            bluetoothAdapter.enable();
        }
    }
    static void closeBluetoothAdapter(){
        if(bluetoothAdapter!=null){
            bluetoothAdapter.disable();
        }
    }
    static boolean getBluetoothAdapterState(){
        if(bluetoothAdapter==null)return false;
        return bluetoothAdapter.isEnabled();
    }
    static void startBluetoothDevicesDiscovery(ScanCallback cb){
        scanCallback = cb;
        if (!scanFlag) {
            bluetoothAdapter.startLeScan(leScanCallback);
            scanFlag = true;
        }
    }
    static void stopBluetoothDevicesDiscovery(){
        if (scanFlag) {
            bluetoothAdapter.stopLeScan(leScanCallback);
            scanFlag = false;
        }
    }
    static private void createBLEConnection(Context context,String name,ConnectCallback cb){
        connectCallback = cb;
        connectionStateChangeCallback = (boolean ok)->{};
        boolean isExist = false;
        for (BLEDevice item : deviceList) {
            if (item.name.equals(name)) {
                bluetoothGatt = item.bluetoothDevice.connectGatt(context, false, bluetoothGattCallback);
                isExist = true;
                break;
            }
        }
        if (!isExist) {
            connectCallback.callback(false, -1);
        }
    }
    static void closeBLEConnection(){
        bluetoothGatt.disconnect();
    }
    static private void getBLEDeviceServices(GetServicesCallback cb){
        getServicesCallback = cb;
        bluetoothGatt.discoverServices();
    }
    static List<String> getBLEDeviceCharacteristics(String serviceId){
        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceId));
        List<BluetoothGattCharacteristic> listGattCharacteristic = service.getCharacteristics();
        List<String> characteristicsList = new ArrayList<String>();
        if (listGattCharacteristic == null) return characteristicsList;
        for (BluetoothGattCharacteristic item : listGattCharacteristic) {
            Log.e("ble-characteristic", "UUID=:" + item.getUuid().toString());
            characteristicsList.add(item.getUuid().toString());
        }
        return characteristicsList;
    }

    static private boolean notifyBLECharacteristicValueChange(String serviceId,String characteristicId){
        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceId));
        if(service==null){
            return false;
        }
        BluetoothGattCharacteristic characteristicRead = service.getCharacteristic(UUID.fromString(characteristicId));
        boolean res = bluetoothGatt.setCharacteristicNotification(characteristicRead, true);
        if(!res){
            return false;
        }
        for (BluetoothGattDescriptor dp : characteristicRead.getDescriptors()) {
            dp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(dp);
        }
        return true;
    }

    static void onBLECharacteristicValueChange(CharacteristicChangedCallback cb){
        characteristicChangedCallback = cb;
    }

    static void easyOneConnect(Context context,String name,ConnectCallback cb){
        createBLEConnection(context,name,(boolean ok , int errCode)->{
            Log.e("Connection", "res:" + ok + "|" + errCode);
            if (ok) {
                getBLEDeviceServices((List<String> servicesList)->{
                    for (String item : servicesList) {
                        Log.e("ble-service", "UUID=" + item);
                    }
                    getBLEDeviceCharacteristics(ecServerId);
                    notifyBLECharacteristicValueChange(ecServerId, ecReadCharacteristicId);
                    cb.callback(true,0);
                    new Thread(()->{
                        try {
                            Thread.sleep(300);
                            setMtu(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                });
            } else {
                cb.callback(false,0);
            }
        });
    }
    static void easyConnect(Context context,String name,ConnectCallback cb){
        easyOneConnect(context,name,(boolean ok,int errCode)->{
            if(ok){
                reconnectTime = 0;
                cb.callback(true,0);
            }else{
                reconnectTime = reconnectTime + 1;
                if(reconnectTime>4){
                    reconnectTime = 0;
                    cb.callback(false,0);
                }
                else{
                    new Thread(()->{
                        easyConnect(context,name,cb);
                    }).start();
                }
            }
        });
    }

    static void onBLEConnectionStateChange(ConnectionStateChangeCallback cb){
        connectionStateChangeCallback = cb;
    }

    static void offBLEConnectionStateChange(){
        connectionStateChangeCallback = (boolean ok)->{};
    }

    static void writeBLECharacteristicValue(String serviceId,String characteristicId,String data,boolean isHex){
        byte[] byteArray = isHex?toByteArray(data):data.getBytes();
        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceId));
        BluetoothGattCharacteristic characteristicWrite = service.getCharacteristic(UUID.fromString(characteristicId));
        characteristicWrite.setValue(byteArray);
        //设置回复形式
        characteristicWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        //开始写数据
        bluetoothGatt.writeCharacteristic(characteristicWrite);
    }

    static void easySendData(String data,boolean isHex){
        writeBLECharacteristicValue(ecServerId, ecWriteCharacteristicId, data, isHex);
    }

    static void setMtu(int v){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothGatt.requestMtu(v);
        }
    }

    static String bytesToHexString(byte[] bytes){
        if (bytes == null) return "";
        StringBuilder str = new StringBuilder();
        for (byte b : bytes) {
            str.append(String.format("%02X", b));
        }
        return str.toString();
    }

    static byte[] toByteArray(String hexString){
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i+1), 16));
        }
        return data;
    }
}
