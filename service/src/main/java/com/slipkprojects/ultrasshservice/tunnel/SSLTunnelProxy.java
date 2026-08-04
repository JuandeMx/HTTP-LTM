package com.slipkprojects.ultrasshservice.tunnel;

import com.trilead.ssh2.ProxyData;
import java.net.Socket;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.net.InetSocketAddress;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLSocket;
import java.security.SecureRandom;
import com.slipkprojects.ultrasshservice.logger.SkStatus;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HandshakeCompletedEvent;
import java.util.Arrays;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import com.trilead.ssh2.transport.ClientServerHello;
import android.content.Context;

public class SSLTunnelProxy implements ProxyData
{
	class HandshakeTunnelCompletedListener implements HandshakeCompletedListener {
        private final String val$host;
        private final int val$port;
        private final SSLSocket val$sslSocket;

        HandshakeTunnelCompletedListener( String str, int i, SSLSocket sSLSocket) {
            this.val$host = str;
            this.val$port = i;
            this.val$sslSocket = sSLSocket;
        }

        public void handshakeCompleted(HandshakeCompletedEvent handshakeCompletedEvent) {
			SkStatus.logInfo(new StringBuffer().append("SSL: Supported protocols: <br>").append(Arrays.toString(val$sslSocket.getSupportedProtocols())).toString().replace("[", "").replace("]", "").replace(",", "<br>"));
			SkStatus.logInfo(new StringBuffer().append("SSL: Enabled protocols: <br>").append(Arrays.toString(val$sslSocket.getEnabledProtocols())).toString().replace("[", "").replace("]", "").replace(",", "<br>"));
			SkStatus.logInfo("SSL: Using cipher " + handshakeCompletedEvent.getSession().getCipherSuite());
			SkStatus.logInfo("SSL: Using protocol " + handshakeCompletedEvent.getSession().getProtocol());
			SkStatus.logInfo("SSL: Handshake finished");
        }
    }
	
	private String stunnelServer;
	private int stunnelPort = 443;
	private String stunnelHostSNI;
	private String requestPayload;
	private Context mContext;

	public SSLTunnelProxy(String server, int port, String hostSni) {
		this(server, port, hostSni, null, null);
	}

	public SSLTunnelProxy(String server, int port, String hostSni, String requestPayload, Context context) {
		this.stunnelServer = server;
		this.stunnelPort = port;
		this.stunnelHostSNI = hostSni;
		this.requestPayload = requestPayload;
		this.mContext = context;
	}

	private Socket mSocket;

	@Override
	public Socket openConnection(String hostname, int port, int connectTimeout, int readTimeout) throws IOException
	{
		mSocket = SocketChannel.open().socket();
		mSocket.connect(new InetSocketAddress(stunnelServer, stunnelPort), connectTimeout);
		if (readTimeout > 0) {
			mSocket.setSoTimeout(readTimeout);
		}
		
		if (mSocket.isConnected()) {
			mSocket = doSSLHandshake(stunnelServer, stunnelHostSNI, stunnelPort);
			SkStatus.logInfo("SSL Handshake Success");
		}
		
		if (requestPayload != null && !requestPayload.isEmpty() && mContext != null) {
			String formattedPayload = getFormattedPayload(hostname, port);
			
			if (TunnelUtils.isActiveVpn(mContext)) {
				SkStatus.logInfo("<strong>" + mContext.getString(com.slipkprojects.ultrasshservice.R.string.error_vpn_sniffer_detected) + "</strong>");
				throw new IOException("error detected");
			}

			SkStatus.logInfo("SSL: sending payload");
			OutputStream out = mSocket.getOutputStream();
			
			if (!TunnelUtils.injectSplitPayload(formattedPayload, out)) {
				try {
					out.write(formattedPayload.getBytes("ISO-8859-1"));
				} catch (UnsupportedEncodingException e2) {
					out.write(formattedPayload.getBytes());
				}
				out.flush();
			}

			byte[] buffer = new byte[1024];
			InputStream in = mSocket.getInputStream();
			
			java.util.List<String> methods = new java.util.ArrayList<>();
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Za-z0-9-]+)\\s+[^\\s]+\\s+HTTP/").matcher(formattedPayload);
			while (m.find()) {
				methods.add(m.group(1).toUpperCase());
			}
			int expectedResponses = methods.size();
			if (expectedResponses <= 0) expectedResponses = 1;

			boolean isWebSocket = false;

			for (int i = 0; i < expectedResponses; i++) {
				int len;
				do {
					len = ClientServerHello.readLineRN(in, buffer);
				} while (len == 0);

				String httpReponseFirstLine = "";
				try {
					httpReponseFirstLine = new String(buffer, 0, len, "ISO-8859-1");
				} catch (UnsupportedEncodingException e3) {
					httpReponseFirstLine = new String(buffer, 0, len);
				}

				SkStatus.logInfo("<strong>" + httpReponseFirstLine + "</strong>");

				int contentLength = 0;
				boolean isChunked = false;

				String httpReponseAll = httpReponseFirstLine;
				while ((len = ClientServerHello.readLineRN(in, buffer)) != 0) {
					httpReponseAll += "\n";
					String headerLine = "";
					try {
						headerLine = new String(buffer, 0, len, "ISO-8859-1");
					} catch (UnsupportedEncodingException e3) {
						headerLine = new String(buffer, 0, len);
					}
					httpReponseAll += headerLine;

					String lowerHeader = headerLine.toLowerCase();
					if (lowerHeader.startsWith("content-length:")) {
						try {
							contentLength = Integer.parseInt(lowerHeader.substring(15).trim());
						} catch (Exception e) {}
					} else if (lowerHeader.startsWith("transfer-encoding:") && lowerHeader.contains("chunked")) {
						isChunked = true;
					}
				}

				String currentMethod = (i < methods.size()) ? methods.get(i) : "GET";
				if (currentMethod.equals("HEAD")) {
					contentLength = 0;
					isChunked = false;
				}
				
				boolean isSwitchingProtocols = httpReponseFirstLine.contains("101") && httpReponseAll.toLowerCase().contains("upgrade: websocket");
				
				if (!isSwitchingProtocols) {
					if (contentLength > 0) {
						int totalRead = 0;
						while (totalRead < contentLength) {
							int toRead = Math.min(buffer.length, contentLength - totalRead);
							int r = in.read(buffer, 0, toRead);
							if (r < 0) break;
							totalRead += r;
						}
					} else if (isChunked) {
						while (true) {
							int chunkLenLine = ClientServerHello.readLineRN(in, buffer);
							if (chunkLenLine <= 0) break;
							String chunkLenStr = new String(buffer, 0, chunkLenLine).trim();
							try {
								int chunkSize = Integer.parseInt(chunkLenStr, 16);
								if (chunkSize == 0) {
									ClientServerHello.readLineRN(in, buffer);
									break;
								}
								int totalRead = 0;
								while (totalRead < chunkSize) {
									int toRead = Math.min(buffer.length, chunkSize - totalRead);
									int r = in.read(buffer, 0, toRead);
									if (r < 0) break;
									totalRead += r;
								}
								ClientServerHello.readLineRN(in, buffer);
							} catch (Exception e) {
								break;
							}
						}
					}
				}
				
				if (!httpReponseAll.isEmpty())
					SkStatus.logDebug(httpReponseAll);
				
				if (!httpReponseFirstLine.startsWith("HTTP/")) {
					throw new IOException("The proxy did not send back a valid HTTP response.");
				} else if (httpReponseFirstLine.length() >= 14 && httpReponseFirstLine.charAt(8) == ' ' && httpReponseFirstLine.charAt(12) == ' ') {
					try {
						int errorCode = Integer.parseInt(httpReponseFirstLine.substring(9, 12));
						if (errorCode < 0 || errorCode > 999) {
							throw new IOException("The proxy did not send back a valid HTTP response.");
						} else if (errorCode == 101 && httpReponseAll.toLowerCase().contains("upgrade: websocket")) {
							SkStatus.logInfo("set auto replace response");
							SkStatus.logInfo("<strong>HTTP/1.1 200 OK</strong>");
							isWebSocket = true;
						} else if (errorCode >= 100 && errorCode <= 299) {
							// Continue reading
						} else {
							if (i < expectedResponses - 1) {
								// Normal
							} else {
								SkStatus.logInfo("set auto replace response");
								SkStatus.logInfo("<strong>HTTP/1.1 200 OK</strong>");
							}
						}
					} catch (NumberFormatException e4) {
						throw new IOException("The proxy did not send back a valid HTTP response.");
					}
				} else {
					throw new IOException("The proxy did not send back a valid HTTP response.");
				}
			}
		}
		
		return mSocket;
	}

	private String getFormattedPayload(String hostname, int port) {
		String payload = this.requestPayload;
		if (payload != null) {
			payload = TunnelUtils.formatCustomPayload(hostname, port, payload, mContext);
		}
		return payload;
	}
	
	private SSLSocket doSSLHandshake(String host, String sni, int port) throws IOException {
        try {
			TLSSocketFactory tsf = new TLSSocketFactory();
            SSLSocket socket3 = (SSLSocket) tsf.createSocket(host, port);
			
			try {
				socket3.getClass().getMethod("setHostname", String.class).invoke(socket3, sni);
				SkStatus.logInfo("Setting up SNI (Legacy)...");
			} catch (Throwable e) {
				try {
				    javax.net.ssl.SSLParameters params = socket3.getSSLParameters();
				    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
				        params.setServerNames(java.util.Collections.singletonList(new javax.net.ssl.SNIHostName(sni)));
				        socket3.setSSLParameters(params);
				        SkStatus.logInfo("Setting up SNI (Modern)...");
				    }
				} catch (Throwable e2) {
				    SkStatus.logInfo("Failed to set SNI: " + e2.getMessage());
				}
			}
			
            socket3.addHandshakeCompletedListener(new HandshakeTunnelCompletedListener(host, port, socket3));
            SkStatus.logInfo("Starting SSL Handshake...");
			socket3.startHandshake();
            return socket3;
        } catch (Exception e) {
            IOException iOException = new IOException(new StringBuffer().append("Could not do SSL handshake: ").append(e).toString());
            throw iOException;
        }
    }



	@Override
	public void close()
	{
		try {
			if (mSocket != null) {
				mSocket.close();
			}
		} catch(IOException e) {}
	}

}
