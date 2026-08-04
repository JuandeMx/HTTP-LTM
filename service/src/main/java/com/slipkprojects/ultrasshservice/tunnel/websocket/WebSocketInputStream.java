package com.slipkprojects.ultrasshservice.tunnel.websocket;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

public class WebSocketInputStream extends InputStream {
    private final DataInputStream in;
    private final OutputStream out;
    private byte[] buffer;
    private int bufferPos = 0;
    private int bufferLength = 0;
    private final Random random = new Random();

    public WebSocketInputStream(InputStream in, OutputStream out) {
        this.in = new DataInputStream(in);
        this.out = out;
    }

    @Override
    public int read() throws IOException {
        if (bufferPos >= bufferLength) {
            if (!readFrame()) {
                return -1;
            }
        }
        return buffer[bufferPos++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (bufferPos >= bufferLength) {
            if (!readFrame()) {
                return -1;
            }
        }
        int toCopy = Math.min(len, bufferLength - bufferPos);
        System.arraycopy(buffer, bufferPos, b, off, toCopy);
        bufferPos += toCopy;
        return toCopy;
    }

    private boolean readFrame() throws IOException {
        while (true) {
            int b0 = in.read(); // Bloquea hasta que haya datos o arroja EOF
            if (b0 == -1) return false;
            
            int opcode = b0 & 0x0F;
            
            int b1 = in.read();
            if (b1 == -1) return false;
            
            boolean masked = (b1 & 0x80) != 0;
            int payloadLen = b1 & 0x7F;
            
            long length = payloadLen;
            if (payloadLen == 126) {
                length = in.readUnsignedShort();
            } else if (payloadLen == 127) {
                length = in.readLong();
            }
            
            if (length > Integer.MAX_VALUE || length < 0) {
                throw new IOException("WebSocket frame too large");
            }
            
            byte[] maskKey = null;
            if (masked) {
                maskKey = new byte[4];
                in.readFully(maskKey);
            }
            
            byte[] payload = new byte[(int) length];
            if (length > 0) {
                in.readFully(payload);
                
                if (masked) {
                    for (int i = 0; i < length; i++) {
                        payload[i] ^= maskKey[i % 4];
                    }
                }
            }
            
            if (opcode == 0x08) { // Opcode Close
                return false;
            } else if (opcode == 0x09) { // Opcode Ping
                sendPong(payload);
                continue; // Leer el siguiente frame
            } else if (opcode == 0x0A) { // Opcode Pong
                continue; // Ignorar pong
            } else if (opcode == 0x01 || opcode == 0x02 || opcode == 0x00) { // Text, Binary, Continuation
                if (length > 0) {
                    buffer = payload;
                    bufferLength = (int) length;
                    bufferPos = 0;
                    return true;
                }
                // Si la longitud es 0, ignorar y leer el siguiente frame
            } else {
                throw new IOException("Unknown WebSocket opcode: " + opcode);
            }
        }
    }
    
    private void sendPong(byte[] payload) throws IOException {
        out.write(0x8A); // FIN=1, Opcode=10 (Pong)
        
        // El cliente SIEMPRE debe enmascarar (RFC 6455)
        out.write(0x80 | payload.length);
        byte[] maskKey = new byte[4];
        random.nextBytes(maskKey);
        out.write(maskKey);
        
        for (int i = 0; i < payload.length; i++) {
            out.write(payload[i] ^ maskKey[i % 4]);
        }
        out.flush();
    }
}
