package com.intel.distml.util.store;

import com.intel.distml.util.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by yunlong on 12/8/15.
 */
public class DoubleArrayStore extends DataStore {

    public static final int VALUE_SIZE = 8;

    transient KeyCollection localRows;
    transient double[] localData;

    public void init(KeyCollection keys) {
        this.localRows = keys;
        localData = new double[(int)keys.size()];
        for (int i = 0; i < keys.size(); i++)
            localData[i] = 0.0;
    }

    public int indexOf(long key) {
        if (localRows instanceof KeyRange) {
            return (int) (key - ((KeyRange)localRows).firstKey);
        }
        else if (localRows instanceof KeyHash) {
            KeyHash hash = (KeyHash) localRows;
            return (int) ((key - hash.minKey) % hash.hashQuato);
        }

        throw new RuntimeException("Only KeyRange or KeyHash is allowed in server storage");
    }

    public long keyOf(int index) {
        if (localRows instanceof KeyRange) {
            return ((KeyRange)localRows).firstKey + index;
        }
        else if (localRows instanceof KeyHash) {
            KeyHash hash = (KeyHash) localRows;
            return hash.minKey + index * hash.hashQuato;
        }

        throw new RuntimeException("Only KeyRange or KeyHash is allowed in server storage");
    }

    @Override
    public byte[] handleFetch(DataDesc format, KeyCollection rows) {

        KeyCollection keys = localRows.intersect(rows);
        System.out.println("returned element size: " + keys.size() + ", " + format);

        int len = (int) ((format.keySize + VALUE_SIZE) * keys.size());
        byte[] buf = new byte[len];

        Iterator<Long> it = keys.iterator();
        int offset = 0;
        while(it.hasNext()) {
            long k = it.next();

            format.writeKey((Number) k, buf, offset);
            offset += format.keySize;

            double value = localData[indexOf(k)];
            format.writeValue(value, buf, offset);
            offset += VALUE_SIZE;

            System.out.println("key: " + k + ", value: " + value + ", " + offset);
        }

        System.out.println("returned data size: " + buf.length);
        return buf;
    }

    public void handlePush(DataDesc format, byte[] data) {

        int offset = 0;
        while (offset < data.length) {
            long key = format.readKey(data, offset).longValue();
            offset += format.keySize;

            double update = format.readDouble(data, offset);
            offset += VALUE_SIZE;

            localData[indexOf(key)] += update;
        }
    }

    public Iter iter() {
        return new Iter();
    }

    private class Iter {

        int p;

        public Iter() {
            p = -1;
        }

        public boolean hasNext() {
            return p <= localData.length;
        }

        public long key() {
            return keyOf(p);
        }

        public double value() {
            return localData[p];
        }

        public boolean next() {
            p++;
            return p <= localData.length;
        }
    }

}
