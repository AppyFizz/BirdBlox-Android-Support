package com.birdbraintechnologies.birdblocks.devices;

import android.util.Log;

import com.birdbraintechnologies.birdblocks.bluetooth.MelodySmartConnection;
import com.birdbraintechnologies.birdblocks.util.DeviceUtil;

import java.util.Arrays;

/**
 * Represents a Flutter device and all of its functionality: Setting outputs, reading sensors
 *
 * @author Terence Sun (tsun1215)
 */
public class Flutter {
    private static final String TAG = Flutter.class.getName();

    /* Commands for Flutter */
    private static final String SET_CMD = "s";
    private static final byte READ_CMD = 'r';
    private static final String SERVO_OUTPUT = "s";
    private static final String TRI_LED_R_OUTPUT = "r";
    private static final String TRI_LED_G_OUTPUT = "g";
    private static final String TRI_LED_B_OUTPUT = "b";
    private static final String SET_SERVO_CMD = SET_CMD + SERVO_OUTPUT + "%d,%x";
    private static final String SET_TRI_R_CMD = SET_CMD + TRI_LED_R_OUTPUT + "%d,%x";
    private static final String SET_TRI_G_CMD = SET_CMD + TRI_LED_G_OUTPUT + "%d,%x";
    private static final String SET_TRI_B_CMD = SET_CMD + TRI_LED_B_OUTPUT + "%d,%x";

    private MelodySmartConnection conn;

    /**
     * Initializes a Flutter device
     *
     * @param conn Connection established with the Flutter device
     */
    public Flutter(MelodySmartConnection conn) {
        this.conn = conn;
    }

    /**
     * Sets the output of the given output type according to args
     *
     * @param outputType Type of the output
     * @param args       Arguments for setting the output
     * @return True if the output was successfully set, false otherwise
     */
    public boolean setOutput(String outputType, String[] args) {
        int port = Integer.parseInt(args[1]);
        switch (outputType) {
            case "servo":
                return setServo(port, Integer.parseInt(args[2]));
            case "triled":
                return setTriLED(port, Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                        Integer.parseInt(args[4]));
        }
        return false;
    }

    /**
     * Sets the RGB values of a tri-color LED connected to the given port
     *
     * @param port     Port number that the LED is connected to
     * @param rPercent Percentage [0,100] to set R to
     * @param gPercent Percentage [0,100] to set G to
     * @param bPercent Percentage [0,100] to set B to
     * @return True if the command succeeded, false otherwise
     */
    private boolean setTriLED(int port, int rPercent, int gPercent, int bPercent) {
        byte r = clampToBounds(Math.round(rPercent), 0, 100);
        byte g = clampToBounds(Math.round(gPercent), 0, 100);
        byte b = clampToBounds(Math.round(bPercent), 0, 100);

        boolean check = conn.writeBytes(String.format(SET_TRI_R_CMD, port, r).getBytes());
        check &= conn.writeBytes(String.format(SET_TRI_G_CMD, port, g).getBytes());
        check &= conn.writeBytes(String.format(SET_TRI_B_CMD, port, b).getBytes());
        return check;
    }

    /**
     * Sets the angle of the servo connected to the given port
     *
     * @param port  Port number that the servo is connected to
     * @param angle Percentage [0,100] to set the intensity to
     * @return True if the command succeeded, false otherwise
     */
    private boolean setServo(int port, int angle) {
        byte angleByte = clampToBounds(Math.round(angle * 1.25), 0, 225);
        return conn.writeBytes(String.format(SET_SERVO_CMD, port, angleByte).getBytes());
    }

    /**
     * Reads the value of the sensor at the given port and returns the formatted value according to
     * sensorType
     *
     * @param sensorType Type of sensor connected to the port (dictates format of the returned
     *                   value)
     * @param portString Port that the sensor is connected to
     * @return A string representing the value of the sensor
     */
    public String readSensor(String sensorType, String portString) {
        byte[] responseBytes = conn.writeBytesWithResponse(new byte[]{READ_CMD});
        if (responseBytes[0] != READ_CMD) {
            Log.e(TAG, "Received invalid response to read command: " + Arrays.toString(responseBytes));
        }
        int port = Integer.parseInt(portString);

        // Response is given in percent
        String[] response = new String(responseBytes).split(",");
        String sensorPercent = response[port];

        // Convert so that it is backward compatible with conversion library
        byte rawSensorValue = DeviceUtil.PercentToRaw(Double.parseDouble(sensorPercent));

        switch (sensorType) {
            case "distance":
                return Double.toString(DeviceUtil.RawToDist(rawSensorValue));
            case "temperature":
                return Double.toString(DeviceUtil.RawToTemp(rawSensorValue));
            case "soil":
                return Double.toString(clampToBounds(Double.parseDouble(sensorPercent), 0.0, 90.0));
            case "sound":
            case "light":
            case "sensor":
            default:
                return sensorPercent;
        }
    }

    /**
     * Computes the ascii byte of the port number
     *
     * @param port Integer port to convert
     * @return Ascii representation of the port
     */
    private byte computePort(int port) {
        // TODO: Error handling for invalid ports
        // Adding 48 to a number 0-9 makes it ascii
        return (byte) ((port) + 48);
    }

    /**
     * Returns a value that is bounded by min and max
     *
     * @param value Value to be clamped
     * @param min   Minimum that this value can be
     * @param max   Maximum that this value can be
     * @return Clamped value
     */
    private byte clampToBounds(long value, int min, int max) {
        if (value > max) {
            return (byte) max;
        } else if (value < min) {
            return (byte) min;
        } else {
            return (byte) value;
        }
    }

    private double clampToBounds(double value, double min, double max) {
        if (value > max) {
            return max;
        } else if (value < min) {
            return min;
        } else {
            return value;
        }
    }

    /**
     * Returns whether or not this device is connected
     *
     * @return True if connected, false otherwise
     */
    public boolean isConnected() {
        return conn.isConnected();
    }

    public void rename(String newName) {
        // TODO: Implement renaming
        Log.e(TAG, "Call to unimplemented function: rename(" + newName + ")");
    }

    /**
     * Disconnects the device
     */
    public void disconnect() {
        conn.disconnect();
    }
}
