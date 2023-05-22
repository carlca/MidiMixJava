package com.carlca.midiMix.utils;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;

public class StringUtils {

    public static String data1Hex(ShortMidiMessage msg) {
        return String.format("%1$02X", msg.getData1());
    }

    public static String data2Hex(ShortMidiMessage msg) {
        return String.format("%1$02X", msg.getData2());
    }

    public static String unadornedClassName(Object objClass) {
        String className = objClass.getClass().getName().substring(6);
        // Remove the package name
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex != -1) {
            className = className.substring(lastDotIndex + 1);
        }
        // Remove any proxy-related suffixes
        int proxyIndex = className.indexOf("Proxy");
        if (proxyIndex != -1) {
            className = className.substring(0, proxyIndex);
        }
        return className;
    }
}
