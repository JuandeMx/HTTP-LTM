package com.slipkprojects.ultrasshservice.tunnel;

import com.trilead.ssh2.crypto.Base64;
import com.trilead.ssh2.sftp.Packet;
import com.trilead.ssh2.transport.ClientServerHello;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import com.trilead.ssh2.ProxyData;
import com.trilead.ssh2.HTTPProxyException;
import java.util.Map;
import android.util.ArrayMap;
import java.util.Iterator;
import com.trilead.ssh2.HTTPProxyData;
import com.slipkprojects.ultrasshservice.logger.SkStatus;
import android.text.Html;
import com.trilead.ssh2.transport.TransportManager;
import java.util.regex.Pattern;
import com.slipkprojects.ultrasshservice.tunnel.TunnelUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.net.NetworkInfo;
import android.net.ConnectivityManager;
import android.os.Build;
import android.content.Context;
import com.slipkprojects.ultrasshservice.R;

/**
 * By Skank3r
 */
public class HttpProxyCustom
implements ProxyData
{

	private final String proxyHost;
    private final String proxyPass;
    private final int proxyPort;
    private final String proxyUser;
    private final String requestPayload;
	private boolean modoDropbear = false;

	private Socket sock;
	private Context mContext;

	public HttpProxyCustom(String proxyHost, int proxyPort, Context context) {
        this(proxyHost, proxyPort, null, null, context);
    }

    public HttpProxyCustom(String proxyHost, int proxyPort, String proxyUser, String proxyPass, Context context) {
        this(proxyHost, proxyPort, proxyUser, proxyPass, null, false, context);
    }

    public HttpProxyCustom(String proxyHost, int proxyPort, String proxyUser, String proxyPass, String requestPayload, boolean modoDropbear, Context context) {
        if (proxyHost == null) {
            throw new IllegalArgumentException("proxyHost must be non-null");
        } else if (proxyPort < 0) {
            throw new IllegalArgumentException("proxyPort must be non-negative");
        } else {
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.proxyUser = proxyUser;
            this.proxyPass = proxyPass;
            this.requestPayload = requestPayload;
			this.modoDropbear = modoDropbear;
			this.mContext = context;
        }
    }

	@Override
    public Socket openConnection(String hostname, int port, int connectTimeout, int readTimeout) throws IOException {
		sock = new Socket();
		
		InetAddress addr = TransportManager.createInetAddress(this.proxyHost);
		sock.connect(new InetSocketAddress(addr, this.proxyPort), connectTimeout);
        sock.setSoTimeout(readTimeout);
		
		SkStatus.logInfo(R.string.state_proxy_running);

		String requestPayload = getRequestPayload(hostname, port);
		
		// anti vpn sniffer
		if (TunnelUtils.isActiveVpn(mContext)) {
			SkStatus.logInfo("<strong>" + mContext.getString(R.string.error_vpn_sniffer_detected) + "</strong>");

			throw new IOException("error detected");
		}

		SkStatus.logInfo(R.string.state_proxy_inject);
		
		OutputStream out = sock.getOutputStream();
		
		// suporte a [split] na payload
		if (!TunnelUtils.injectSplitPayload(requestPayload, out)) {
			try {
				out.write(requestPayload.getBytes("ISO-8859-1"));
			} catch (UnsupportedEncodingException e2) {
				out.write(requestPayload.getBytes());
			}
			out.flush();
		}

		// suporta Dropbear (SSH + PAYLOAD)
		if (modoDropbear) {
			boolean isWebSocketPayload = requestPayload != null && requestPayload.toLowerCase().contains("upgrade: websocket");
			if (!isWebSocketPayload) {
				return sock;
			}
		}

        byte[] buffer = new byte[1024];
        InputStream in = sock.getInputStream();
		
		java.util.List<String> methods = new java.util.ArrayList<>();
		if (requestPayload != null) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Za-z0-9-]+)\\s+[^\\s]+\\s+HTTP/").matcher(requestPayload);
			while (m.find()) {
				methods.add(m.group(1).toUpperCase());
			}
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
				contentLength = 0; // Las peticiones HEAD NUNCA devuelven un body, incluso si tienen Content-Length
				isChunked = false;
			}
			
			// Si la respuesta indica un cambio de protocolo o WebSocket, detener la lectura del body
			boolean isSwitchingProtocols = httpReponseFirstLine.contains("101") && httpReponseAll.toLowerCase().contains("upgrade: websocket");
			
			// Consumir el body de la respuesta si lo hay (importante para errores 301 o 400 de CloudFront)
			// PERO NO CONSUMIR SI ES 101 (WebSocket) para no tragarnos los primeros bytes del protocolo SSH
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
						// Continue reading if there are more expected responses
					} else {
						if (i < expectedResponses - 1) {
							// Es normal recibir 301, 400 o 500 en las peticiones falsas de un [split]
						} else {
							SkStatus.logInfo("set auto replace response");
							SkStatus.logInfo("<strong>HTTP/1.1 200 OK</strong>");
							// Bypassed HTTPProxyException
						}
					}
				} catch (NumberFormatException e4) {
					throw new IOException("The proxy did not send back a valid HTTP response.");
				}
			} else {
				throw new IOException("The proxy did not send back a valid HTTP response.");
			}
		}

		if (isWebSocket) {
			return sock;
		}
		
		return sock;
    }

	private String getRequestPayload(String hostname, int port) {
		String payload = this.requestPayload;

		if (payload != null) {
			payload = TunnelUtils.formatCustomPayload(hostname, port, payload, mContext);
        }
		else {
			StringBuffer sb = new StringBuffer();

			sb.append("CONNECT ");
			sb.append(hostname);
			sb.append(':');
			sb.append(port);
			sb.append(" HTTP/1.0\r\n");
			if (!(this.proxyUser == null || this.proxyPass == null)) {
				char[] encoded;
				String credentials = this.proxyUser + ":" + this.proxyPass;
				try {
					encoded = Base64.encode(credentials.getBytes("ISO-8859-1"));
				} catch (UnsupportedEncodingException e) {
					encoded = Base64.encode(credentials.getBytes());
				}
				sb.append("Proxy-Authorization: Basic ");
				sb.append(encoded);
				sb.append("\r\n");
			}
			sb.append("\r\n");

			payload = sb.toString();
		}

		return payload;
	}

	@Override
	public void close()
	{
		if (sock == null) return;

		try {
			sock.close();
		} catch (IOException e) { /* failed */ }
	}

}
