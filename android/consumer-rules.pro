# Keep all USB serial driver classes - they are looked up via reflection
# by ProbeTable / UsbSerialProber (getMethod("getSupportedDevices"),
# getMethod("probe", UsbDevice.class), and the (UsbDevice) constructor).
-keep class com.markone.usbserial.driver.** { *; }
-keep interface com.markone.usbserial.driver.** { *; }

# Keep the reflected static methods on every UsbSerialDriver implementation,
# even if someone subclasses them outside this package.
-keepclassmembers class * implements com.markone.usbserial.driver.UsbSerialDriver {
    public static java.util.Map getSupportedDevices();
    public static boolean probe(android.hardware.usb.UsbDevice);
    public <init>(android.hardware.usb.UsbDevice);
}

# Plugin entry points called from Flutter / Android USB system.
-keep class dev.bessems.usbserial.** { *; }
