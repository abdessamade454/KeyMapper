package io.github.sds100.keymapper.system.devices

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.view.InputDevice
import androidx.core.content.getSystemService
import io.github.sds100.keymapper.system.bluetooth.BluetoothDeviceInfo
import io.github.sds100.keymapper.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import splitties.mainthread.mainLooper

/**
 * Created by sds100 on 13/03/2021.
 */
class AndroidDevicesAdapter(
    context: Context,
    private val bluetoothAdapter: io.github.sds100.keymapper.system.bluetooth.BluetoothAdapter,
    private val coroutineScope: CoroutineScope
) : DevicesAdapter {
    private val ctx = context.applicationContext
    private val inputManager = ctx.getSystemService<InputManager>()

    override val onInputDeviceConnect = MutableSharedFlow<InputDeviceInfo>()
    override val onInputDeviceDisconnect = MutableSharedFlow<InputDeviceInfo>()
    override val connectedInputDevices =
        MutableStateFlow<State<List<InputDeviceInfo>>>(State.Loading)

    override val pairedBluetoothDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())

    override val connectedBluetoothDevices = MutableStateFlow<Set<BluetoothDeviceInfo>>(emptySet())

    init {
        coroutineScope.launch {
            updatePairedBluetoothDevices()
            updateInputDevices()
        }

        coroutineScope.launch {
            merge(
                bluetoothAdapter.onDevicePairedChange,
                bluetoothAdapter.isBluetoothEnabled
            ).collectLatest {
                updatePairedBluetoothDevices()
            }
        }

        inputManager?.apply {
            registerInputDeviceListener(object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    coroutineScope.launch {
                        val device = InputDevice.getDevice(deviceId) ?: return@launch
                        onInputDeviceConnect.emit(device.createModel())

                        updateInputDevices()
                    }
                }

                override fun onInputDeviceRemoved(deviceId: Int) {
                    coroutineScope.launch {
                        connectedInputDevices.value.ifIsData { connectedInputDevices ->
                            val device = connectedInputDevices.find { it.id == deviceId }
                                ?: return@ifIsData

                            onInputDeviceDisconnect.emit(device)
                        }

                        updateInputDevices()
                    }
                }

                override fun onInputDeviceChanged(deviceId: Int) {
                    updateInputDevices()
                }

            }, Handler(mainLooper))
        }

        bluetoothAdapter.onDeviceConnect.onEach { device ->
            val currentValue = connectedBluetoothDevices.value

            connectedBluetoothDevices.value = currentValue.plus(device)
        }.launchIn(coroutineScope)

        bluetoothAdapter.onDeviceDisconnect.onEach { device ->
            val currentValue = connectedBluetoothDevices.value

            connectedBluetoothDevices.value = currentValue.minus(device)
        }.launchIn(coroutineScope)
    }

    override fun deviceHasKey(id: Int, keyCode: Int): Boolean {
        return InputDevice.getDevice(id).hasKeys(keyCode)[0]
    }

    override fun getInputDeviceName(descriptor: String): Result<String> {
        InputDevice.getDeviceIds().forEach {
            val device = InputDevice.getDevice(it)

            if (device.descriptor == descriptor) {
                return Success(device.name)
            }
        }

        return Error.DeviceNotFound(descriptor)
    }

    private fun updateInputDevices() {
        val devices = mutableListOf<InputDeviceInfo>()

        InputDevice.getDeviceIds().forEach {
            val device = InputDevice.getDevice(it) ?: return@forEach

            devices.add(device.createModel())
        }

        connectedInputDevices.value = State.Data(devices)
    }

    private fun updatePairedBluetoothDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()

        if (adapter == null) {
            pairedBluetoothDevices.value = emptyList()
            return
        }

        val devices = adapter.bondedDevices?.mapNotNull { device: BluetoothDevice? ->
            if (device == null) {
                return@mapNotNull null
            }

            if (device.address == null || device.name == null) {
                return@mapNotNull null
            }

            BluetoothDeviceInfo(device.address, device.name)
        }

        pairedBluetoothDevices.value = devices ?: emptyList()
    }

    private fun InputDevice.createModel(): InputDeviceInfo {
        return InputDeviceInfo(
            this.descriptor,
            this.name,
            this.id,
            this.isExternalCompat
        )
    }
}