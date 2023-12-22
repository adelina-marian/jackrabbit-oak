/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.oak.index.indexer.document.flatfile.analysis.stream;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.jackrabbit.oak.index.indexer.document.flatfile.analysis.stream.NodeProperty.ValueType;

import net.jpountz.lz4.LZ4FrameInputStream;

/**
 * A node stream reader.
 */
public class NodeStreamReader implements NodeDataReader {

    private final InputStream in;
    private final long fileSize;

    private long lineCount;
    private byte[] buffer = new byte[1024 * 1024];

    private NodeStreamReader(long fileSize, InputStream in) {
        this.fileSize = fileSize;
        this.in = in;
    }

    public static NodeStreamReader open(String fileName) throws IOException {
        long fileSize = new File(fileName).length();
        InputStream fileIn = new FileInputStream(fileName);
        try {
            InputStream in;
            if (fileName.endsWith(".lz4")) {
                in = new LZ4FrameInputStream(fileIn);
            } else {
                in = fileIn;
            }
            return new NodeStreamReader(fileSize, in);
        } catch (IOException e) {
            fileIn.close();
            throw e;
        }
    }

    /**
     * Read a variable size int.
     *
     * @return the value
     * @throws IOException
     */
    public static int readVarInt(InputStream in) throws IOException {
        int b = in.read();
        if ((b & 0x80) == 0) {
            return b;
        }
        // a separate function so that this one can be inlined
        return readVarIntRest(in, b);
    }

    private static int readVarIntRest(InputStream in, int b) throws IOException {
        int x = b & 0x7f;
        b = in.read();
        if ((b & 0x80) == 0) {
            return x | (b << 7);
        }
        x |= (b & 0x7f) << 7;
        b = in.read();
        if ((b & 0x80) == 0) {
            return x | (b << 14);
        }
        x |= (b & 0x7f) << 14;
        b = in.read();
        if ((b & 0x80) == 0) {
            return x | b << 21;
        }
        x |= ((b & 0x7f) << 21) | (in.read() << 28);
        return x;
    }

    public NodeData readNode() throws IOException {
        int size = readVarInt(in);
        if (size < 0) {
            close();
            return null;
        }
        if (++lineCount % 1000000 == 0) {
            System.out.println(lineCount + " lines");
        }
        ArrayList<String> pathElements = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            pathElements.add(readString(in));
        }
        int propertyCount = readVarInt(in);
        ArrayList<NodeProperty> properties = new ArrayList<>(propertyCount);
        for (int i = 0; i < propertyCount; i++) {
            NodeProperty p;
            String name = readString(in);
            ValueType type = ValueType.byOrdinal(in.read());
            if (in.read() == 1) {
                int count = readVarInt(in);
                String[] values = new String[count];
                for (int j = 0; j < count; j++) {
                    values[j] = readString(in);
                }
                p = new NodeProperty(name, type, values, true);
            } else {
                String value = readString(in);
                p = new NodeProperty(name, type, value);
            }
            properties.add(p);
        }
        return new NodeData(pathElements, properties);
    }

    private String readString(InputStream in) throws IOException {
        int len = readVarInt(in);
        if (len == -1) {
            return null;
        }
        byte[] buff = buffer;
        if (len > buff.length) {
            buff = buffer = new byte[len];
        }
        int read = in.readNBytes(buff, 0, len);
        if (read != len) {
            throw new EOFException();
        }
        return new String(buff, 0, len, StandardCharsets.UTF_8);
    }

    @Override
    public long getFileSize() {
        return fileSize;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

}