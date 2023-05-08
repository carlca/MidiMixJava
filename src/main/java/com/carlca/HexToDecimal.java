package com.carlca;

import java.util.*;

public class HexToDecimal {

    public static void main(String[] args) {

        Hashtable<Integer, Integer> fxAHash = makeHash(0);
        Hashtable<Integer, Integer> fxBHash = makeHash(1);
        Hashtable<Integer, Integer> fxCHash = makeHash(2);
        Hashtable<Integer, Integer> volumes = makeHash(3);

        System.out.println(fxAHash.get(0x2E));
        System.out.println(fxBHash.get(0x33));
        System.out.println(fxCHash.get(0x12));
        System.out.println(volumes.get(0x3D));

    }

    static private Hashtable<Integer, Integer> makeHash(int offset) {
        int[] base = {0x10, 0x14, 0x18, 0x1C, 0x2E, 0x32, 0x36, 0x3A};
        Hashtable<Integer, Integer> hash = new Hashtable<>();
        for (int i = 0; i < base.length; i++)
            hash.put(base[i] + offset, i);
        return hash;
    }

}