package net.p2pchat.file;

import java.util.ArrayList;
import java.util.List;

public class Chunker {

    public static final int CHUNK_SIZE = 1000;

    public static List<byte[]> split(byte[] file) {
        List<byte[]> list = new ArrayList<>();

        int offset = 0;
        while (offset < file.length) {
            int end = Math.min(offset + CHUNK_SIZE, file.length);
            byte[] chunk = new byte[end - offset];
            System.arraycopy(file, offset, chunk, 0, chunk.length);
            list.add(chunk);
            offset = end;
        }
        return list;
    }
}