package com.carlca.midiMix.utils;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;

public class StringUtils {

    public static String data1Hex(ShortMidiMessage msg) {
        return String.format("%1$02X", msg.getData1());
    }

    public static String data2Hex(ShortMidiMessage msg) {
        return String.format("%1$02X", msg.getData2());
    }
}
