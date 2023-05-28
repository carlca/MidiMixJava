package com.carlca.midiMix;

import java.io.IOException;
import java.util.*;

import com.bitwig.extension.api.util.midi.ShortMidiMessage;
import com.bitwig.extension.callback.ShortMidiMessageReceivedCallback;
import com.bitwig.extension.controller.api.*;
import com.bitwig.extension.controller.ControllerExtension;
import com.carlca.midiMix.utils.StringUtils;
import com.carlca.utils.*;
import com.carlca.logger.*;
import com.carlca.config.*;

public class MidiMixExtension extends ControllerExtension {

    protected MidiMixExtension(final MidiMixExtensionDefinition definition, final ControllerHost host) {
        super(definition, host);
    }
    
    private Transport mTransport;
    private HashMap<Integer, Integer> mTracks;
    private HashMap<Integer, Integer> mTypes;
    private Stack<Integer> mPending;
    private TrackBank mTrackBank;
    private TrackBank mMainTrackBank;
    private TrackBank mEffectTrackBank;
    private Track mMasterTrack;
    private CursorTrack mCursorTrack;
    private HashMap<Track, Integer> mParentCounts;

    private static final int MAX_TRACKS = 0x10;
    private static final int MAX_SENDS = 0x03;
    private static final int MAX_SCENES = 0x10;
    private static final boolean HAS_FLAT_TRACK_LIST = true;

    private static final int TRACK_1 = 0x10;
    private static final int TRACK_2 = 0x14;
    private static final int TRACK_3 = 0x18;
    private static final int TRACK_4 = 0x1C;
    private static final int TRACK_5 = 0x2E;
    private static final int TRACK_6 = 0x32;
    private static final int TRACK_7 = 0x36;
    private static final int TRACK_8 = 0x3A;
    private static final int[] TRACKS = {TRACK_1, TRACK_2, TRACK_3, TRACK_4, TRACK_5, TRACK_6, TRACK_7, TRACK_8};
    private static final int MAST_MIDI = 0x3E;
    private static final int SEND_A = 0;
    private static final int SEND_B = 1;
    private static final int SEND_C = 2;
    private static final int VOLUME = 3;
    private static final int MASTER = 0xFF;

    @Override
    public void init() {
        final ControllerHost host = getHost();

        Log.init("midiMix");
        Log.cls();

        initTransport(host);
        initOnMidiCallback(host);
        initOnSysexCallback(host);
        initPendingStack();
        initTrackMap();
        initTypeMap();
        initTrackBanks(host);
        initMasterTrack(host);
        initCursorTrack(host);
        initParentTracks(host);
    }

    private void initOnMidiCallback(ControllerHost host) {
        host.getMidiInPort(0).setMidiCallback((ShortMidiMessageReceivedCallback) msg -> {
            try {
                onMidi0(msg);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void initOnSysexCallback(ControllerHost host) {
        host.getMidiInPort(0).setSysexCallback(this::onSysex0);
    }

    private void initPendingStack() {
        mPending = new Stack<>();
    }

    private void initTransport(ControllerHost host) {
        mTransport = host.createTransport();
    }

    private void initTrackMap() {
        mTracks = new HashMap<>();
        mTracks.putAll(makeTrackHash(SEND_A));
        mTracks.putAll(makeTrackHash(SEND_B));
        mTracks.putAll(makeTrackHash(SEND_C));
        mTracks.putAll(makeTrackHash(VOLUME));
        mTracks.put(MAST_MIDI, MASTER);
    }

    private void initTypeMap() {
        mTypes = new HashMap<>();
        mTypes.putAll(makeTypeHash(SEND_A));
        mTypes.putAll(makeTypeHash(SEND_B));
        mTypes.putAll(makeTypeHash(SEND_C));
        mTypes.putAll(makeTypeHash(VOLUME));
        mTypes.put(MAST_MIDI, VOLUME);
    }

    private void initTrackBanks(ControllerHost host) {
        mTrackBank = host.createTrackBank(MAX_TRACKS, MAX_SENDS, MAX_SCENES);
        mMainTrackBank = host.createMainTrackBank(MAX_TRACKS, MAX_SENDS, MAX_SCENES);
        mEffectTrackBank = host.createEffectTrackBank(MAX_SENDS, MAX_SENDS, MAX_SCENES);
        initInterest(mTrackBank);
        initInterest(mMainTrackBank);
        initInterest(mEffectTrackBank);
    }

    private void initMasterTrack(ControllerHost host) {
        mMasterTrack = host.createMasterTrack(0);
    }

    private void initParentTracks(ControllerHost host) {
        mParentCounts = new HashMap<>();
        initParentCountsForOneBank(mTrackBank, host);
        initParentCountsForOneBank(mMainTrackBank, host);
        initParentCountsForOneBank(mEffectTrackBank, host);
    };

    private void initParentCountsForOneBank(TrackBank bank, ControllerHost host) {
        for (int i=0; i<bank.getCapacityOfBank(); i++) {
            Track track = bank.getItemAt(i);
            track.exists().markInterested();
            String trackName = track.name().get();
            Stack<Track> tracks = new Stack<>();
            int count = 0;
            while (track.exists().get()) {
                trackName = track.name().get();
                tracks.push(track);
                track = track.createParentTrack(0, 0);
                count++;
            }
            if (!tracks.isEmpty()) {
                Track lastTrack = tracks.pop();
                mParentCounts.put(lastTrack, count);
            }
        }
    };

    private void initInterest(TrackBank bank) {
        bank.itemCount().markInterested();
        bank.channelCount().markInterested();
        for (int i=0; i<bank.getCapacityOfBank(); i++) {
            Track track = bank.getItemAt(i);
            track.name().markInterested();
            track.isGroup().markInterested();
            track.canHoldNoteData().markInterested();
            track.canHoldAudioData().markInterested();
            track.trackType().markInterested();
            track.position().markInterested();
            track.exists().markInterested();
            Track parent = track.createParentTrack(0, 0);
            parent.name().markInterested();
        };
    }

    private void initCursorTrack(ControllerHost host) {
        mCursorTrack = host.createCursorTrack(1, 0);
    }

    private HashMap<Integer, Integer> makeTrackHash(int offset) {
        HashMap<Integer, Integer> hash = new HashMap<>();
        for (int i = 0; i < TRACKS.length; i++)
            hash.put(TRACKS[i] + offset, i);
        return hash;
    }

    private HashMap<Integer, Integer> makeTypeHash(int offset) {
        HashMap<Integer, Integer> hash = new HashMap<>();
        for (int i : TRACKS) hash.put(i + offset, offset);
        return hash;
    }

    @Override
    public void exit() {
        Log.send("midiMix Exited");
    }

    @Override
    public void flush() {
    }

    /**
     * Called when we receive short MIDI message on port 0.
     */
    private void onMidi0(ShortMidiMessage msg) throws IOException {
        if (msg.isControlChange())
            processControlChange(msg);
        else if (msg.isNoteOff())
            processNoteOff(msg);
        else if (msg.isNoteOn())
            processNoteOn(msg);
    }

    private void processControlChange(ShortMidiMessage msg) {
        int trackNum = mTracks.get(msg.getData1());
        int typeNum = mTypes.get(msg.getData1());
        int valueNum = msg.getData2();

        Log.send("Track: %d  Type: %d  Value: %d", trackNum, typeNum, valueNum);

        double volume = valueNum / 127.0;
        if (trackNum == MASTER) {
            mMasterTrack.volume().set(volume);
        } else {
            Track track = mTrackBank.getItemAt(trackNum);
            track.volume().set(volume);
        }
    }

    private void processNoteOff(ShortMidiMessage msg) throws IOException {
        if (!mPending.empty()) {
            processPending(mPending.pop());
        }
        if (!mPending.empty()) {
            throw new NonEmptyStackException();
        }
    }

    private void processPending(int pending) throws IOException {
        Config config = new Config("midiMix");
//        Log.send("pair processed: %d", pending);
//        Log.send("getConfigFolder: %s", config.getConfigFolder());

        // TODO: Finish Button processing
        // TODO: Think about paging
        // TODO: Work out API inputs
        // TODO: Work out how to handle folders

        Log.line();
        Log.send("mMainTrackBank.itemCount().getAsInt(): %d", mMainTrackBank.itemCount().getAsInt());
        Log.send("mMainTrackBank.getSizeOfBank: %d", mMainTrackBank.getSizeOfBank());
        Log.send("mMainTrackBank.getCapacityOfBank: %d", mMainTrackBank.getCapacityOfBank());
        Log.line();
        Log.send("mTrackBank.itemCount().getAsInt(): %d", mTrackBank.itemCount().getAsInt());
        Log.send("mTrackBank.getSizeOfBank: %d", mTrackBank.getSizeOfBank());
        Log.send("mTrackBank.getCapacityOfBank: %d", mTrackBank.getCapacityOfBank());
        Log.line();
        Log.send("mEffectTrackBank.itemCount().getAsInt(): %d", mEffectTrackBank.itemCount().getAsInt());
        Log.send("mEffectTrackBank.getSizeOfBank: %d", mEffectTrackBank.getSizeOfBank());
        Log.send("mEffectTrackBank.getCapacityOfBank: %d", mEffectTrackBank.getCapacityOfBank());
        Log.line();
        iterateTracks(mMainTrackBank);
        Log.line();
        iterateTracks(mTrackBank);
        Log.line();
        iterateTracks(mEffectTrackBank);
        Log.line();
        iterateTracks();
        Log.line();
    }

    private void iterateTracks() {
        for (int i = 0; i < mTrackBank.getSizeOfBank(); i++) {
            Track track = mTrackBank.getItemAt(i);
            if (track.isGroup().get()) {
                Log.send("Group: %s", track.name().get());
            } else {
                String trackType = track.trackType().get();
                if (!trackType.isEmpty()) {
                    if ("InstrumentAudioEffectMaster".contains(trackType)) {
                        Log.send("%s: %s  Position: %d", trackType, track.name().get(), track.position().get());
                    }
                }
            }
        }
    }

    private void iterateTracks(TrackBank bank) {
        int trackCount = Math.min(bank.itemCount().getAsInt(), bank.getSizeOfBank());
        String bankClass = StringUtils.unadornedClassName(bank);
        Log.send("%s.(Effective)itemCount() %d", bankClass, trackCount);
        for (int i=0; i<trackCount; i++) {
            Track track = bank.getItemAt(i);
            Integer count = mParentCounts.get(track);
            Log.send("Track #: %d, Name: %s, Positions: %d, Parent count: %d",
                    track.name().get(), track.name().isSubscribed(), track.position().get(), count);
        }
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
