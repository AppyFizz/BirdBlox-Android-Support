package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.bluetooth.BluetoothHelper;
import com.birdbraintechnologies.birdblocks.bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblocks.bluetooth.UARTSettings;
import com.birdbraintechnologies.birdblocks.devices.Hummingbird;
import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;

/**
 * Class for handling requests from the router to Hummingbird devices
 *
 * @author Terence Sun (tsun1215)
 * @author Shreyan Bakshi (AppyFizz)
 */
public class HummingbirdRequestHandler implements RequestHandler {
    private static final String TAG = "HBRequestHandler";

    /* UUIDs for different Hummingbird features */
    private static final String HUMMINGBIRD_DEVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    // TODO: Remove this, it is the same across devices
    private static final UUID RX_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private HttpService service;
    private UARTSettings hbUARTSettings;
    private BluetoothHelper btHelper;
    private HashMap<String, Hummingbird> connectedDevices;

    public HummingbirdRequestHandler(HttpService service) {
        this.service = service;
        this.btHelper = service.getBluetoothHelper();
        this.connectedDevices = new HashMap<>();

        // Build UART settings
        this.hbUARTSettings = (new UARTSettings.Builder())
                .setUARTServiceUUID(UART_UUID)
                .setRxCharacteristicUUID(RX_UUID)
                .setTxCharacteristicUUID(TX_UUID)
                .setRxConfigUUID(RX_CONFIG_UUID)
                .build();
    }


    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        // Generate response body
        String responseBody = "";
        switch (path[0]) {
            case "discover":
                responseBody = listDevices();
                break;
            case "totalStatus":
                responseBody = getTotalStatus();
                break;
            case "stopDiscover":
                responseBody = stopDiscover();
                break;
            case "connect":
                responseBody = connectToDevice(m.get("id").get(0));
                break;
            case "disconnect":
                responseBody = disconnectFromDevice(m.get("id").get(0));
                break;
            case "out":
                getDeviceFromId(m.get("id").get(0)).setOutput(path[1], m);
                responseBody = "Connected to Hummingbird successfully.";
                break;
            case "in":
                responseBody = getDeviceFromId(m.get("id").get(0)).readSensor(m.get("sensor").get(0), m.get("port").get(0));
                break;
//                case "rename":
//                    responseBody = renameDevice(path[0], path[2]);
//                    break;
        }

        // Create response from the responseBody
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    /**
     * Lists the Hummingbird devices that are seen
     *
     * @return List of Hummingbird devices
     */
    private synchronized String listDevices() {
        // List<BluetoothDevice> deviceList = btHelper.scanDevices(generateDeviceFilter());
        new Thread() {
            @Override
            public void run() {
                btHelper.scanDevices(generateDeviceFilter());
            }
        }.run();
        // btHelper.scanDevices(generateDeviceFilter());
        List<BluetoothDevice> deviceList = (new ArrayList<>(btHelper.deviceList.values()));
        // TODO: Change this behavior to display correctly on device
        JSONArray devices = new JSONArray();
        for (BluetoothDevice device : deviceList) {
            JSONObject humm = new JSONObject();
            try {
                humm.put("id", device.getAddress());
                humm.put("name", device.getName());
            } catch (JSONException e) {
                Log.e("JSON", "JSONException while discovering hummingbirds");
            }
            devices.put(humm);
        }
        return devices.toString();
    }

    /**
     * Extracts the MAC address from the deviceId
     *
     * @param deviceId Id of the device
     * @return MAC address of the device
     */
//    private String extractMAC(String deviceId) {
//        Matcher match = Pattern.compile("^.*\\(([\\w\\:]+)\\)").matcher(deviceId);
//        if (match.matches()) {
//            return match.group(1);
//        }
//        return "";
//    }

    /**
     * Finds a deviceId in the list of connected devices. Null if it does not exist.
     *
     * @param deviceId Device ID to find
     * @return The connected device if it exists, null otherwise
     */
    private Hummingbird getDeviceFromId(String deviceId) {
        return connectedDevices.get(deviceId);
    }

    /**
     * Connects to the device and creates a Hummingbird instance in the list of connected devices
     *
     * @param deviceId Device ID to connect to
     * @return No Response
     */
    private String connectToDevice(String deviceId) {
        // TODO: Handle errors when connecting to device
        UARTConnection conn = btHelper.connectToDeviceUART(deviceId, this.hbUARTSettings);
        if (conn != null) {
            Hummingbird device = new Hummingbird(conn);
            connectedDevices.put(deviceId, device);
        }
        return "";
    }

//    private String renameDevice(String deviceId, String newName) {
//        Hummingbird device = getDeviceFromId(deviceId);
//        if (device != null) {
//            device.rename(newName);
//        }
//        return "";
//    }

    /**
     * Disconnects from a given device
     *
     * @param deviceId Device ID of the device to disconnect from
     * @return No Response
     */
    private String disconnectFromDevice(String deviceId) {
        Hummingbird device = getDeviceFromId(deviceId);
        if (device != null) {
            Log.d(TAG, "Disconnecting from device: " + deviceId);
            device.disconnect();
            Log.d("TotStat", "Removing device: " + deviceId);
            connectedDevices.remove(deviceId);
        }
        Log.d("TotStat", "Connected Hummingbirds: " + connectedDevices.toString());
        return "Hummingbird disconnected successfully.";
    }

    /**
     * Creates a bluetooth scan device filter that only matches Hummingbird devices
     *
     * @return List of scan filters
     */
    private List<ScanFilter> generateDeviceFilter() {
        ScanFilter hbScanFilter = (new ScanFilter.Builder())
                .setServiceUuid(ParcelUuid.fromString(HUMMINGBIRD_DEVICE_UUID))
                .build();
        List<ScanFilter> hummingbirdDeviceFilters = new ArrayList<>();
        hummingbirdDeviceFilters.add(hbScanFilter);
        return hummingbirdDeviceFilters;
    }

    /**
     * Returns the aggregated status of all connected Hummingbirds. 0 means at least 1 device is
     * disconnected; 1 means that all devices are OK, 2 means that no devices are connected.
     *
     * @return 0, 1, or 2 depending on the aggregate status of all the devices
     */
    private String getTotalStatus() {
        Log.d("TotStat", "Connected Devices: " + connectedDevices.toString());
        if (connectedDevices.size() == 0) {
            return "2";  // No devices connected
        }
        for (Hummingbird device : connectedDevices.values()) {
            if (!device.isConnected()) {
                return "0";  // Some device is disconnected
            }
        }
        return "1";  // All devices are OK
    }

    /**
     *
     *
     */
    private String stopDiscover() {
        if (btHelper != null)
            btHelper.stopScan();
        return "Bluetooth discovery stopped.";
    }
}
