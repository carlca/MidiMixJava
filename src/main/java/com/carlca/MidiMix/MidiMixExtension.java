package com.carlca.MidiMix;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.callback.ShortMidiMessageReceivedCallback;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Transport;
import com.bitwig.extension.controller.ControllerExtension;
import com.carlca.logger.*;
import com.carlca.config.*;
import org.javatuples.*;

public class MidiMixExtension extends ControllerExtension {

    protected MidiMixExtension(final MidiMixExtensionDefinition definition, final ControllerHost host) {
        super(definition, host);
    }
    
    private Transport mTransport;
    private HashMap<Integer, Integer> mTracks;
    private HashMap<Integer, Integer> mTypes;
    private Stack<Integer> mPending;
    private Sender log;
    
    private static final int TRACK_1 = 0x10;
    private static final int TRACK_2 = 0x14;
    private static final int TRACK_3 = 0x18;
    private static final int TRACK_4 = 0x1C;
    private static final int TRACK_5 = 0x2E;
    private static final int TRACK_6 = 0x32;
    private static final int TRACK_7 = 0x36;
    private static final int TRACK_8 = 0x3A;
    private static final int MAST_MIDI = 0x3E;
    private static final int SEND_A = 0;
    private static final int SEND_B = 1;
    private static final int SEND_C = 2;
    private static final int VOLUME = 3;
    private static final int MASTER = 0xFF;


    @Override
    public void init() {
        final ControllerHost host = getHost();

        mTransport = host.createTransport();
        host.getMidiInPort(0).setMidiCallback((ShortMidiMessageReceivedCallback) this::onMidi0);
        host.getMidiInPort(0).setSysexCallback(this::onSysex0);

        // TODO: Perform your driver initialization here.
        // For now just show a popup notification for verification that it is running.
        host.showPopupNotification("MidiMix Initialized");

        mTracks = new HashMap<>();
        mTracks.putAll(makeTrackHash(SEND_A));
        mTracks.putAll(makeTrackHash(SEND_B));
        mTracks.putAll(makeTrackHash(SEND_C));
        mTracks.putAll(makeTrackHash(VOLUME));
        mTracks.put(MAST_MIDI, MASTER);

        mTypes = new HashMap<>();
        mTypes.putAll(makeTypeHash(SEND_A));
        mTypes.putAll(makeTypeHash(SEND_B));
        mTypes.putAll(makeTypeHash(SEND_C));
        mTypes.putAll(makeTypeHash(VOLUME));
        mTypes.put(MAST_MIDI, VOLUME);

        mPending = new Stack<>();
    }

    private HashMap<Integer, Integer> makeTrackHash(int offset) {
        int[] base = {TRACK_1, TRACK_2, TRACK_3, TRACK_4, TRACK_5, TRACK_6, TRACK_7, TRACK_8};
        HashMap<Integer, Integer> hash = new HashMap<>();
        for (int i = 0; i < base.length; i++)
            hash.put(base[i] + offset, i);
        return hash;
    }

    private HashMap<Integer, Integer> makeTypeHash(int offset) {
        int[] base = {TRACK_1, TRACK_2, TRACK_3, TRACK_4, TRACK_5, TRACK_6, TRACK_7, TRACK_8};
        HashMap<Integer, Integer> hash = new HashMap<>();
        for (int i : base) hash.put(i + offset, offset);
        return hash;
    }

    @Override
    public void exit() {
        // TODO: Perform any cleanup once the driver exits
        // For now just show a popup notification for verification that it is no longer running.
        getHost().showPopupNotification("MidiMix Exited");
    }

    @Override
    public void flush() {
        // TODO Send any updates you need here.
    }

    /**
     * Called when we receive short MIDI message on port 0.
     */
    private void onMidi0(ShortMidiMessage msg) {
        if (msg.isControlChange())
            processControlChange(msg);
        else if (msg.isNoteOff())
            processNoteOff(msg);
        else if (msg.isNoteOn())
            processNoteOn(msg);
    }

    private String data1Hex(ShortMidiMessage msg) {
        return String.format("%1$02X", msg.getData1());
    }

    private String data2Hex(ShortMidiMessage msg) {
        return String.format("%1$02X", msg.getData2());
    }

    private void processControlChange(ShortMidiMessage msg) {
        int track = mTracks.get(msg.getData1());
        int type = mTypes.get(msg.getData1());
        int value = msg.getData2();

        log = new Sender();

        getHost().println(String.format("Track: %d  Type: %d  Value: %d", track, type, value));

//        String configRoot = ConfigFolder.getConfigFolderRoot();
//        String shortName = PackageName.getShortName();
//        String fullName = PackageName.getFullName();
//        Path shortHome = Paths.get(configRoot, shortName);
//        Path fullHome = Paths.get(configRoot, fullName);
//        System.out.printf("Short: %s\n", shortHome);
//        System.out.printf("Full: %s\n", fullHome);
    }

    private void processNoteOff(ShortMidiMessage msg) {
        if (!mPending.empty()) {
            processPending(mPending.pop());
        }
        if (!mPending.empty()) {
            throw new NonEmptyStackException();
        }
    }

    private void processPending(int pending) {
        getHost().println(String.format("pair processed: %d", pending));
        // TODO: Finish Button processing
        // TODO: Think about paging
        // TODO: Work out API inputs
        // TODO: Work out how to handle folders
    }

    private void processNoteOn(ShortMidiMessage msg) {
        mPending.push(msg.getData1());
    }

    /**
     * Called when we receive sysex MIDI message on port 0.
     */
    private void onSysex0(final String data) {
        // MMC Transport Controls:
        switch (data) {
            case "f07f7f0605f7":
                mTransport.rewind();
                break;
            case "f07f7f0604f7":
                mTransport.fastForward();
                break;
            case "f07f7f0601f7":
                mTransport.stop();
                break;
            case "f07f7f0602f7":
                mTransport.play();
                break;
            case "f07f7f0606f7":
                mTransport.record();
                break;
        }
    }
}
