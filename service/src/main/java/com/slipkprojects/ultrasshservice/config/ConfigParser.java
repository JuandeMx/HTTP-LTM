package com.slipkprojects.ultrasshservice.config;

import android.content.Context;
import java.io.File;
import java.util.Properties;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import android.util.Log;
import android.content.pm.PackageInfo;
import java.util.Calendar;
import android.widget.Toast;
import java.util.Date;
import java.text.ParseException;
import java.text.DateFormat;
import android.content.pm.PackageManager;
import com.slipkprojects.ultrasshservice.logger.SkStatus;
import com.slipkprojects.ultrasshservice.util.FileUtils;
import java.io.InputStream;
import com.slipkprojects.ultrasshservice.R;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.os.Build;
import com.scottyab.rootbeer.RootBeer;
import com.slipkprojects.ultrasshservice.util.securepreferences.crypto.Cryptor;
import com.slipkprojects.ultrasshservice.util.securepreferences.model.SecurityConfig;
import java.util.ArrayList;
import com.slipkprojects.ultrasshservice.util.Cripto;
import com.slipkprojects.ultrasshservice.config.maze.MazeDecrypter;

/**
* @author SlipkHunter
*/
public class ConfigParser
{
	private static final String TAG = ConfigParser.class.getSimpleName();
	public static final String CONVERTED_PROFILE = "converted Profile";
	
	public static final String FILE_EXTENSAO = "LT";
	
	// Legacy key removed to prevent sniffer fallback
	
	private static final String
		SETTING_VERSION = "file.appVersionCode",
		SETTING_VALIDADE = "file.validade",
		SETTING_PROTEGER = "file.proteger",
		SETTING_AUTOR_MSG = "file.msg",
		SETTING_HIDE_SERVER_MSG = "file.hideServerMessage";
	
	
	public static boolean convertInputAndSave(InputStream input, Context mContext)
			throws IOException {
		Properties mConfigFile = new Properties();
		
		Settings settings = new Settings(mContext);
		SharedPreferences.Editor prefsEdit = settings.getPrefsPrivate()
			.edit();
		
		try {
			
			InputStream decodedInput = decodeInput(input, mContext);
			
			try {
				mConfigFile.loadFromXML(decodedInput);
			} catch(FileNotFoundException e) {
				throw new IOException("File Not Found");
			} catch(IOException e) {
				throw new Exception("Error Unknown", e);
			}

			// versão check
			int versionCode = Integer.parseInt(mConfigFile.getProperty(SETTING_VERSION));

			if (versionCode > getBuildId(mContext)) {
				throw new IOException(mContext.getString(R.string.alert_update_app));
			}

			// validade check
			String msg = mConfigFile.getProperty(SETTING_AUTOR_MSG);
			String protegerProp = mConfigFile.getProperty(SETTING_PROTEGER);
			boolean mIsProteger = protegerProp != null && protegerProp.equals("1");
			long mValidade = 0;
			
			try {
				mValidade = Long.parseLong(mConfigFile.getProperty(SETTING_VALIDADE));
			} catch(Exception e) {
				throw new IOException(mContext.getString(R.string.alert_update_app));
			}

			if (!mIsProteger || mValidade < 0) {
				mValidade = 0;
			}
			else if (mValidade > 0 && isValidadeExpirou(mValidade)){
				throw new IOException(mContext.getString(R.string.error_settings_expired));
			}
			
			// bloqueia root
			boolean isBloquearRoot = false;
			String _blockRoot = mConfigFile.getProperty("bloquearRoot");
			if (_blockRoot != null) {
				isBloquearRoot = _blockRoot.equals("1") ? true : false;
				if (isBloquearRoot) {
					if (isDeviceRooted(mContext)) {
						throw new IOException(mContext.getString(R.string.error_root_detected));
					}
				}
			}

			// bloqueia hwid
			String targetHwid = mConfigFile.getProperty("hwid_lock");
			if (targetHwid != null && !targetHwid.isEmpty()) {
				try {
					Class<?> utilsClass = Class.forName("com.slipkprojects.sockshttp.util.Utils");
					java.lang.reflect.Method getHWID = utilsClass.getMethod("getHWID", Context.class);
					String currentHwid = (String) getHWID.invoke(null, mContext);
					if (!targetHwid.equals(currentHwid)) {
						throw new IOException("HWID Inválido: " + currentHwid);
					}
				} catch (Exception e) {
					if (e instanceof IOException) {
						throw (IOException) e;
					}
				}
			}

			try {
				String _useV2ray = mConfigFile.getProperty("use_v2ray");
				boolean isV2ray = _useV2ray != null && _useV2ray.equals("1");

				String mServidor = deobfuscateString(mConfigFile.getProperty(Settings.SERVIDOR_KEY), mContext);
				String mServidorPorta = deobfuscateString(mConfigFile.getProperty(Settings.SERVIDOR_PORTA_KEY), mContext);
				String mUsuario = deobfuscateString(mConfigFile.getProperty(Settings.USUARIO_KEY), mContext);
				String mSenha = deobfuscateString(mConfigFile.getProperty(Settings.SENHA_KEY), mContext);
				int mPortaLocal = 1080;
				try {
					mPortaLocal = Integer.parseInt(mConfigFile.getProperty(Settings.PORTA_LOCAL_KEY));
				} catch(Exception e) {}
				int mTunnelType = Settings.bTUNNEL_TYPE_SSH_DIRECT;
				
				// ssh hwid auth flag
				boolean useSshHwid = "1".equals(mConfigFile.getProperty("ssh_hwid", "0"));
				prefsEdit.putBoolean(Settings.HWID_AUTH_KEY, useSshHwid);
				
				String _tunnelType = mConfigFile.getProperty(Settings.TUNNELTYPE_KEY);
				if (_tunnelType != null && !_tunnelType.isEmpty()) {
					/**
					* Mantêm compatibilidade
					*/
					if (_tunnelType.equals(Settings.TUNNEL_TYPE_SSH_PROXY)) {
						mTunnelType = Settings.bTUNNEL_TYPE_SSH_PROXY;
					}
					else if (!_tunnelType.equals(Settings.TUNNEL_TYPE_SSH_DIRECT)) {
						mTunnelType = Integer.parseInt(_tunnelType);
					}
				}
				
				if (!isV2ray && mServidor == null) {
					throw new Exception();
				}

				String _proxyIp = deobfuscateString(mConfigFile.getProperty(Settings.PROXY_IP_KEY), mContext);
				String _proxyPort = deobfuscateString(mConfigFile.getProperty(Settings.PROXY_PORTA_KEY), mContext);
				prefsEdit.putString(Settings.PROXY_IP_KEY, _proxyIp != null ? _proxyIp : "");
				prefsEdit.putString(Settings.PROXY_PORTA_KEY, _proxyPort != null ? _proxyPort : "");

				String _defaultPayload = mConfigFile.getProperty(Settings.PROXY_USAR_DEFAULT_PAYLOAD);
				prefsEdit.putBoolean(Settings.PROXY_USAR_DEFAULT_PAYLOAD, _defaultPayload == null || _defaultPayload.equals("1"));
				
				String _customPayload = deobfuscateString(mConfigFile.getProperty(Settings.CUSTOM_PAYLOAD_KEY), mContext);
				prefsEdit.putString(Settings.CUSTOM_PAYLOAD_KEY, _customPayload != null ? _customPayload : "");
				
				String _customSni = deobfuscateString(mConfigFile.getProperty(com.slipkprojects.ultrasshservice.config.SettingsConstants.CUSTOM_SNI), mContext);
				prefsEdit.putString(com.slipkprojects.ultrasshservice.config.SettingsConstants.CUSTOM_SNI, _customSni != null ? _customSni : "");
				
				prefsEdit.putString(Settings.CONFIG_MENSAGEM_KEY, msg != null ? msg : "");
				
				String hideServerMsg = mConfigFile.getProperty(SETTING_HIDE_SERVER_MSG);
				prefsEdit.putBoolean(Settings.HIDE_SERVER_MESSAGE_KEY, hideServerMsg != null && hideServerMsg.equals("1"));
				
				if (mIsProteger) {
					new Settings(mContext)
						.setModoDebug(false);

					String pedirLogin = mConfigFile.getProperty("file.pedirLogin");
					if (pedirLogin != null)
						prefsEdit.putBoolean(Settings.CONFIG_INPUT_PASSWORD_KEY, pedirLogin.equals("1") ? true : false);
					else
						prefsEdit.putBoolean(Settings.CONFIG_INPUT_PASSWORD_KEY, false);
				}
				else {
					prefsEdit.putBoolean(Settings.CONFIG_INPUT_PASSWORD_KEY, false);
				}
				
				prefsEdit.putString(Settings.SERVIDOR_KEY, mServidor != null ? mServidor : "");
				prefsEdit.putString(Settings.SERVIDOR_PORTA_KEY, mServidorPorta != null ? mServidorPorta : "");
				prefsEdit.putString(Settings.USUARIO_KEY, mUsuario != null ? mUsuario : "");
				prefsEdit.putString(Settings.SENHA_KEY, mSenha != null ? mSenha : "");
				prefsEdit.putString(Settings.PORTA_LOCAL_KEY, Integer.toString(mPortaLocal));

				String unified = deobfuscateString(mConfigFile.getProperty("unified_input"), mContext);
				if (unified != null && !unified.isEmpty()) {
					prefsEdit.putString("unified_input", unified);
				} else {
					String fallbackUnified = "";
					if (mServidor != null && !mServidor.isEmpty()) {
						fallbackUnified = mServidor + ":" + (mServidorPorta != null && !mServidorPorta.isEmpty() ? mServidorPorta : "22");
						if (mUsuario != null && !mUsuario.isEmpty()) {
							fallbackUnified += "@" + mUsuario + ":" + (mSenha != null ? mSenha : "");
						}
					}
					prefsEdit.putString("unified_input", fallbackUnified);
				}
				
				String use_ssl = mConfigFile.getProperty("use_ssl");
				if (use_ssl != null) prefsEdit.putBoolean("use_ssl", use_ssl.equals("1"));
				String use_payload = mConfigFile.getProperty("use_payload");
				if (use_payload != null) prefsEdit.putBoolean("use_payload", use_payload.equals("1"));
				String use_enhanced = mConfigFile.getProperty("use_enhanced");
				if (use_enhanced != null) prefsEdit.putBoolean("use_enhanced", use_enhanced.equals("1"));
				String use_slowdns = mConfigFile.getProperty("use_slowdns");
				if (use_slowdns != null) prefsEdit.putBoolean("use_slowdns", use_slowdns.equals("1"));
				String use_psiphon = mConfigFile.getProperty("use_psiphon");
				if (use_psiphon != null) prefsEdit.putBoolean("use_psiphon", use_psiphon.equals("1"));
				String use_v2ray = mConfigFile.getProperty("use_v2ray");
				if (use_v2ray != null) prefsEdit.putBoolean("use_v2ray", use_v2ray.equals("1"));
				String v2ray_config = deobfuscateString(mConfigFile.getProperty("v2ray_config"), mContext);
				if (v2ray_config != null) prefsEdit.putString("v2ray_config", v2ray_config);
				String update_url = deobfuscateString(mConfigFile.getProperty("update_url"), mContext);
				if (update_url != null) prefsEdit.putString("update_url", update_url);

				prefsEdit.putInt(Settings.TUNNELTYPE_KEY, mTunnelType);
				prefsEdit.putBoolean(Settings.CONFIG_PROTEGER_KEY, mIsProteger);
				prefsEdit.putLong(Settings.CONFIG_VALIDADE_KEY, mValidade);
				prefsEdit.putBoolean(Settings.BLOQUEAR_ROOT_KEY, isBloquearRoot);
				
				String _isDnsForward = mConfigFile.getProperty(Settings.DNSFORWARD_KEY);
				boolean isDnsForward = _isDnsForward != null && _isDnsForward.equals("0") ? false : true;
				String dnsResolver = mConfigFile.getProperty(Settings.DNSRESOLVER_KEY);
				settings.setVpnDnsForward(isDnsForward);
				settings.setVpnDnsResolver(dnsResolver);
				
				String _isUdpForward = mConfigFile.getProperty(Settings.UDPFORWARD_KEY);
				boolean isUdpForward = _isUdpForward != null && _isUdpForward.equals("1") ? true : false;
				String udpResolver = mConfigFile.getProperty(Settings.UDPRESOLVER_KEY);
				settings.setVpnUdpForward(isUdpForward);
				settings.setVpnUdpResolver(udpResolver);
				
				String _hHost = deobfuscateString(mConfigFile.getProperty("hysteria_host"), mContext);
				String _hPort = deobfuscateString(mConfigFile.getProperty("hysteria_port"), mContext);
				String _hAuth = deobfuscateString(mConfigFile.getProperty("hysteria_auth"), mContext);
				String _hObfs = deobfuscateString(mConfigFile.getProperty("hysteria_obfs"), mContext);
				String _hSni = deobfuscateString(mConfigFile.getProperty("hysteria_sni"), mContext);
				String _hAlpn = deobfuscateString(mConfigFile.getProperty("hysteria_alpn"), mContext);
				
				if (_hHost != null) prefsEdit.putString("hysteria_host", _hHost);
				if (_hPort != null) prefsEdit.putString("hysteria_port", _hPort);
				if (_hAuth != null) prefsEdit.putString("hysteria_auth", _hAuth);
				if (_hObfs != null) {
					prefsEdit.putString("hysteria_obfs", _hObfs);
					prefsEdit.putString(SettingsConstants.UDP_CUSTOM_OBFS_KEY, _hObfs);
				}
				if (_hSni != null) {
					prefsEdit.putString("hysteria_sni", _hSni);
					if (!_hSni.isEmpty()) {
						prefsEdit.putString(SettingsConstants.CUSTOM_SNI, _hSni);
					}
				}
				if (_hAlpn != null) prefsEdit.putString("hysteria_alpn", _hAlpn);
				
				String _hInsecure = mConfigFile.getProperty("hysteria_insecure");
				if (_hInsecure != null) prefsEdit.putBoolean("hysteria_insecure", _hInsecure.equals("1"));
				
				String _hHopping = mConfigFile.getProperty("hysteria_hopping");
				if (_hHopping != null) prefsEdit.putString("hysteria_hopping", _hHopping);
				
				String _hUp = mConfigFile.getProperty("hysteria_up");
				if (_hUp != null) {
					prefsEdit.putString("hysteria_up", _hUp);
					prefsEdit.putString(SettingsConstants.UDP_CUSTOM_UP_KEY, _hUp);
				}
				
				String _hDown = mConfigFile.getProperty("hysteria_down");
				if (_hDown != null) {
					prefsEdit.putString("hysteria_down", _hDown);
					prefsEdit.putString(SettingsConstants.UDP_CUSTOM_DOWN_KEY, _hDown);
				}
				
				String _hQuic = mConfigFile.getProperty("hysteria_quic_window");
				if (_hQuic != null) {
					try {
						prefsEdit.putInt("hysteria_quic_window", Integer.parseInt(_hQuic));
					} catch(Exception e) {}
				}
				
				String _hDisableMtu = mConfigFile.getProperty("hysteria_disable_mtu");
				if (_hDisableMtu != null) prefsEdit.putBoolean("hysteria_disable_mtu", _hDisableMtu.equals("1"));
				
			} catch(Exception e) {
				if (settings.getModoDebug()) {
					SkStatus.logException("Error Settings", e);
				}
				throw new IOException(mContext.getString(R.string.error_file_settings_invalid));
			}
			
			return prefsEdit.commit();
		
		} catch(IOException e) {
			throw e;
		} catch(Exception e) {
			throw new IOException(mContext.getString(R.string.error_file_invalid), e);
		} catch (Throwable e) {
			throw new IOException(mContext.getString(R.string.error_file_invalid));
		}
	}
	
	public static void convertDataToFile(OutputStream fileOut, Context mContext,
			boolean mIsProteger, boolean mPedirSenha, boolean isBloquearRoot, String mTargetHwid, String mMensagem, long mValidade, boolean mSshAuthHwid, boolean mHideServerMessage)
				throws IOException {
		
		Properties mConfigFile = new Properties();
		ByteArrayOutputStream tempOut = new ByteArrayOutputStream();
		
		Settings settings = new Settings(mContext);
		SharedPreferences prefs = settings.getPrefsPrivate();
		
		try {
			int targerId = getBuildId(mContext);
			// para verses betas
			targerId = 24;
			
			mConfigFile.setProperty(SETTING_VERSION, Integer.toString(targerId));

			mConfigFile.setProperty(SETTING_AUTOR_MSG, mMensagem);
			mConfigFile.setProperty(SETTING_HIDE_SERVER_MSG, mHideServerMessage ? "1" : "0");
			mConfigFile.setProperty(SETTING_PROTEGER, mIsProteger ? "1" : "0");
			mConfigFile.setProperty("bloquearRoot", isBloquearRoot ? "1" : "0");
			
			if (mTargetHwid != null && !mTargetHwid.isEmpty()) {
				mConfigFile.setProperty("hwid_lock", mTargetHwid);
			}
			
			mConfigFile.setProperty("ssh_hwid", mSshAuthHwid ? "1" : "0");
						
			mConfigFile.setProperty(SETTING_VALIDADE, Long.toString(mValidade));
			mConfigFile.setProperty("file.pedirLogin", mPedirSenha ? "1" : "0");

			String server = prefs.getString(Settings.SERVIDOR_KEY, "");
			String server_port = prefs.getString(Settings.SERVIDOR_PORTA_KEY, "");
			
			boolean isUdpForward = settings.getVpnUdpForward();
			boolean isV2ray = prefs.getBoolean("use_v2ray", false);
			if (mIsProteger) {
				if (isUdpForward) {
					String hHost = prefs.getString("hysteria_host", "");
					String hPort = prefs.getString("hysteria_port", "");
					if (hHost.isEmpty() || hPort.isEmpty()) {
						throw new Exception("Hysteria host/port empty");
					}
				} else if (isV2ray) {
					String vConfig = prefs.getString("v2ray_config", "");
					if (vConfig.isEmpty()) {
						throw new Exception("V2Ray config empty");
					}
				} else {
					if (server.isEmpty() || server_port.isEmpty()) {
						throw new Exception("SSH host/port empty");
					}
				}
			}
						
			mConfigFile.setProperty(Settings.SERVIDOR_KEY, obfuscateString(server, mContext));
			mConfigFile.setProperty(Settings.SERVIDOR_PORTA_KEY, obfuscateString(server_port, mContext));
			mConfigFile.setProperty(Settings.USUARIO_KEY, obfuscateString(prefs.getString(Settings.USUARIO_KEY, ""), mContext));
			mConfigFile.setProperty(Settings.SENHA_KEY, obfuscateString(prefs.getString(Settings.SENHA_KEY, ""), mContext));
			mConfigFile.setProperty(Settings.PORTA_LOCAL_KEY, prefs.getString(Settings.PORTA_LOCAL_KEY, "1080"));

			mConfigFile.setProperty(Settings.TUNNELTYPE_KEY, Integer.toString(prefs.getInt(Settings.TUNNELTYPE_KEY, Settings.bTUNNEL_TYPE_SSH_DIRECT)));
			
			mConfigFile.setProperty(Settings.DNSFORWARD_KEY, settings.getVpnDnsForward() ? "1" : "0");
			mConfigFile.setProperty(Settings.DNSRESOLVER_KEY, settings.getVpnDnsResolver());
			
			mConfigFile.setProperty(Settings.UDPFORWARD_KEY, settings.getVpnUdpForward() ? "1" : "0");
			mConfigFile.setProperty(Settings.UDPRESOLVER_KEY, settings.getVpnUdpResolver());
			
			mConfigFile.setProperty("hysteria_host", obfuscateString(prefs.getString("hysteria_host", ""), mContext));
			mConfigFile.setProperty("hysteria_port", obfuscateString(prefs.getString("hysteria_port", ""), mContext));
			mConfigFile.setProperty("hysteria_auth", obfuscateString(prefs.getString("hysteria_auth", ""), mContext));
			mConfigFile.setProperty("hysteria_obfs", obfuscateString(prefs.getString("hysteria_obfs", ""), mContext));
			mConfigFile.setProperty("hysteria_sni", obfuscateString(prefs.getString("hysteria_sni", ""), mContext));
			mConfigFile.setProperty("hysteria_alpn", obfuscateString(prefs.getString("hysteria_alpn", ""), mContext));
			mConfigFile.setProperty("hysteria_insecure", prefs.getBoolean("hysteria_insecure", true) ? "1" : "0");
			mConfigFile.setProperty("hysteria_hopping", prefs.getString("hysteria_hopping", "10"));
			mConfigFile.setProperty("hysteria_up", prefs.getString("hysteria_up", "50"));
			mConfigFile.setProperty("hysteria_down", prefs.getString("hysteria_down", "100"));
			mConfigFile.setProperty("hysteria_quic_window", Integer.toString(prefs.getInt("hysteria_quic_window", 8)));
			mConfigFile.setProperty("hysteria_disable_mtu", prefs.getBoolean("hysteria_disable_mtu", false) ? "1" : "0");
			
			mConfigFile.setProperty(Settings.PROXY_IP_KEY, obfuscateString(prefs.getString(Settings.PROXY_IP_KEY, ""), mContext));
			mConfigFile.setProperty(Settings.PROXY_PORTA_KEY, obfuscateString(prefs.getString(Settings.PROXY_PORTA_KEY, ""), mContext));

			String isDefaultPayload = prefs.getBoolean(Settings.PROXY_USAR_DEFAULT_PAYLOAD, true) ? "1" : "0";
			String customPayload = prefs.getString(Settings.CUSTOM_PAYLOAD_KEY, "");
						
			if (mIsProteger && !isUdpForward && !isV2ray && isDefaultPayload.equals("0") && customPayload.isEmpty()) {
				throw new IOException();
			}
			
			mConfigFile.setProperty(Settings.PROXY_USAR_DEFAULT_PAYLOAD, isDefaultPayload);
			mConfigFile.setProperty(Settings.CUSTOM_PAYLOAD_KEY, obfuscateString(customPayload, mContext));
			
			String customSni = prefs.getString(com.slipkprojects.ultrasshservice.config.SettingsConstants.CUSTOM_SNI, "");
			mConfigFile.setProperty(com.slipkprojects.ultrasshservice.config.SettingsConstants.CUSTOM_SNI, obfuscateString(customSni, mContext));

			// Custom UI keys
			mConfigFile.setProperty("unified_input", obfuscateString(prefs.getString("unified_input", ""), mContext));
			mConfigFile.setProperty("use_ssl", prefs.getBoolean("use_ssl", false) ? "1" : "0");
			mConfigFile.setProperty("use_payload", prefs.getBoolean("use_payload", false) ? "1" : "0");
			mConfigFile.setProperty("use_enhanced", prefs.getBoolean("use_enhanced", false) ? "1" : "0");
			mConfigFile.setProperty("use_slowdns", prefs.getBoolean("use_slowdns", false) ? "1" : "0");
			mConfigFile.setProperty("use_psiphon", prefs.getBoolean("use_psiphon", false) ? "1" : "0");
			mConfigFile.setProperty("use_v2ray", prefs.getBoolean("use_v2ray", false) ? "1" : "0");
			mConfigFile.setProperty("v2ray_config", obfuscateString(prefs.getString("v2ray_config", ""), mContext));

		} catch(Exception e) {
			throw new IOException(mContext.getString(R.string.error_file_settings_invalid));
		}

		try {
			mConfigFile.storeToXML(tempOut,
				"Arquivo de Configuração");
		} catch(FileNotFoundException e) {
			throw new IOException("File Not Found");
		} catch(IOException e) {
			throw new IOException("Error Unknown", e);
		}
		
		try {
			InputStream input_encoded = encodeInput(
				new ByteArrayInputStream(tempOut.toByteArray()), mContext);
			
			FileUtils.copiarArquivo(input_encoded, fileOut);
		} catch(Throwable e) {
			throw new IOException(mContext.getString(R.string.error_save_settings));
		}
	}
	
	
	/**
	* Criptografia
	*/
	
	static {
		System.loadLibrary("native-lib");
	}

	public static native String getSecureConfigKey(Context context);
	public static native byte[] getObfuscationKey(Context context);

	private static Cryptor getCryptor(Context context) {
		return Cryptor.initWithSecurityConfig(
			new SecurityConfig.Builder(getSecureConfigKey(context)).build());
	}

	private static InputStream encodeInput(InputStream in, Context context) throws Throwable {
		Cryptor crypto = getCryptor(context);
		String strBase64 = crypto.encryptToBase64(getBytesArrayInputStream(in)
			.toByteArray());
		
		return new ByteArrayInputStream(strBase64.getBytes("UTF-8"));
	}
	
	private static InputStream decodeInput(InputStream in, Context context) throws Throwable {
		byte[] byteDecript;
		
		ByteArrayOutputStream byteArrayOut = getBytesArrayInputStream(in);
		String str = byteArrayOut.toString("UTF-8").trim();
		if (str.startsWith("\uFEFF")) {
			str = str.substring(1);
		}
		
		Cryptor crypto = getCryptor(context);
		try {
			byteDecript = crypto.decryptFromBase64(str);
		} catch (IllegalArgumentException e) {
			throw new IOException("Invalid configuration file or old format unsupported.");
		}
		
		return new ByteArrayInputStream(byteDecript);
	}
	
	public static ByteArrayOutputStream getBytesArrayInputStream(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		
		int nRead;
		byte[] data = new byte[1024];
		while ((nRead = is.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, nRead);
		}

		buffer.flush();
		
		return buffer;
	}

	
	/**
	* Utils
	*/
	
	public static boolean isValidadeExpirou(long validadeDateMillis) {
		if (validadeDateMillis == 0) {
			return false;
		}
		
		// Get Current Date
		long date_atual = Calendar.getInstance()
			.getTime().getTime();
		
		if (date_atual >= validadeDateMillis) {
			return true;
		}
		
		return false;
	}
	
	public static int getBuildId(Context context) throws IOException {
		try {
			PackageInfo pinfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			return pinfo.versionCode;
		} catch (PackageManager.NameNotFoundException e) {
			throw new IOException("Build ID not found");
		}
	}
	
	public static boolean isDeviceRooted(Context context) {
        /*for (String pathDir : System.getenv("PATH").split(":")){
			if (new File(pathDir, "su").exists()) {
				return true;
			}
		}
		
		Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[] { "/system/xbin/which", "su" });
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            if (in.readLine() != null) return true;
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }*/
		
		RootBeer rootBeer = new RootBeer(context);
		boolean simpleTests = rootBeer.isRooted();
		//boolean experiementalTests = rootBeer.checkForMagiskNative();
			
		return simpleTests;
	}

	public static native String translate(String input, Context context);
	public static native String detranslate(String input, Context context);

	private static final String OBFUSCATION_PREFIX = "OBF:";

	private static String obfuscateString(String input, Context context) {
		if (input == null) {
			return null;
		}
		if (input.isEmpty()) {
			return "";
		}
		try {
			return MazeDecrypter.encrypt(input, context);
		} catch (Exception e) {
			return input;
		}
	}

	private static String deobfuscateString(String input, Context context) {
		if (input == null) {
			return null;
		}
		if (input.isEmpty()) {
			return "";
		}
		if (input.startsWith(MazeDecrypter.PREFIX)) {
			try {
				return MazeDecrypter.decrypt(input, context);
			} catch (Exception e) {
				return input;
			}
		}
		if (input.startsWith("secure")) {
			try {
				return detranslate(input, context);
			} catch (Exception e) {
				return input;
			}
		}
		if (input.startsWith(OBFUSCATION_PREFIX)) {
			try {
				String base64Str = input.substring(OBFUSCATION_PREFIX.length());
				byte[] bytes = android.util.Base64.decode(base64Str, android.util.Base64.NO_WRAP);
				byte[] obfuscationKey = getObfuscationKey(context);
				for (int i = 0; i < bytes.length; i++) {
					bytes[i] = (byte) (bytes[i] ^ obfuscationKey[i % obfuscationKey.length]);
				}
				return new String(bytes, "UTF-8");
			} catch (Exception e) {
				return input;
			}
		}
		return input;
	}

}
