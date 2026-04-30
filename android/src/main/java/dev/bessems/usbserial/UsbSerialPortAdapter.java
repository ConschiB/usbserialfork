package dev.bessems.usbserial;

import android.hardware.usb.UsbDeviceConnection;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.markone.usbserial.driver.UsbSerialPort;
import com.markone.usbserial.util.SerialInputOutputManager;

import java.io.IOException;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

public class UsbSerialPortAdapter implements MethodCallHandler, EventChannel.StreamHandler {
    private final String TAG = UsbSerialPortAdapter.class.getSimpleName();

    private final int m_InterfaceId;
    private final UsbDeviceConnection m_Connection;
    private final UsbSerialPort m_Port;
    private final BinaryMessenger m_Messenger;
    private final String m_MethodChannelName;
    private final Handler m_Handler;

    private EventChannel.EventSink m_EventSink;
    private SerialInputOutputManager m_IoManager;

    private static final int WRITE_TIMEOUT_MS = 1000;

    UsbSerialPortAdapter(
            BinaryMessenger messenger,
            int interfaceId,
            UsbDeviceConnection connection,
            UsbSerialPort port
    ) {
        m_Messenger = messenger;
        m_InterfaceId = interfaceId;
        m_Connection = connection;
        m_Port = port;
        m_MethodChannelName = "usb_serial/UsbSerialPortAdapter/" + interfaceId;
        m_Handler = new Handler(Looper.getMainLooper());

        final MethodChannel channel = new MethodChannel(m_Messenger, m_MethodChannelName);
        channel.setMethodCallHandler(this);

        final EventChannel eventChannel =
                new EventChannel(m_Messenger, m_MethodChannelName + "/stream");
        eventChannel.setStreamHandler(this);
    }

    String getMethodChannelName() {
        return m_MethodChannelName;
    }

    private Boolean open() {
        try {
            m_Port.open(m_Connection);

            m_IoManager = new SerialInputOutputManager(
                    m_Port,
                    new SerialInputOutputManager.Listener() {
                        @Override
                        public void onNewData(byte[] data) {
                            if (m_EventSink == null) return;

                            m_Handler.post(() -> {
                                if (m_EventSink != null) {
                                    m_EventSink.success(data);
                                }
                            });
                        }

                        @Override
                        public void onRunError(Exception e) {
                            Log.w(TAG, "Serial IO manager stopped: " + e.getMessage(), e);
                        }
                    }
            );

            m_IoManager.start();

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Could not open serial port", e);
            return false;
        }
    }

    private Boolean close() {
        try {
            if (m_IoManager != null) {
                m_IoManager.stop();
                m_IoManager = null;
            }
        } catch (Exception ignored) {}

        try {
            m_Port.close();
        } catch (Exception ignored) {}

        try {
            m_Connection.close();
        } catch (Exception ignored) {}

        return true;
    }

    private void write(byte[] data) throws IOException {
        // Synchronous write. For your drawer this is perfect.
        m_Port.write(data, WRITE_TIMEOUT_MS);
    }

    private void setPortParameters(
            int baudRate,
            int dataBits,
            int stopBits,
            int parity
    ) throws IOException {
        m_Port.setParameters(baudRate, dataBits, stopBits, parity);
    }

    private void setFlowControl(int flowControl) throws IOException {
        UsbSerialPort.FlowControl mapped;

        switch (flowControl) {
            case 1:
                mapped = UsbSerialPort.FlowControl.RTS_CTS;
                break;

            case 2:
                mapped = UsbSerialPort.FlowControl.DTR_DSR;
                break;

            case 3:
                mapped = UsbSerialPort.FlowControl.XON_XOFF;
                break;

            case 0:
            default:
                mapped = UsbSerialPort.FlowControl.NONE;
                break;
        }

        m_Port.setFlowControl(mapped);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        try {
            switch (call.method) {
                case "open":
                    result.success(open());
                    break;

                case "close":
                    result.success(close());
                    break;

                case "write":
                    write((byte[]) call.argument("data"));
                    result.success(true);
                    break;

                case "setPortParameters":
                    setPortParameters(
                            (int) call.argument("baudRate"),
                            (int) call.argument("dataBits"),
                            (int) call.argument("stopBits"),
                            (int) call.argument("parity")
                    );
                    result.success(null);
                    break;

                case "setFlowControl":
                    setFlowControl((int) call.argument("flowControl"));
                    result.success(null);
                    break;

                case "setDTR": {
                    boolean value = call.argument("value");
                    m_Port.setDTR(value);
                    result.success(null);
                    break;
                }

                case "setRTS": {
                    boolean value = call.argument("value");
                    m_Port.setRTS(value);
                    result.success(null);
                    break;
                }

                default:
                    result.notImplemented();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Serial port operation failed: " + call.method, e);
            result.error(TAG, e.getMessage(), null);
        }
    }

    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        m_EventSink = eventSink;
    }

    @Override
    public void onCancel(Object o) {
        m_EventSink = null;
    }
}