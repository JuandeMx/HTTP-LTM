package com.slipkprojects.sockshttp.util;

import android.util.Log;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;

public class ShareNetProxy {

    private static final String TAG = "ShareNetProxy";
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private Thread serverThread;
    
    private static ShareNetProxy instance;

    public static ShareNetProxy getInstance() {
        if (instance == null) {
            instance = new ShareNetProxy();
        }
        return instance;
    }

    private ShareNetProxy() {
    }
    
    private int socksPort;

    public boolean isRunning() {
        return isRunning;
    }

    public void start(final int localPort, final int socksPort) {
        this.socksPort = socksPort;
        if (isRunning) return;
        isRunning = true;
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket();
                    serverSocket.setReuseAddress(true);
                    serverSocket.bind(new InetSocketAddress(localPort));
                    Log.i(TAG, "Share Net Proxy started on port " + localPort);
                    while (isRunning) {
                        Socket client = serverSocket.accept();
                        handleClient(client);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in ShareNetProxy server", e);
                }
            }
        });
        serverThread.start();
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    private void handleClient(final Socket client) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream in = client.getInputStream();
                    OutputStream out = client.getOutputStream();

                    // Read the request line
                    StringBuilder sb = new StringBuilder();
                    int c;
                    while ((c = in.read()) != -1) {
                        sb.append((char) c);
                        if (sb.toString().endsWith("\r\n")) {
                            break;
                        }
                    }

                    String requestLine = sb.toString().trim();
                    if (requestLine.isEmpty()) {
                        client.close();
                        return;
                    }

                    String[] parts = requestLine.split(" ");
                    String method = parts[0];
                    String target = parts[1];

                    String host;
                    int port;

                    if (method.equalsIgnoreCase("CONNECT")) {
                        String[] hostPort = target.split(":");
                        host = hostPort[0];
                        port = Integer.parseInt(hostPort[1]);
                        
                        // Consume remaining headers
                        while ((c = in.read()) != -1) {
                            sb.append((char) c);
                            if (sb.toString().endsWith("\r\n\r\n")) {
                                break;
                            }
                        }

                        // Connect to target via local SOCKS proxy using manual handshake
                        Socket targetSocket = new Socket();
                        targetSocket.connect(new InetSocketAddress("127.0.0.1", socksPort), 10000);

                        OutputStream tOut = targetSocket.getOutputStream();
                        InputStream tIn = targetSocket.getInputStream();

                        // SOCKS5 greeting
                        tOut.write(new byte[]{0x05, 0x01, 0x00});
                        tOut.flush();
                        byte[] resp1 = new byte[2];
                        tIn.read(resp1);

                        // SOCKS5 CONNECT
                        byte[] hostBytes = host.getBytes();
                        java.nio.ByteBuffer req = java.nio.ByteBuffer.allocate(7 + hostBytes.length);
                        req.put((byte) 0x05);
                        req.put((byte) 0x01);
                        req.put((byte) 0x00);
                        req.put((byte) 0x03);
                        req.put((byte) hostBytes.length);
                        req.put(hostBytes);
                        req.putShort((short) port);

                        tOut.write(req.array());
                        tOut.flush();

                        byte[] resp2 = new byte[10];
                        tIn.read(resp2);

                        if (resp2[1] != 0x00) {
                            throw new java.io.IOException("SOCKS5 connection failed: " + resp2[1]);
                        }

                        out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                        out.flush();

                        forwardData(client, targetSocket);
                        
                    } else {
                        // For GET/POST, target is an absolute URL (e.g. http://example.com/path)
                        if (target.startsWith("http://")) {
                            String withoutHttp = target.substring(7);
                            int slashIdx = withoutHttp.indexOf('/');
                            String hostPort = slashIdx == -1 ? withoutHttp : withoutHttp.substring(0, slashIdx);
                            if (hostPort.contains(":")) {
                                String[] hp = hostPort.split(":");
                                host = hp[0];
                                port = Integer.parseInt(hp[1]);
                            } else {
                                host = hostPort;
                                port = 80;
                            }

                            // Consume remaining headers to read them, but we need to pass them to the target
                            // Actually, a simple approach is to read all headers, modify the request line to be relative,
                            // but since this is a basic proxy for hotspot, many clients use CONNECT for everything.
                            // For simplicity, we just establish the SOCKS connection and forward the raw data.
                            // However, we already consumed the first line, so we need to rewrite it and send it.
                            // Connect via manual SOCKS5 handshake
                            Socket targetSocket = new Socket();
                            targetSocket.connect(new InetSocketAddress("127.0.0.1", socksPort), 10000);

                            OutputStream tOut = targetSocket.getOutputStream();
                            InputStream tIn = targetSocket.getInputStream();

                            tOut.write(new byte[]{0x05, 0x01, 0x00});
                            tOut.flush();
                            byte[] resp1 = new byte[2];
                            tIn.read(resp1);

                            byte[] hostBytes = host.getBytes();
                            java.nio.ByteBuffer req = java.nio.ByteBuffer.allocate(7 + hostBytes.length);
                            req.put((byte) 0x05);
                            req.put((byte) 0x01);
                            req.put((byte) 0x00);
                            req.put((byte) 0x03);
                            req.put((byte) hostBytes.length);
                            req.put(hostBytes);
                            req.putShort((short) port);

                            tOut.write(req.array());
                            tOut.flush();

                            byte[] resp2 = new byte[10];
                            tIn.read(resp2);

                            if (resp2[1] != 0x00) {
                                throw new java.io.IOException("SOCKS5 connection failed: " + resp2[1]);
                            }

                            String relativePath = slashIdx == -1 ? "/" : withoutHttp.substring(slashIdx);
                            String newRequestLine = method + " " + relativePath + " " + (parts.length > 2 ? parts[2] : "HTTP/1.1") + "\r\n";
                            targetSocket.getOutputStream().write(newRequestLine.getBytes());
                            
                            forwardData(client, targetSocket);
                        } else {
                            client.close();
                        }
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error handling client", e);
                    try { client.close(); } catch (Exception ex) {}
                }
            }
        }).start();
    }

    private void forwardData(final Socket client, final Socket target) {
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream in = client.getInputStream();
                    OutputStream out = target.getOutputStream();
                    byte[] buffer = new byte[32768];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        out.flush();
                    }
                } catch (Exception e) {
                } finally {
                    try { client.close(); } catch (Exception e) {}
                    try { target.close(); } catch (Exception e) {}
                }
            }
        });

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream in = target.getInputStream();
                    OutputStream out = client.getOutputStream();
                    byte[] buffer = new byte[32768];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        out.flush();
                    }
                } catch (Exception e) {
                } finally {
                    try { client.close(); } catch (Exception e) {}
                    try { target.close(); } catch (Exception e) {}
                }
            }
        });

        t1.start();
        t2.start();
    }
}
