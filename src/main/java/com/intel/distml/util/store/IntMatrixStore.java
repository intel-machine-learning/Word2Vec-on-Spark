package com.intel.distml.util.store;

import com.intel.distml.util.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by yunlong on 12/8/15.
 */
public class IntMatrixStore extends DataStore {

    public static final int VALUE_SIZE = 4;

    transient KeyCollection localRows;
    transient int[][] localData;

    public void init(KeyCollection keys, int cols) {
        this.localRows = keys;
        localData = new int[(int)keys.size()][cols];

        for (int i = 0; i < keys.size(); i++)
            for (int j = 0; j < cols; j++)
                localData[i][j] = 0;

    }

    @Override
    public byte[] handleFetch(DataDesc format, KeyCollection rows) {

        KeyCollection keys = localRows.intersect(rows);
        byte[] buf;
        if (format.denseColumn) {
            int keySpace = (int) (format.keySize * keys.size());
            int valueSpace = (int) (VALUE_SIZE * keys.size() * localData[0].length);
            buf = new byte[keySpace + valueSpace];
        }
        else {
            int nzcount = 0;
            Iterator<Long> it = keys.iterator();
            while (it.hasNext()) {
                long k = it.next();
                int[] values = localData[indexOf(k)];
                for (int i = 0; i < values.length; i++) {
                    if (values[i] != 0) {
                        nzcount++;
                    }
                }
            }
            int len = (VALUE_SIZE + 4) * nzcount;
            buf = new byte[format.keySize * (int)keys.size() + len];
        }

        Iterator<Long> it = keys.iterator();
        int offset = 0;
        while(it.hasNext()) {
            long k = it.next();
            format.writeKey((Number)k, buf, offset);
            offset += format.keySize;

            int[] values = localData[indexOf(k)];
            if (format.denseColumn) {
                for (int i = 0; i < values.length; i++) {
                    format.writeValue(values[i], buf, offset);
                    offset += VALUE_SIZE;
                }
            }
            else {
                int counterIndex = offset;
                offset += 4;

                int counter = 0;
                for (int i = 0; i < values.length; i++) {
                    if (values[i] != 0) {
                        format.write(i, buf, offset);
                        offset += 4;
                        format.write(values[i], buf, offset);
                        offset += VALUE_SIZE;
                    }

                    counter++;
                }
                format.write(counter, buf, counterIndex);
            }
        }

        return buf;
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

    public void handlePush(DataDesc format, byte[] data) {

        int offset = 0;
        while (offset < data.length) {
            long key = format.readKey(data, offset).longValue();
            offset += format.keySize;
            offset = updateRow(key, data, offset, format);
        }
    }

    private int updateRow(long key, byte[] data, int start, DataDesc format) {
        assert(localRows.contains(key));

        int index = indexOf(key);
        int[] row = localData[index];
        int offset = start;
        if (format.denseColumn) {
            for (int i = 0; i < row.length; i++) {
                int update = format.readInt(data, offset);
                row[i] += update;
                if (row[i] < 0) {
                    throw new IllegalStateException("invalid counter: " + key + ", " + i + ", " + row[i]);
                }
                offset += 4;
            }
        }
        else {
            int count = format.readInt(data, offset);
            offset += 4;
            for (int i = 0; i < count; i++) {
                int col = format.readInt(data, offset);
                offset += 4;
                assert(col < row.length);

                int update = format.readInt(data, offset);
                row[col] += update;
                offset += 4;
            }
        }

        return offset;
    }

}
