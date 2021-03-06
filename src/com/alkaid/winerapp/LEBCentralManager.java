package com.alkaid.winerapp;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

/**
 * 蓝牙4.0的管理类，包括查找外设，连接外设，传递数据等方法，本类用于连接一个外设，不能连接多个外设
 * 
 * @author jiang
 * 
 */
@SuppressLint("NewApi")
public class LEBCentralManager {

	public static final String TAG = "LEBCentralManager";

	private static LEBCentralManager mInstance;
	private BluetoothManager BLEManager;
	private BluetoothAdapter BLEAdapter;
	private BluetoothDevice BLEDevice;
	private LEBCentralCallback mLEBCentralCallback;

	private BluetoothGatt BLEGatt;// 当前连接的设备

	public ArrayList<BluetoothDevice> BLEDevices = null;

	private LEBCentralManager() {
	}

	private static Context mContext;

	private Handler mHandler = new Handler();

	private boolean isEnableReq = false;// 是否可以读和写,当false的话 阻塞不允许做读写操作
	private boolean isConnect = false;

	// 常量
	private static final long SCAN_PERIOD = 5000;// 查找外设期限
	private static final int RE_CONNECT_DEVICE_TIMES = 4;// 连接设备失败重试次数

	// 管理类单例
	public static LEBCentralManager getInstance(Context mContext) {
		if (mInstance == null) {
			mInstance = new LEBCentralManager();
		}
		LEBCentralManager.mContext = mContext;
		return mInstance;
	}

	/**
	 * 蓝牙
	 * 
	 * @param mLEBCentralCallback
	 */
	public void operate(LEBCentralCallback mLEBCentralCallback) {
		if (mContext != null) {
			this.BLEManager = (BluetoothManager) mContext
					.getSystemService(Context.BLUETOOTH_SERVICE);
			this.BLEAdapter = BLEManager.getAdapter();
			this.mLEBCentralCallback = mLEBCentralCallback;
			if (BLEDevices == null) {
				BLEDevices = new ArrayList<BluetoothDevice>();
			}
		}
	}

	/** 本机蓝牙是否打开 */
	public boolean isLEBEnable() {
		if (BLEAdapter != null) {
			return BLEAdapter.isEnabled();
		}
		return false;
	}

	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
			if (Constants.D)
				Log.d(TAG,
						"scaned bluetoothDevice name:" + device.getName()
								+ " rssi：" + rssi + " scanRecord:"
								+ scanRecord.toString());

			if (!BLEDevices.contains(device)) {
				BLEDevices.add(device);
			}
		}

	};

	/**
	 * 查找所有外设
	 */
	public void scan() {
		if (BLEAdapter != null) {
			mHandler.postDelayed(new Runnable() {

				@Override
				public void run() {
					BLEAdapter.stopLeScan(mLeScanCallback);
					if (mLEBCentralCallback != null) {
						if (BLEDevices != null && BLEDevices.size() > 0) {
							mLEBCentralCallback.scanSuccessCallback(BLEDevices);
						} else {
							mLEBCentralCallback.scanNoDeviceCallback();
						}

					}
				}
			}, SCAN_PERIOD);
			BLEAdapter.startLeScan(mLeScanCallback);
			mLEBCentralCallback.startScanCallback();
		}
	}

	/**
	 * 根据uuid查找外设
	 * 
	 * @param uuids
	 */
	public void scan(UUID[] uuids) {
		if (BLEAdapter != null) {
			mHandler.postDelayed(new Runnable() {

				@Override
				public void run() {
					BLEAdapter.stopLeScan(mLeScanCallback);
					if (mLEBCentralCallback != null) {
						mLEBCentralCallback.scanSuccessCallback(BLEDevices);
					}
				}
			}, SCAN_PERIOD);
			BLEAdapter.startLeScan(uuids, mLeScanCallback);
		}
	}

	private int retryConTimes = 0;

	/** 连接某个外设的回调 */
	BluetoothGattCallback BLEGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(
				android.bluetooth.BluetoothGatt gatt, int status, int newState) {
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				retryConTimes = 0;
				if (Constants.D)
					Log.d(TAG, "device connected");
				isConnect = true;
				isEnableReq = true;
				BLEGatt.discoverServices();
				mLEBCentralCallback.deviceConnectedCallback(gatt);
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				reset();
				// retryConTimes++;
				if (Constants.D)
					Log.d(TAG, "device disconnected");
				// if (retryConTimes > RE_CONNECT_DEVICE_TIMES) {
				// retryConTimes = 0;
				mLEBCentralCallback
						.deviceDisConnectedCallback(gatt.getDevice());
				// }
			}
		};

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			Log.d(TAG, "找到服务了");
		};

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if (!isConnect) {
				return;
			}
			String hexStr = Utils.byteArrayToHex(characteristic.getValue());
			Log.d(TAG, "read" + hexStr + " " + status);
			mLEBCentralCallback.responseReadOrWrite();
			isEnableReq = true;
			super.onCharacteristicRead(gatt, characteristic, status);
		}

		public void onCharacteristicWrite(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if (!isConnect) {
				return;
			}
			String hexStr = Utils.byteArrayToHex(characteristic.getValue());
			Log.d(TAG, "write" + hexStr + " " + status);
			mLEBCentralCallback.responseReadOrWrite();
			isEnableReq = true;
			super.onCharacteristicWrite(gatt, characteristic, status);
		};
		
		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			if (!isConnect) {
				return;
			}
			String hexStr = Utils.byteArrayToHex(characteristic.getValue());
			Log.d(TAG, "notify" + hexStr + " ");
			mLEBCentralCallback.onCharacteristicChanged(characteristic.getValue());
			isEnableReq = true;
			super.onCharacteristicChanged(gatt, characteristic);
		}

	};

	public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (isConnectDevice()) {
			if (Constants.D)
				Log.d(TAG, "current device is not exist");
			mLEBCentralCallback.deviceDisConnectedCallback(BLEDevice);
			return;
		}
		if (!isEnableReq) {
			// 阻塞请求
			return;
		}
		BLEGatt.readCharacteristic(characteristic);
		isEnableReq = false;
		mLEBCentralCallback.requestReadOrWrite();
	}

	/** 发送 外设character */
	public void writeCharacteristic(byte[] data) {
		if (!isConnectDevice()) {
			mLEBCentralCallback.deviceDisConnectedCallback(BLEDevice);
			if (Constants.D)
				Log.d(TAG, "current device is not exist");
			return;
		}
		if (!isEnableReq) {
			// 阻塞请求
			return;
		}
		ArrayList<BluetoothGattCharacteristic> characteristics = getCharacteristic();
		for (BluetoothGattCharacteristic characteristic : characteristics) {
			byte[] values = data;
			characteristic.setValue(values);
			BLEGatt.writeCharacteristic(characteristic);
		}
		isEnableReq = false;
		mLEBCentralCallback.requestReadOrWrite();
	}

	/** 发送所有Characteristic */
	public ArrayList<BluetoothGattCharacteristic> getCharacteristic() {
		if (BLEAdapter == null || BLEGatt == null) {
			if (Constants.D)
				Log.d(TAG, "current device is not exist");
			return null;
		}
		ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<BluetoothGattCharacteristic>();
		List<BluetoothGattService> services = BLEGatt.getServices();
		for (int i = 0; i < services.size(); i++) {
			BluetoothGattService gattService = services.get(i);
			List<BluetoothGattCharacteristic> characteristics = gattService
					.getCharacteristics();
			for (BluetoothGattCharacteristic bluetoothGattCharacteristic : characteristics) {
				charas.add(bluetoothGattCharacteristic);
				BLEGatt.setCharacteristicNotification(bluetoothGattCharacteristic, true);
			}
		}
		return charas;
	}

	/**
	 * 连接某个外设
	 * 
	 * @param device
	 */
	public void connect(BluetoothDevice device) {
		if (Constants.D)
			Log.d(TAG, "device start connected");
		mLEBCentralCallback.startDeviceConnectCallback(device);
		BLEDevice = device;
		BLEGatt = device.connectGatt(mContext, false, BLEGattCallback);
	}

	/**
	 * 查看是否有连接到设备
	 * 
	 * @return
	 */
	public boolean isConnectDevice() {
		if (BLEManager != null
				&& BLEAdapter != null
				&& BLEDevice != null
				&& BLEGatt != null) {
			return true;
		} else {
			return false;
		}
	}
	
	public void reset(){
		if(BLEGatt!=null){
			BLEGatt.disconnect();
			BLEGatt = null;
		}
		BLEDevice = null;
		BLEDevices = null;
		isEnableReq = false;
		isConnect =false;

	}

}
