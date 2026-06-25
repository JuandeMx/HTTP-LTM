package com.slipkprojects.ultrasshservice.tunnel;

import android.content.Context;
import java.io.IOException;
import com.slipkprojects.ultrasshservice.logger.SkStatus;
import android.content.IntentFilter;
import com.slipkprojects.ultrasshservice.tunnel.vpn.TunnelVpnService;
import android.support.v4.content.LocalBroadcastManager;
import java.util.List;
import com.slipkprojects.ultrasshservice.tunnel.vpn.VpnUtils;
import android.util.Log;
import com.slipkprojects.ultrasshservice.tunnel.vpn.TunnelState;
import android.content.Intent;
import com.slipkprojects.ultrasshservice.tunnel.vpn.TunnelVpnSettings;
import android.content.BroadcastReceiver;
import com.slipkprojects.ultrasshservice.SocksHttpService;
import com.slipkprojects.ultrasshservice.tunnel.vpn.TunnelVpnManager;
import com.slipkprojects.ultrasshservice.config.Settings;
import android.os.Handler;
import java.net.InetAddress;
import java.net.UnknownHostException;
import com.trilead.ssh2.transport.TransportManager;
import java.util.concurrent.CountDownLatch;
import com.trilead.ssh2.Connection;
import android.widget.Toast;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.File;
import com.trilead.ssh2.KnownHosts;
import com.slipkprojects.ultrasshservice.config.SettingsConstants;
import com.trilead.ssh2.ProxyData;
import com.trilead.ssh2.DynamicPortForwarder;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.DebugLogger;
import com.trilead.ssh2.InteractiveCallback;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.slipkprojects.ultrasshservice.R;
import com.slipkprojects.ultrasshservice.config.PasswordCache;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.ProxyInfo;
import android.os.Build;
import android.net.NetworkRequest;
import android.net.NetworkCapabilities;
import android.net.Network;

public class TunnelManagerThread
	implements Runnable, ConnectionMonitor, InteractiveCallback,
		ServerHostKeyVerifier, DebugLogger
{
	private static final String TAG = TunnelManagerThread.class.getSimpleName();
	
	private OnStopCliente mListener;
	private Context mContext;
	private Handler mHandler;
	private Settings mConfig;
	private boolean mRunning = false, mStopping = false, mStarting = false;
	
	private CountDownLatch mTunnelThreadStopSignal;
	//private ConnectivityManager mCmgr;
	
	public interface OnStopCliente {
		void onStop();
	}
	
	public TunnelManagerThread(Handler handler, Context context) {
		mContext = context;
		mHandler = handler;
		
		mConfig = new Settings(context);
	}
	
	public void setOnStopClienteListener(OnStopCliente listener) {
		mListener = listener;
	}

	@Override
	public void run()
	{
		mStarting = true;
		mTunnelThreadStopSignal = new CountDownLatch(1);
		
		SkStatus.logInfo("<strong>" + mContext.getString(R.string.starting_service_ssh) + "</strong>");
		
		int tries = 0;
		while (!mStopping) {
			try {
				if (!TunnelUtils.isNetworkOnline(mContext)) {
					SkStatus.updateStateString(SkStatus.SSH_AGUARDANDO_REDE, mContext.getString(R.string.state_nonetwork));

					SkStatus.logInfo(R.string.state_nonetwork);
					
					try {
						Thread.sleep(5000);
					} catch(InterruptedException e2) {
						stopAll();
						break;
					}
				}
				else {
					if (tries > 0)
						SkStatus.logInfo("<strong>" + mContext.getString(R.string.state_reconnecting) + "</strong>");

					try {
						Thread.sleep(500);
					} catch(InterruptedException e2) {
						stopAll();
						break;
					}

					if (mConfig.getPrefsPrivate().getBoolean("use_v2ray", false)) {
						startV2ray();
					} else if (mConfig.getVpnUdpForward()) {
						startUdpCustom();
					} else {
						startClienteSSH();
					}
					break;
				}
			} catch(Exception e) {

				SkStatus.logError("<strong>" + mContext.getString(R.string.state_disconnected) + "</strong>");
				closeSSH();
				
				try {
					Thread.sleep(500);
				} catch(InterruptedException e2) {
					stopAll();
					break;
				}
			}
			
			tries++;
		}
		
		mStarting = false;
		
		if (!mStopping) {
			try {
				mTunnelThreadStopSignal.await();
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		
		if (mListener != null) {
			mListener.onStop();
		}
	}
	
	public void stopAll() {
		if (mStopping) return;
		
		SkStatus.updateStateString(SkStatus.SSH_PARANDO, mContext.getString(R.string.stopping_service_ssh));
		SkStatus.logInfo("<strong>" + mContext.getString(R.string.stopping_service_ssh) + "</strong>");
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				mStopping = true;

				if (mTunnelThreadStopSignal != null)
					mTunnelThreadStopSignal.countDown();

				closeSSH();
				
				try {
					Thread.sleep(1000);
				} catch(InterruptedException e){}

				SkStatus.updateStateString(SkStatus.SSH_DESCONECTADO, mContext.getString(R.string.state_disconnected));

				mRunning = false;
				mStarting = false;
				mReconnecting = false;
			}
		}).start();
	}
	
	
	/**
	 * Forwarder
	*/

	protected void startForwarder(int portaLocal) throws Exception {
		if (!mConnected) {
			throw new Exception();
		}
		
		startForwarderSocks(portaLocal);
		
		startTunnelVpnService();
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					if (!mConnected) break;
					
					try {
						Thread.sleep(2000);
					} catch(InterruptedException e) {
						break;
					}
					
					if (lastPingLatency > 0) {
						SkStatus.logInfo(String.format("Ping Latency: %d ms", lastPingLatency));
						break;
					}
				}
			}
		}).start();
	}

	protected void stopForwarder() {
		stopTunnelVpnService();
		
		stopForwarderSocks();
	}
	
	
	/**
	* Cliente SSH
	*/
	
	private final static int AUTH_TRIES = 1;
	private final static int RECONNECT_TRIES = 5;
	
	private Connection mConnection;
	
	private boolean mConnected = false;
	
	protected void startClienteSSH() throws Exception {
		mStopping = false;
		mRunning = true;
		
		String usuario = mConfig.getPrivString(Settings.USUARIO_KEY);
		
		String _senha = mConfig.getPrivString(Settings.SENHA_KEY);
		String senha = _senha.isEmpty() ? PasswordCache.getAuthPassword(null, false) : _senha;
		
		boolean useHwid = mConfig.getPrefsPrivate().getBoolean(Settings.HWID_AUTH_KEY, false);
		if (useHwid) {
			try {
				Class<?> utilsClass = Class.forName("com.slipkprojects.sockshttp.util.Utils");
				java.lang.reflect.Method getHWID = utilsClass.getMethod("getHWID", Context.class);
				String hwid = ((String) getHWID.invoke(null, mContext)).toLowerCase();
				// use HWID as password
				senha = hwid;
				// if username is empty/default or MX, also set it to HWID
				if (usuario == null || usuario.isEmpty() 
					|| "default".equalsIgnoreCase(usuario) 
					|| "mx".equalsIgnoreCase(usuario)
					|| "juandemx".equalsIgnoreCase(usuario)) {
					usuario = hwid;
				}
			} catch (Exception e) {
				senha = "error_hwid";
			}
		}
		
		String keyPath = mConfig.getSSHKeypath();
		int portaLocal = Integer.parseInt(mConfig.getPrivString(Settings.PORTA_LOCAL_KEY));

		try {
			String servidor = mConfig.getPrivString(Settings.SERVIDOR_KEY);
			int porta = 443;
			try {
				porta = Integer.parseInt(mConfig.getPrivString(Settings.SERVIDOR_PORTA_KEY));
			} catch(Exception ep) {}

			conectar(servidor, porta);			for (int i = 0; i < AUTH_TRIES; i++) {
				if (mStopping) {
					return;
				}

				try {
					autenticar(usuario, senha, keyPath);

					break;
				} catch(IOException e) {
					if (i+1 >= AUTH_TRIES) {
						throw new IOException("Autenticação falhou");
					}
					else {
						try {
							Thread.sleep(3000);
						} catch(InterruptedException e2) {
							return;
						}
					}
				}
			}

			SkStatus.updateStateString(SkStatus.SSH_CONECTADO, "Conexión SSH establecida");
			SkStatus.logInfo("<strong><font color=\"#00FF00\">CONECTADO</font></strong>");
			
			if (mConfig.getSSHPinger() > 0) {
				startPinger(mConfig.getSSHPinger());
			}
			
			startForwarder(portaLocal);

		} catch(Exception e) {
			mConnected = false;

			throw e;
		}
	}
	
	public synchronized void closeSSH() {
		stopForwarder();
		stopPinger();
		closeUdpCustom();
		closeV2ray();

		if (mConnection != null) {
			SkStatus.logDebug("Parando SSH");
			mConnection.close();
		}
	}
	
	private Process udpCustomProcess;

	protected void startUdpCustom() throws Exception {
		mStopping = false;
		mRunning = true;
		
		android.content.SharedPreferences prefs = mConfig.getPrefsPrivate();
		String server = prefs.getString("hysteria_host", "");
		String port = prefs.getString("hysteria_port", "36712");
		String pass = prefs.getString("hysteria_auth", "");
		String user = "";

		if (server.isEmpty()) {
			// Fallback to legacy unified_input parsing for backwards compatibility
			String unified = prefs.getString("unified_input", "");
			try {
				String[] parts = unified.split("@");
				String[] hostPort = parts[0].split(":");
				server = hostPort[0];
				if (hostPort.length > 1) {
					port = hostPort[1];
				}
				if (parts.length > 1) {
					String[] userPass = parts[1].split(":");
					user = userPass[0];
					if (userPass.length > 1) {
						pass = userPass[1];
					} else {
						pass = user;
						user = "";
					}
				}
			} catch (Exception e) {}
		}

		if (server.isEmpty()) {
			throw new Exception("Hysteria: Configuración de servidor vacía o inválida");
		}

		SkStatus.updateStateString(SkStatus.SSH_CONECTANDO, "Iniciando Hysteria...");
		SkStatus.logInfo("Iniciando conexión Hysteria a " + server + ":" + port);

		int portaLocal = 1080;
		try {
			portaLocal = Integer.parseInt(mConfig.getPrivString(Settings.PORTA_LOCAL_KEY));
		} catch (Exception e) {}

		String sni = prefs.getString("hysteria_sni", prefs.getString(SettingsConstants.CUSTOM_SNI, ""));
		String alpn = prefs.getString("hysteria_alpn", "");
		boolean insecure = prefs.getBoolean("hysteria_insecure", true);
		String obfsPassword = prefs.getString("hysteria_obfs", prefs.getString(SettingsConstants.UDP_CUSTOM_OBFS_KEY, ""));
		String upSpeed = prefs.getString("hysteria_up", prefs.getString(SettingsConstants.UDP_CUSTOM_UP_KEY, "50"));
		String downSpeed = prefs.getString("hysteria_down", prefs.getString(SettingsConstants.UDP_CUSTOM_DOWN_KEY, "100"));
		int quicWindow = prefs.getInt("hysteria_quic_window", 8);
		boolean disableMtu = prefs.getBoolean("hysteria_disable_mtu", false);

		int streamWindow = quicWindow * 1024 * 1024;
		int connWindow = (int) (quicWindow * 2.5 * 1024 * 1024);

		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"server\": \"").append(server).append(":").append(port).append("\",\n");
		if (user != null && !user.isEmpty()) {
			sb.append("  \"user\": \"").append(user).append("\",\n");
		}
		sb.append("  \"auth_str\": \"").append(pass).append("\",\n");
		sb.append("  \"insecure\": ").append(insecure).append(",\n");
		if (sni != null && !sni.isEmpty()) {
			sb.append("  \"sni\": \"").append(sni).append("\",\n");
		}
		if (obfsPassword != null && !obfsPassword.isEmpty()) {
			sb.append("  \"obfs\": \"").append(obfsPassword).append("\",\n");
		}
		sb.append("  \"up\": \"").append(upSpeed).append(" Mbps\",\n");
		sb.append("  \"down\": \"").append(downSpeed).append(" Mbps\",\n");
		sb.append("  \"disable_path_mtu_discovery\": ").append(disableMtu).append(",\n");
		sb.append("  \"quic_receive_window_stream\": ").append(streamWindow).append(",\n");
		sb.append("  \"quic_receive_window_conn\": ").append(connWindow).append(",\n");
		sb.append("  \"socks5\": {\n");
		sb.append("    \"listen\": \"127.0.0.1:").append(portaLocal).append("\"\n");
		sb.append("  }\n");
		sb.append("}");
		String jsonConfig = sb.toString();

		try {
			// Revert to "auth" instead of "auth_str"
			String jsonConfigStr = sb.toString().replace("\"auth_str\"", "\"auth\"");
			
			SkStatus.logInfo("Iniciando Hysteria en proceso...");
			String err = tunnelgo.Tunnelgo.startHysteria(jsonConfigStr);
			if (err != null && !err.isEmpty()) {
				throw new Exception("Hysteria Go error: " + err);
			}
			SkStatus.logInfo("Hysteria Go inicializado exitosamente");
			
			mConnected = true;
			SkStatus.updateStateString(SkStatus.SSH_CONECTADO, "Hysteria Conectado");
			SkStatus.logInfo("<strong><font color=\"#00FF00\">CONECTADO (HYSTERIA)</font></strong>");

			// Solo iniciamos tun2socks, el forwarder de Hysteria ya expone el puerto local
			startTunnelVpnService();
			
		} catch (Exception e) {
			mConnected = false;
			throw new Exception("Error al iniciar Hysteria: " + e.getMessage());
		}
	}
	
	public synchronized void closeUdpCustom() {
		try {
			tunnelgo.Tunnelgo.stopHysteria();
			SkStatus.logInfo("Hysteria Go detenido.");
		} catch (Exception e) {
			SkStatus.logDebug("Error al detener Hysteria Go: " + e.getMessage());
		}
		if (udpCustomProcess != null) {
			udpCustomProcess.destroy();
			udpCustomProcess = null;
		}
	}

	protected void startV2ray() throws Exception {
		mStopping = false;
		mRunning = true;
		
		android.content.SharedPreferences prefs = mConfig.getPrefsPrivate();
		String configJson = prefs.getString("v2ray_config", "");

		if (configJson.isEmpty()) {
			throw new Exception("V2Ray: Configuración vacía o inválida");
		}

		int portaLocal = 1080;
		try {
			portaLocal = Integer.parseInt(mConfig.getPrivString(Settings.PORTA_LOCAL_KEY));
		} catch (Exception e) {}

		try {
			org.json.JSONObject json = new org.json.JSONObject(configJson);
			org.json.JSONArray inbounds = json.optJSONArray("inbounds");
			if (inbounds == null) {
				inbounds = new org.json.JSONArray();
				json.put("inbounds", inbounds);
			}
			
			boolean hasSocks = false;
			for (int i = 0; i < inbounds.length(); i++) {
				org.json.JSONObject inbound = inbounds.optJSONObject(i);
				if (inbound != null && "socks".equalsIgnoreCase(inbound.optString("protocol", ""))) {
					inbound.put("port", portaLocal);
					inbound.put("listen", "127.0.0.1");
					org.json.JSONObject settings = inbound.optJSONObject("settings");
					if (settings == null) {
						settings = new org.json.JSONObject();
						inbound.put("settings", settings);
					}
					settings.put("udp", true);
					hasSocks = true;
					break;
				}
			}
			
			if (!hasSocks) {
				org.json.JSONObject socksInbound = new org.json.JSONObject();
				socksInbound.put("port", portaLocal);
				socksInbound.put("listen", "127.0.0.1");
				socksInbound.put("protocol", "socks");
				org.json.JSONObject settings = new org.json.JSONObject();
				settings.put("auth", "noauth");
				settings.put("udp", true);
				socksInbound.put("settings", settings);
				inbounds.put(socksInbound);
			}
			
			configJson = json.toString();
		} catch (Exception e) {
			// Fallback string replacement if JSON parsing fails
			configJson = configJson.replace("\"port\": 10808", "\"port\": " + portaLocal)
			                       .replace("\"port\":10808", "\"port\":" + portaLocal)
			                       .replace("\"port\": 1080", "\"port\": " + portaLocal)
			                       .replace("\"port\":1080", "\"port\":" + portaLocal);
		}

		SkStatus.updateStateString(SkStatus.SSH_CONECTANDO, "Iniciando V2Ray...");
		SkStatus.logInfo("Iniciando conexión V2Ray Core...");

		try {
			SkStatus.logInfo("Iniciando V2Ray en proceso...");
			String err = tunnelgo.Tunnelgo.startV2ray(configJson);
			if (err != null && !err.isEmpty()) {
				throw new Exception("V2Ray Core error: " + err);
			}
			SkStatus.logInfo("V2Ray Core inicializado exitosamente");
			
			mConnected = true;
			SkStatus.updateStateString(SkStatus.SSH_CONECTADO, "V2Ray Conectado");
			SkStatus.logInfo("<strong><font color=\"#00FF00\">CONECTADO (V2RAY)</font></strong>");

			startTunnelVpnService();
			
		} catch (Exception e) {
			mConnected = false;
			throw new Exception("Error al iniciar V2Ray: " + e.getMessage());
		}
	}
	
	public synchronized void closeV2ray() {
		try {
			tunnelgo.Tunnelgo.stopV2ray();
			SkStatus.logInfo("V2Ray Core detenido.");
		} catch (Exception e) {
			SkStatus.logDebug("Error al detener V2Ray Core: " + e.getMessage());
		}
	}

	
	protected void conectar(String servidor, int porta) throws Exception {
		if (!mStarting) {
			throw new Exception();
		}
		
		SharedPreferences prefs = mConfig.getPrefsPrivate();

		// aqui deve conectar
		try {
			mConnection = new Connection(servidor, porta);

			if (false) {
				mConnection.enableDebugging(true, this);
			}
			
			// delay sleep
			if (mConfig.getIsDisabledDelaySSH()) {
				mConnection.setTCPNoDelay(true);
			}

			// proxy
			int tunnelType = prefs.getInt(Settings.TUNNELTYPE_KEY, Settings.bTUNNEL_TYPE_SSH_DIRECT);
			
			String customPayload = null;
			if (prefs.getBoolean("use_payload", false)) {
				customPayload = mConfig.getPrivString(Settings.CUSTOM_PAYLOAD_KEY);
				String proxyIp = prefs.getString(Settings.PROXY_IP_KEY, "");
				if (!proxyIp.isEmpty()) {
					tunnelType = Settings.bTUNNEL_TYPE_SSH_PROXY;
				}
			}

			if (prefs.getBoolean("use_ssl", false)) {
				tunnelType = Settings.bTUNNEL_TYPE_SSH_SSL;
			}

			if (prefs.getBoolean("use_psiphon", false)) { SkStatus.logInfo("Psiphon not yet integrated (Native library missing)."); }
			if (prefs.getBoolean("use_slowdns", false)) { SkStatus.logInfo("SlowDNS not yet integrated (Native library missing)."); }

			addProxy(prefs.getBoolean(Settings.CONFIG_PROTEGER_KEY, false), tunnelType,
				customPayload, mConnection);

			// monitora a conexão
			mConnection.addConnectionMonitor(this);
			
			if (Build.VERSION.SDK_INT >= 23) {
				ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
				ProxyInfo proxy = cm.getDefaultProxy();
				if (proxy != null) {
					SkStatus.logInfo("<strong>Proxy na Rede:</strong> " + String.format("%s:%d", proxy.getHost(), proxy.getPort()));
				}
			}
			
			SkStatus.updateStateString(SkStatus.SSH_CONECTANDO, mContext.getString(R.string.state_connecting));
			SkStatus.logInfo(R.string.state_connecting);
			
			// Pausa estabilizadora para evitar que los primeros bytes de SSH colisionen con los de HTTP
			try { Thread.sleep(300); } catch (InterruptedException e) {}

			mConnection.connect(this, 10*1000, 20*1000);

			mConnected = true;

		} catch(Exception e) {

			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));

			String cause = e.getCause() != null ? e.getCause().toString() : e.toString();
			if (useProxy && cause.contains("Key exchange was not finished")) {
				SkStatus.logError("Proxy: conexión perdida");
			}
			else {
				SkStatus.logError("SSH: " + cause);
			}
			
			throw new Exception(e);
		}
	}


	/**
	 * Autenticação
	 */

	private static final String AUTH_PUBLICKEY = "publickey",
			AUTH_PASSWORD = "password", AUTH_KEYBOARDINTERACTIVE = "keyboard-interactive";

	protected void autenticar(String usuario, String senha, String keyPath) throws IOException {
		if (!mConnected) {
			throw new IOException();
		}
		
		SkStatus.updateStateString(SkStatus.SSH_AUTENTICANDO, mContext.getString(R.string.state_auth));

		try {
			if (mConnection.isAuthMethodAvailable(usuario,
				AUTH_PASSWORD)) {

				SkStatus.logInfo("Autenticando con contraseña");
					
				if (mConnection.authenticateWithPassword(usuario,
						senha)) {
					SkStatus.logInfo("<strong>" + mContext.getString(R.string.state_auth_success) + "</strong>");
				}
			}
		} catch (IllegalStateException e) {
			Log.e(TAG,
				  "Connection went away while we were trying to authenticate",
				  e);
		} catch (Exception e) {
			Log.e(TAG, "Problem during handleAuthentication()", e);
		}

		try {
			if (mConnection.isAuthMethodAvailable(usuario,
					AUTH_PUBLICKEY) && keyPath != null && !keyPath.isEmpty()) {
				File f = new File(keyPath);
				if (f.exists()) {
					if (senha.equals("")) senha = null;

					SkStatus.logInfo("Autenticando con llave pública");
					
					if (mConnection.authenticateWithPublicKey(usuario, f,
							senha)) {
						SkStatus.logInfo("<strong>" + mContext.getString(R.string.state_auth_success) + "</strong>");
					}
				}
			}
		} catch (Exception e) {
			Log.d(TAG, "Host does not support 'Public key' authentication.");
		}

		/*try {
		 if (mConnection.authenticateWithNone(mSettings.usuario)) {
		 Log.d(TAG, "Authenticate with none");
		 return true;
		 }
		 } catch (Exception e) {
		 Log.d(TAG, "Host does not support 'none' authentication.");
		 }

		 // TODO: Need verification

		 try {
		 if (mConnection.isAuthMethodAvailable(mSettings.usuario,
		 AUTH_KEYBOARDINTERACTIVE)) {
		 if (mConnection.authenticateWithKeyboardInteractive(
		 mSettings.usuario, this))
		 return true;
		 }
		 } catch (Exception e) {
		 Log.d(TAG,
		 "Host does not support 'Keyboard-Interactive' authentication.");
		 }*/

		if (!mConnection.isAuthenticationComplete()) {
			SkStatus.logInfo("Fallo al autenticar, usuario o contraseña expirado");

			throw new IOException("No fue posible autenticar con los datos proporcionados");
		}
	}

	// XXX: Is it right?
	@Override
	public String[] replyToChallenge(String name, String instruction,
			int numPrompts, String[] prompt, boolean[] echo) throws Exception {
		String[] responses = new String[numPrompts];
		for (int i = 0; i < numPrompts; i++) {
			// request response from user for each prompt
			if (prompt[i].toLowerCase().contains("password")) {
				boolean useHwid = mConfig.getPrefsPrivate().getBoolean(Settings.HWID_AUTH_KEY, false);
				if (useHwid) {
					try {
						Class<?> utilsClass = Class.forName("com.slipkprojects.sockshttp.util.Utils");
						java.lang.reflect.Method getHWID = utilsClass.getMethod("getHWID", Context.class);
						responses[i] = (String) getHWID.invoke(null, mContext);
					} catch (Exception e) {
						responses[i] = "error_hwid";
					}
				} else {
					responses[i] = mConfig.getPrivString(Settings.SENHA_KEY);
				}
			}
		}
		return responses;
	}


	/**
	 * ServerHostKeyVerifier
	 * Fingerprint
	 */

	@Override
	public boolean verifyServerHostKey(String hostname, int port,
		String serverHostKeyAlgorithm, byte[] serverHostKey)
	throws Exception {

		String fingerPrint = KnownHosts.createHexFingerprint(
			serverHostKeyAlgorithm, serverHostKey);
		//int fingerPrintStatus = SSHConstants.FINGER_PRINT_CHANGED;

		SkStatus.logInfo("Finger Print: " + fingerPrint);

		//Log.d(TAG, "Finger Print Type: " + "");

		return true;
	}


	/**
	 * Proxy
	 */

	private boolean useProxy = false;

	protected void addProxy(boolean isProteger, int mTunnelType, String mCustomPayload, Connection conn) throws Exception {

		if (mTunnelType != 0) {
			useProxy = true;

			switch (mTunnelType) {
				case Settings.bTUNNEL_TYPE_SSH_DIRECT:
					if (mCustomPayload != null) {
						try {
							ProxyData proxyData = new HttpProxyCustom(mConfig.getPrivString(Settings.SERVIDOR_KEY), Integer.parseInt(mConfig.getPrivString(Settings.SERVIDOR_PORTA_KEY)),
								null, null, mCustomPayload, true, mContext);

							conn.setProxyData(proxyData);

							if (!mCustomPayload.isEmpty() && !isProteger)
								SkStatus.logInfo("Payload: " + mCustomPayload);

						} catch(Exception e) {
							throw new Exception(mContext.getString(R.string.error_proxy_invalid));
						}
					}
					else {
						useProxy = false;
					}
				break;

				case Settings.bTUNNEL_TYPE_SSH_PROXY:
					String customPayload = mCustomPayload;

					if (customPayload != null && customPayload.isEmpty()) {
						customPayload = null;
					}

					String servidor = mConfig.getPrivString(Settings.PROXY_IP_KEY);
					int porta = Integer.parseInt(mConfig.getPrivString(Settings.PROXY_PORTA_KEY));

					try {
						ProxyData proxyData = new HttpProxyCustom(servidor, porta,
							null, null, customPayload, false, mContext);

						if (!isProteger)
							SkStatus.logInfo(String.format("Proxy Remoto: %s:%d", servidor, porta));
						conn.setProxyData(proxyData);

						if (customPayload != null && !customPayload.isEmpty() && !isProteger) {
							SkStatus.logInfo("Payload: " + customPayload);
						}
					} catch(Exception e) {
						SkStatus.logError(R.string.error_proxy_invalid);

						throw new Exception(mContext.getString(R.string.error_proxy_invalid));
					}
				break;

				case Settings.bTUNNEL_TYPE_SSH_SSL:
					String sslServer = mConfig.getPrivString(Settings.SERVIDOR_KEY);
					int sslPort = 443;
					try {
						sslPort = Integer.parseInt(mConfig.getPrivString(Settings.SERVIDOR_PORTA_KEY));
					} catch(Exception e) {}

					String sni = mConfig.getPrivString(SettingsConstants.CUSTOM_SNI);
					if (sni == null || sni.isEmpty()) {
						sni = sslServer;
					}

					try {
						ProxyData proxyData = new SSLTunnelProxy(sslServer, sslPort, sni, mCustomPayload, mContext);
						if (mCustomPayload != null && !mCustomPayload.isEmpty()) {
							SkStatus.logInfo(String.format("SSL Tunnel: %s:%d (SNI: %s) + Payload", isProteger ? "********" : sslServer, sslPort, isProteger ? "********" : sni));
						} else {
							SkStatus.logInfo(String.format("SSL Tunnel: %s:%d (SNI: %s)", isProteger ? "********" : sslServer, sslPort, isProteger ? "********" : sni));
						}
						conn.setProxyData(proxyData);
					} catch(Exception e) {
						SkStatus.logError("SSL Tunnel error");
						throw new Exception("SSL Proxy invalid");
					}
				break;

					/*case Prefs.TUNNEL_TYPE_SSH_HTTP:
					 SkStatus.logInfo("Usando Tunnel HTTP");

					 String servidorHttp = "165.227.48.122";
					 int portaHttp = 80;

					 ProxyData pData = new HttpTunnelCliente(servidorHttp, portaHttp);

					 SkStatus.logInfo(String.format("Proxy: %s:%d", servidorHttp, portaHttp));
					 conn.setProxyData(pData);
					 break;*/

				default: useProxy = false;
			}
		}
	}


	/**
	 * Socks5 Forwarder
	 */

	private DynamicPortForwarder dpf;
	private com.trilead.ssh2.LocalPortForwarder lpfUdp;

	private synchronized void startForwarderSocks(int portaLocal) throws Exception {
		if (!mConnected) {
			throw new Exception();
		}
		
		if (mConfig.getVpnUdpForward()) {
			try {
				String[] udpResolverParts = mConfig.getVpnUdpResolver().split(":");
				int udpPort = Integer.parseInt(udpResolverParts.length > 1 ? udpResolverParts[1] : "7300");
				SkStatus.logInfo("starting udp forwarder on port " + udpPort);
				lpfUdp = mConnection.createLocalPortForwarder(udpPort, "127.0.0.1", udpPort);
			} catch (Exception e) {
				SkStatus.logError("UDP Forwarder: " + e.getMessage());
			}
		}

		SkStatus.logInfo("starting socks local");
		SkStatus.logDebug(String.format("socks local listen: %d", portaLocal));
		
		try {

			int nThreads = mConfig.getMaximoThreadsSocks();

			if (nThreads > 0) {
				dpf = mConnection.createDynamicPortForwarder(portaLocal, nThreads);

				SkStatus.logDebug("socks local number threads: " + Integer.toString(nThreads));
			}
			else {
				dpf = mConnection.createDynamicPortForwarder(portaLocal);
			}

		} catch (Exception e) {
			SkStatus.logError("Socks Local: " + e.getCause().toString());

			throw new Exception();
		}
	}

	private synchronized void stopForwarderSocks() {
		if (dpf != null) {
			try {
				dpf.close(); 
			} catch(IOException e){}
			dpf = null;
		}
		
		if (lpfUdp != null) {
			try {
				lpfUdp.close();
			} catch (IOException e) {}
			lpfUdp = null;
		}
	}


	/**
	 * Pinger
	 */

	private Thread thPing;
	private long lastPingLatency = -1;
	
	private void startPinger(final int timePing) throws Exception {
		if (!mConnected) {
			throw new Exception();
		}

		SkStatus.logInfo("starting pinger");

		thPing = new Thread() {
			private int pingFailCount = 0;

			@Override
			public void run() {
				while (mConnected) {
					try {
						makePinger();
					} catch(InterruptedException e) {
						break;
					}
				}
				SkStatus.logDebug("pinger stopped");
			}
			
			private synchronized void makePinger() throws InterruptedException {
				try {
					if (mConnection != null) {
						long ping = mConnection.ping();
						pingFailCount = 0;
						if (lastPingLatency < 0) {
							lastPingLatency = ping;
						}
					}
					else throw new InterruptedException();
				} catch(Exception e) {
					pingFailCount++;
					Log.e(TAG, "ping error (fail count: " + pingFailCount + ")", e);
					if (pingFailCount >= 3) {
						pingFailCount = 0;
						// Iniciar auto-reconexión al instante porque el SSH murió
						new Thread(new Runnable() {
							public void run() {
								reconnectSSH();
							}
						}).start();
						throw new InterruptedException();
					}
				}
				
				if (timePing == 0)
					return;

				if (timePing > 0)
					sleep(timePing*1000);
				else {
					SkStatus.logError("ping invalid");
					throw new InterruptedException();
				}
			}
		};

		// inicia
		thPing.start();
	}

	private synchronized void stopPinger() {
		if (thPing != null && thPing.isAlive()) {
			SkStatus.logInfo("stopping pinger");
			
			thPing.interrupt();
			thPing = null;
		}
	}
	
	/**
	 * Connection Monitor
	 */

	@Override
	public void connectionLost(Throwable reason)
	{
		if (mStarting || mStopping || mReconnecting) {
			return;
		}
		
		SkStatus.logError("<strong>" + mContext.getString(R.string.log_conection_lost) + "</strong>");

		if (reason != null) {
			if (reason.getMessage().contains(
					"There was a problem during connect")) {
				return;
			} else if (reason.getMessage().contains(
						   "Closed due to user request")) {
				return;
			}
		}
		
		reconnectSSH();
	}
	
	public boolean mReconnecting = false;
	
	public void reconnectSSH() {
		if (mStarting || mStopping || mReconnecting) {
			return;
		}
		
		mReconnecting = true;
		
		closeSSH();
		
		SkStatus.updateStateString(SkStatus.SSH_RECONECTANDO, "Reconectando..");

		try {
			Thread.sleep(1000);
		} catch(InterruptedException e) {
			mReconnecting = false;
			return;
		}

		while (!mStopping) {
			int sleepTime = 5;
			if (!TunnelUtils.isNetworkOnline(mContext)) {
				SkStatus.updateStateString(SkStatus.SSH_AGUARDANDO_REDE, "Esperando red..");
				SkStatus.logInfo(R.string.state_nonetwork);
			}
			else {
				sleepTime = 3;
				mStarting = true;
				SkStatus.updateStateString(SkStatus.SSH_RECONECTANDO, "Reconectando..");
				SkStatus.logInfo("<strong>" + mContext.getString(R.string.state_reconnecting) + "</strong>");

				try {
					startClienteSSH();
					mStarting = false;
					mReconnecting = false;
					return;
				} catch(Exception e) {
					SkStatus.logInfo("<strong>" + mContext.getString(R.string.state_disconnected) + "</strong>");
				}
				
				mStarting = false;
			}

			try {
				Thread.sleep(sleepTime*1000);
			} catch(InterruptedException e2){
				mReconnecting = false;
				return;
			}
		}
		
		mReconnecting = false;

		stopAll();
	}

	private boolean mHasReceivedServerBanner = false;

	@Override
	public void onReceiveInfo(int id, String msg) {
		if (id == SERVER_BANNER) {
			boolean hideServerMsg = mConfig.getPrefsPrivate().getBoolean(Settings.HIDE_SERVER_MESSAGE_KEY, false);
			
			if (!mHasReceivedServerBanner) {
				mHasReceivedServerBanner = true;
				
				if (hideServerMsg) {
					String notaMsg = mConfig.getPrivString(Settings.CONFIG_MENSAGEM_KEY);
					if (notaMsg != null && !notaMsg.isEmpty()) {
						String cleanMsg = notaMsg.replaceAll("\u001B\\[[;\\d]*m", "");
						SkStatus.logInfo("<strong>" + mContext.getString(R.string.log_server_banner) + "</strong> " + cleanMsg);
					}
				}
				
				return; // Ocultar el primer mensaje del servidor
			}
			
			if (hideServerMsg) {
				return; // Ignorar el mensaje original del servidor
			}
			
			// Limpiar secuencias ANSI (como [1;36m, [0m) en caso de que vengan en mensajes futuros
			String cleanMsg = msg.replaceAll("\u001B\\[[;\\d]*m", "");
			SkStatus.logInfo("<strong>" + mContext.getString(R.string.log_server_banner) + "</strong> " + cleanMsg);
		}
	}


	/**
	 * Debug Logger
	 */

	@Override
	public void log(int level, String className, String message) {
		SkStatus.logInfo("SSH-DEBUG [" + className + "]: " + message);
	}
	

	/**
	 * Vpn Tunnel
	 */
	 
	String serverAddr;

	protected void startTunnelVpnService() throws IOException {
		if (!mConnected) {
			throw new IOException();
		}
		
		SkStatus.logInfo("starting tunnel service");
		SharedPreferences prefs = mConfig.getPrefsPrivate();

		// Broadcast
		IntentFilter broadcastFilter =
			new IntentFilter(TunnelVpnService.TUNNEL_VPN_DISCONNECT_BROADCAST);
		broadcastFilter.addAction(TunnelVpnService.TUNNEL_VPN_START_BROADCAST);
		// Inicia Broadcast
		LocalBroadcastManager.getInstance(mContext)
			.registerReceiver(m_vpnTunnelBroadcastReceiver, broadcastFilter);

		String m_socksServerAddress = String.format("127.0.0.1:%s", mConfig.getPrivString(Settings.PORTA_LOCAL_KEY));
		boolean m_dnsForward = mConfig.getVpnDnsForward();
		String m_udpResolver = mConfig.getVpnUdpForward() ? mConfig.getVpnUdpResolver() : null;

		String servidorIP;
		if (mConfig.getVpnUdpForward()) {
			servidorIP = prefs.getString("hysteria_host", "");
		} else if (prefs.getBoolean("use_v2ray", false)) {
			servidorIP = mConfig.getPrivString(Settings.SERVIDOR_KEY); // fallback
			String vConfig = prefs.getString("v2ray_config", "");
			if (!vConfig.isEmpty()) {
				try {
					org.json.JSONObject json = new org.json.JSONObject(vConfig);
					org.json.JSONArray outbounds = json.optJSONArray("outbounds");
					if (outbounds != null) {
						for (int i = 0; i < outbounds.length(); i++) {
							org.json.JSONObject outbound = outbounds.optJSONObject(i);
							if (outbound != null) {
								org.json.JSONObject settingsObj = outbound.optJSONObject("settings");
								if (settingsObj != null) {
									// Try vnext (vmess / vless)
									org.json.JSONArray vnext = settingsObj.optJSONArray("vnext");
									if (vnext != null && vnext.length() > 0) {
										org.json.JSONObject serverObj = vnext.optJSONObject(0);
										if (serverObj != null) {
											String addr = serverObj.optString("address", "");
											if (!addr.isEmpty()) {
												servidorIP = addr;
												break;
											}
										}
									}
									// Try servers (trojan / shadowsocks)
									org.json.JSONArray servers = settingsObj.optJSONArray("servers");
									if (servers != null && servers.length() > 0) {
										org.json.JSONObject serverObj = servers.optJSONObject(0);
										if (serverObj != null) {
											String addr = serverObj.optString("address", "");
											if (!addr.isEmpty()) {
												servidorIP = addr;
												break;
											}
										}
									}
								}
							}
						}
					}
				} catch (Exception e) {
					Log.e(TAG, "Error parsing V2Ray config for server IP", e);
				}
			}
		} else {
			servidorIP = mConfig.getPrivString(Settings.SERVIDOR_KEY);
			int tunnelType = prefs.getInt(Settings.TUNNELTYPE_KEY, Settings.bTUNNEL_TYPE_SSH_DIRECT);
			if (prefs.getBoolean("use_payload", false)) {
				String proxyIp = prefs.getString(Settings.PROXY_IP_KEY, "");
				if (!proxyIp.isEmpty()) {
					tunnelType = Settings.bTUNNEL_TYPE_SSH_PROXY;
				}
			}

			if (tunnelType == Settings.bTUNNEL_TYPE_SSH_PROXY) {
				try {
					servidorIP = mConfig.getPrivString(Settings.PROXY_IP_KEY);
				} catch(Exception e) {
					SkStatus.logError(R.string.error_proxy_invalid);
					throw new IOException(mContext.getString(R.string.error_proxy_invalid));
				}
			}
		}

		try {
			InetAddress servidorAddr = TransportManager.createInetAddress(servidorIP);
			serverAddr = servidorIP = servidorAddr.getHostAddress();
		} catch(UnknownHostException e) {
			throw new IOException(mContext.getString(R.string.error_server_ip_invalid));
		}
		
		String[] m_excludeIps = {servidorIP};

		String[] m_dnsResolvers = null;
		if (m_dnsForward) {
			m_dnsResolvers = new String[]{mConfig.getVpnDnsResolver()};
		}
		else {
			List<String> lista = VpnUtils.getNetworkDnsServer(mContext);
			m_dnsResolvers = new String[]{lista.get(0)};
		}

		if (isServiceVpnRunning()) {
			Log.d(TAG, "already running service, stopping it first");

			TunnelVpnManager tunnelManager = TunnelState.getTunnelState()
				.getTunnelManager();
			
			if (tunnelManager != null) {
				tunnelManager.signalStopService();
			}

			try { Thread.sleep(500); } catch (Exception e) {}
			TunnelState.getTunnelState().setTunnelManager(null);
		}

		Intent startTunnelVpn = new Intent(mContext, TunnelVpnService.class);
		startTunnelVpn.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		TunnelVpnSettings settings = new TunnelVpnSettings(m_socksServerAddress, m_dnsForward, m_dnsResolvers,
			(m_dnsForward && m_udpResolver == null || !m_dnsForward && m_udpResolver != null), m_udpResolver, m_excludeIps,
				mConfig.getIsFilterApps(), mConfig.getIsFilterBypassMode(), mConfig.getFilterApps(), mConfig.getIsTetheringSubnet());
		startTunnelVpn.putExtra(TunnelVpnManager.VPN_SETTINGS, settings);

		if (mContext.startService(startTunnelVpn) == null) {
			SkStatus.logInfo("failed to start tunnel vpn service");

			throw new IOException("Fallo al iniciar Vpn Service");
		}

		TunnelState.getTunnelState().setStartingTunnelManager();
	}

	public static boolean isServiceVpnRunning() {
		TunnelState tunnelState = TunnelState.getTunnelState();
		return tunnelState.getStartingTunnelManager() || tunnelState.getTunnelManager() != null;
	}

	protected synchronized void stopTunnelVpnService() {
		if (!isServiceVpnRunning()) {
			return;
		}
		
		// Use signalStopService to asynchronously stop the service.
		// 1. VpnService doesn't respond to stopService calls
		// 2. The UI will not block while waiting for stopService to return
		// This scheme assumes that the UI will monitor that the service is
		// running while the Activity is not bound to it. This is the state
		// while the tunnel is shutting down.
		SkStatus.logInfo("stopping tunnel service");
		
		TunnelVpnManager currentTunnelManager = TunnelState.getTunnelState()
			.getTunnelManager();
		
		if (currentTunnelManager != null) {
			currentTunnelManager.signalStopService();
		}
		
		/*if (mThreadLocation != null && mThreadLocation.isAlive()) {
			mThreadLocation.interrupt();
		}
		mThreadLocation = null;*/

		// Parando Broadcast
		LocalBroadcastManager.getInstance(mContext)
			.unregisterReceiver(m_vpnTunnelBroadcastReceiver);
	}
	
	//private Thread mThreadLocation;

	// Local BroadcastReceiver
	private BroadcastReceiver m_vpnTunnelBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public synchronized void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (TunnelVpnService.TUNNEL_VPN_START_BROADCAST.equals(action)) {
				boolean startSuccess = intent.getBooleanExtra(TunnelVpnService.TUNNEL_VPN_START_SUCCESS_EXTRA, true);

				if (!startSuccess) {
					stopAll();
				}
				
			} else if (TunnelVpnService.TUNNEL_VPN_DISCONNECT_BROADCAST.equals(action)) {
				stopAll();
			}
		}
	};
	
}
