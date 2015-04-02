package com.dataart.btle_android.btle_gateway.gatt.callbacks;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;

import com.dataart.android.devicehive.device.CommandResult;
import com.dataart.btle_android.btle_gateway.GattCharacteristicCallBack;
import com.dataart.btle_android.btle_gateway.future.SimpleCallableFuture;

import java.util.UUID;

import timber.log.Timber;

/**
 * Created by Constantine Mars on 3/27/15.
 * Provides single callback for gatt with configurable actions
 */
public class InteractiveGattCallback extends BluetoothGattCallback {
    private boolean servicesDiscovered = false;
    private BluetoothGatt gatt;
    private ReadCharacteristicOperation readOperation;
    private WriteCharacteristicOperation writeOperation;
    private SimpleCallableFuture<CommandResult> callableFuture;

    public InteractiveGattCallback(SimpleCallableFuture<CommandResult> future) {
        this.callableFuture = future;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Timber.d("connected. discovering services");
            this.gatt = gatt;
            this.gatt.discoverServices();
            callableFuture.call(new CommandResult(CommandResult.STATUS_COMLETED, "Ok"));
        } else {
            Timber.d("connection state:" + newState);
            callableFuture.call(new CommandResult(CommandResult.STATUS_FAILED, "Failed with status=" + status + ", state=" + newState));
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        servicesDiscovered = true;
        if (readOperation!=null) {
            readOperation.call(gatt);
        }
        if (writeOperation!=null) {
            writeOperation.call(gatt);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (readOperation !=null) {
            readOperation.onResult(characteristic, status);
//            Reset readOperation for future calls
            readOperation = null;
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (writeOperation!=null) {
            writeOperation.onResult(characteristic, status);
            writeOperation = null;
        }
    }

    public void readCharacteristic(String serviceUUID, String characteristicUUID, GattCharacteristicCallBack callBack) {
        readOperation = new ReadCharacteristicOperation(serviceUUID, characteristicUUID, callBack);
        if (gatt != null) {
            if (servicesDiscovered) {
//                read right now
                readOperation.call(gatt);
            } else {
//                discoverServices should be called before r/w operations - so we postpone call to readOperation
                gatt.discoverServices();
            }
            return;
        }

        Timber.d("gatt is null - probably not connected");
    }

    public void writeCharacteristic(String serviceUUID, String characteristicUUID, GattCharacteristicCallBack callBack, byte[] value) {
        writeOperation = new WriteCharacteristicOperation(serviceUUID, characteristicUUID, callBack, value);
        if (gatt != null) {
            if (servicesDiscovered) {
//                read right now
                writeOperation.call(gatt);
            } else {
//                discoverServices should be called before r/w operations - so we postpone call to readOperation
                gatt.discoverServices();
            }
            return;
        }

        Timber.d("gatt is null - probably not connected");
    }

    public static class ReadCharacteristicOperation extends CharacteristicOperation {


        public ReadCharacteristicOperation(String serviceUUID, String characteristicUUID, GattCharacteristicCallBack callBack) {
            super(serviceUUID, characteristicUUID, callBack);
        }

        @Override
        protected void request(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            gatt.readCharacteristic(characteristic);
        }

        @Override
        public void onResult(BluetoothGattCharacteristic characteristic, int status) {
            callBack.onRead(characteristic.getValue());
        }
    }

    public static class WriteCharacteristicOperation extends CharacteristicOperation {
        private byte[] value;

        public WriteCharacteristicOperation(String serviceUUID, String characteristicUUID, GattCharacteristicCallBack callBack, byte[] value) {
            super(serviceUUID, characteristicUUID, callBack);
            this.value = value;
        }

        @Override
        protected void request(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            characteristic.setValue(value);
            gatt.writeCharacteristic(characteristic);
        }

        @Override
        public void onResult(BluetoothGattCharacteristic characteristic, int status) {
            callBack.onWrite(status);
        }
    }

    public abstract static class CharacteristicOperation {
        private String serviceUUID;
        private String characteristicUUID;
        protected GattCharacteristicCallBack callBack;

        public CharacteristicOperation(String serviceUUID, String characteristicUUID, GattCharacteristicCallBack callBack) {
            this.serviceUUID = serviceUUID;
            this.characteristicUUID = characteristicUUID;
            this.callBack = callBack;
        }

        public void call(BluetoothGatt gatt) {
            BluetoothGattService service = gatt.getService(UUID.fromString(serviceUUID));
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUUID));
                if (characteristic != null) {
                    request(gatt, characteristic);
                    return;
                }
            }
            Timber.d("failed to read characteristic " + characteristicUUID + " from service " + serviceUUID);
        }

        abstract protected void request(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);

        abstract public void onResult(BluetoothGattCharacteristic characteristic, int status);
    }
}
