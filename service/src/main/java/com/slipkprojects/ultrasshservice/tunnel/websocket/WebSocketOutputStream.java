package com.slipkprojects.ultrasshservice.tunnel.websocket;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

public class WebSocketOutputStream extends OutputStream {
    private final OutputStream out;
    private final Random random = new Random();

    public WebSocketOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len <= 0) return;
        
        // Enviar trama binaria (FIN=1, Opcode=2)
        out.write(0x82); 
        
        // MASK bit (1) + length
        if (len <= 125) {
            out.write(0x80 | len);
        } else if (len <= 65535) {
            out.write(0x80 | 126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(0x80 | 127);
            // 8 bytes de longitud (para len > 65535). Ya que len es un int, los primeros 4 son 0
            out.write(0); out.write(0); out.write(0); out.write(0);
            out.write((len >> 24) & 0xFF);
            out.write((len >> 16) & 0xFF);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        }
        
        // Generar clave de máscara de 4 bytes
        byte[] maskKey = new byte[4];
        random.nextBytes(maskKey);
        out.write(maskKey);
        
        // Enmascarar la carga útil (Payload)
        byte[] maskedPayload = new byte[len];
        for (int i = 0; i < len; i++) {
            maskedPayload[i] = (byte) (b[off + i] ^ maskKey[i % 4]);
        }
        
        out.write(maskedPayload);
        out.flush();
    }
}
