package com.slipkprojects.ultrasshservice.tunnel.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

public class WebSocketSocket extends Socket {
    private final Socket underlyingSocket;
    private final InputStream wsInputStream;
    private final OutputStream wsOutputStream;

    public WebSocketSocket(Socket underlyingSocket) throws IOException {
        this.underlyingSocket = underlyingSocket;
        this.wsInputStream = new WebSocketInputStream(underlyingSocket.getInputStream(), underlyingSocket.getOutputStream());
        this.wsOutputStream = new WebSocketOutputStream(underlyingSocket.getOutputStream());
    }

    @Override
    public InputStream getInputStream() {
        return wsInputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return wsOutputStream;
    }

    // Delegar todas las llamadas de estado al socket real subyacente
    @Override
    public void close() throws IOException { underlyingSocket.close(); }
    @Override
    public boolean isClosed() { return underlyingSocket.isClosed(); }
    @Override
    public void setSoTimeout(int timeout) throws SocketException { underlyingSocket.setSoTimeout(timeout); }
    @Override
    public int getSoTimeout() throws SocketException { return underlyingSocket.getSoTimeout(); }
    @Override
    public void bind(SocketAddress bindpoint) throws IOException { underlyingSocket.bind(bindpoint); }
    @Override
    public void connect(SocketAddress endpoint) throws IOException { underlyingSocket.connect(endpoint); }
    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException { underlyingSocket.connect(endpoint, timeout); }
    @Override
    public boolean isConnected() { return underlyingSocket.isConnected(); }
    @Override
    public boolean isBound() { return underlyingSocket.isBound(); }
    @Override
    public InetAddress getInetAddress() { return underlyingSocket.getInetAddress(); }
    @Override
    public InetAddress getLocalAddress() { return underlyingSocket.getLocalAddress(); }
    @Override
    public int getPort() { return underlyingSocket.getPort(); }
    @Override
    public int getLocalPort() { return underlyingSocket.getLocalPort(); }
    @Override
    public SocketAddress getRemoteSocketAddress() { return underlyingSocket.getRemoteSocketAddress(); }
    @Override
    public SocketAddress getLocalSocketAddress() { return underlyingSocket.getLocalSocketAddress(); }
    @Override
    public void setKeepAlive(boolean on) throws SocketException { underlyingSocket.setKeepAlive(on); }
    @Override
    public boolean getKeepAlive() throws SocketException { return underlyingSocket.getKeepAlive(); }
    @Override
    public void setTcpNoDelay(boolean on) throws SocketException { underlyingSocket.setTcpNoDelay(on); }
    @Override
    public boolean getTcpNoDelay() throws SocketException { return underlyingSocket.getTcpNoDelay(); }
}
