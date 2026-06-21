package com.slipkprojects.sockshttp;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import com.slipkprojects.sockshttp.R;
import android.support.v7.widget.Toolbar;
import android.support.v4.app.DialogFragment;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;
import android.support.v4.app.FragmentManager;
import android.widget.Toast;
import android.view.View;
import android.content.Context;
import android.support.v7.widget.SwitchCompat;
import android.widget.CompoundButton;
import com.slipkprojects.sockshttp.util.Utils;
import android.util.Log;
import android.widget.TextView;
import android.support.v4.view.GravityCompat;
import android.widget.EditText;
import android.support.design.widget.TextInputEditText;
import com.slipkprojects.sockshttp.DrawerLog;
import android.support.v4.widget.DrawerLayout;
import android.net.Uri;
import android.widget.Button;
import com.slipkprojects.sockshttp.SocksHttpApp;
import android.widget.CheckBox;
import android.support.v4.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import com.slipkprojects.sockshttp.activities.ConfigGeralActivity;
import android.view.LayoutInflater;
import android.content.pm.PackageManager;
import android.text.Html;
import android.support.v7.app.AlertDialog;
import android.content.pm.PackageInfo;
import com.slipkprojects.ultrasshservice.util.SkProtect;
import com.slipkprojects.ultrasshservice.logger.SkStatus;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import android.widget.LinearLayout;
import com.slipkprojects.sockshttp.fragments.ProxyRemoteDialogFragment;
import android.annotation.TargetApi;
import android.webkit.WebView;
import android.os.Build;
import android.net.VpnService;
import android.content.ActivityNotFoundException;
import android.app.Activity;
import com.slipkprojects.ultrasshservice.logger.ConnectionStatus;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import com.slipkprojects.sockshttp.fragments.ClearConfigDialogFragment;
import com.slipkprojects.sockshttp.activities.ConfigExportFileActivity;
import com.slipkprojects.sockshttp.activities.ConfigImportFileActivity;
import com.slipkprojects.ultrasshservice.config.Settings;
import com.slipkprojects.ultrasshservice.config.SettingsConstants;
import android.support.v7.app.ActionBarDrawerToggle;
import android.os.PersistableBundle;
import android.content.res.Configuration;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatRadioButton;
import android.widget.RadioGroup;
import com.slipkprojects.ultrasshservice.config.ConfigParser;
import android.support.v4.app.ActivityCompat;
import android.content.DialogInterface;
import com.slipkprojects.ultrasshservice.tunnel.TunnelManagerHelper;
import com.slipkprojects.ultrasshservice.LaunchVpn;
import com.slipkprojects.sockshttp.activities.AboutActivity;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import com.slipkprojects.sockshttp.model.ViewFragment;
import android.text.InputType;
import android.widget.ImageButton;
import java.io.IOException;
import android.support.design.widget.NavigationView;
import android.util.AttributeSet;
import com.slipkprojects.sockshttp.util.GoogleFeedbackUtils;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdListener;
import com.slipkprojects.sockshttp.activities.BaseActivity;
import android.widget.FrameLayout;
import com.slipkprojects.ultrasshservice.tunnel.TunnelUtils;
import android.text.TextUtils;
import com.slipkprojects.sockshttp.preference.LocaleHelper;
import android.support.annotation.Nullable;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.net.TrafficStats;
import android.widget.ImageView;

/**
 * Activity Principal
 * @author SlipkHunter
 */

public class SocksHttpMainActivity extends BaseActivity
	implements DrawerLayout.DrawerListener,
			View.OnClickListener, RadioGroup.OnCheckedChangeListener,
				CompoundButton.OnCheckedChangeListener, SkStatus.StateListener, NavigationView.OnNavigationItemSelectedListener
{
	private static final String TAG = SocksHttpMainActivity.class.getSimpleName();
	private static final String UPDATE_VIEWS = "MainUpdate";
	public static final String OPEN_LOGS = "com.httpltm.app:openLogs";
	
	private DrawerLog mDrawer;
	private DrawerPanelMain mDrawerPanel;
	private AdView adsBannerView;
	private AdsManager adsManager;
	
	private Settings mConfig;
	private Toolbar toolbar_main;
	private Handler mHandler;
	private String mPreviousState = "";
	
	private EditText inputUnified;
	private CheckBox chkUsePayload, chkSSL, chkEnhanced, chkSlowDns, chkEnableDNS, chkUdpCustom, chkPsiphon, chkV2ray;
	private Button starterButton;
	private LinearLayout noteLayout;
	private WebView noteWebView;
	
	private LinearLayout configBody;
	
	private TextView uploadText;
	private TextView downloadText;
	private TextView pingText;
	
	private Handler speedHandler = new Handler();
	private long lastRxBytes = 0;
	private long lastTxBytes = 0;
	private Runnable speedRunnable;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
        
        if (!checkSecurity()) {
            return;
        }
		
		mHandler = new Handler();
		mConfig = new Settings(this);
		SharedPreferences prefs = mConfig.getPrefsPrivate();

		if (Build.VERSION.SDK_INT >= 33) {
			if (android.support.v4.content.ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
				android.support.v4.app.ActivityCompat.requestPermissions(this, new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
			}
		}

		setContentView(R.layout.activity_main_drawer);

		// AdView Initialization
		FrameLayout adContainer = (FrameLayout) findViewById(R.id.adBannerMainContainer);
		if (adContainer != null) {
			Log.d(TAG, "Initializing AdMob Banner View...");
			adsBannerView = new AdView(this);
			adsBannerView.setAdSize(com.google.android.gms.ads.AdSize.BANNER);
			if (!BuildConfig.DEBUG) {
				adsBannerView.setAdUnitId(SocksHttpApp.ADS_UNITID_BANNER_MAIN);
			} else {
				adsBannerView.setAdUnitId("ca-app-pub-3940256099942544/6300978111");
			}
			adsBannerView.setAdListener(new AdListener() {
				@Override
				public void onAdLoaded() {
					Log.d(TAG, "AdMob Banner loaded successfully.");
					if (adsBannerView != null) {
						adsBannerView.setVisibility(View.VISIBLE);
					}
				}

				@Override
				public void onAdFailedToLoad(int errorCode) {
					Log.e(TAG, "AdMob Banner failed to load with error code: " + errorCode);
				}

				@Override
				public void onAdOpened() {
					Log.d(TAG, "AdMob Banner opened.");
				}

				@Override
				public void onAdLeftApplication() {
					Log.d(TAG, "AdMob Banner left application.");
				}

				@Override
				public void onAdClosed() {
					Log.d(TAG, "AdMob Banner closed.");
				}
			});
			adContainer.addView(adsBannerView);
			adsBannerView.loadAd(new AdRequest.Builder().build());
		}

		// Initialize AdsManager for Interstitial ads
		adsManager = AdsManager.newInstance(this);
		
		toolbar_main = (Toolbar) findViewById(R.id.toolbar_main);
		setSupportActionBar(toolbar_main);
		
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawerLayoutMain);
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
			this, drawer, toolbar_main, R.string.open, R.string.close);
		drawer.addDrawerListener(toggle);
		toggle.syncState();

		android.support.design.widget.NavigationView navigationView = (android.support.design.widget.NavigationView) findViewById(R.id.drawerNavigationView);
		navigationView.setNavigationItemSelectedListener(this);
		
		View headerView = navigationView.getHeaderView(0);
		TextView appVersionText = headerView.findViewById(R.id.nav_headerAppVersion);
		if (appVersionText != null) {
			try {
				appVersionText.setText("v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
			} catch (PackageManager.NameNotFoundException e) {}
		}
		
		inputUnified = (EditText) findViewById(R.id.activity_mainInputUnified);

		chkUsePayload = (CheckBox) findViewById(R.id.chkUsePayload);
		chkSSL = (CheckBox) findViewById(R.id.chkSSL);
		chkEnhanced = (CheckBox) findViewById(R.id.chkEnhanced);
		chkSlowDns = (CheckBox) findViewById(R.id.chkSlowDns);
		chkEnableDNS = (CheckBox) findViewById(R.id.chkEnableDNS);
		chkUdpCustom = (CheckBox) findViewById(R.id.chkUdpCustom);
		chkPsiphon = (CheckBox) findViewById(R.id.chkPsiphon);
		chkV2ray = (CheckBox) findViewById(R.id.chkV2ray);
		
		starterButton = (Button) findViewById(R.id.activity_starterButtonMain);
		
		starterButton.setOnClickListener(this);

		mDrawer = new DrawerLog(this);
		mDrawerPanel = new DrawerPanelMain(this);
		
		boolean showFirstTime = prefs.getBoolean("connect_first_time", true);
		int lastVersion = prefs.getInt("last_version", 0);

		// se primeira vez
		if (showFirstTime)
        {
            SharedPreferences.Editor pEdit = prefs.edit();
            pEdit.putBoolean("connect_first_time", false);
            pEdit.apply();

			Settings.setDefaultConfig(this);

			showBoasVindas();
        }

		try {
			int idAtual = ConfigParser.getBuildId(this);

			if (lastVersion < idAtual) {
				SharedPreferences.Editor pEdit = prefs.edit();
				pEdit.putInt("last_version", idAtual);
				pEdit.apply();

				// se estiver atualizando
				if (!showFirstTime) {
					if (lastVersion <= 12) {
						Settings.setDefaultConfig(this);
						Settings.clearSettings(this);

						Toast.makeText(this, "As configurações foram limpas para evitar bugs",
							Toast.LENGTH_LONG).show();
					}
				}

			}
		} catch(IOException e) {}
		
		
		// set layout
		doLayout();

		// verifica se existe algum problema
		SkProtect.CharlieProtect();

		// recebe local dados
		IntentFilter filter = new IntentFilter();
		filter.addAction(UPDATE_VIEWS);
		filter.addAction(OPEN_LOGS);
		
		LocalBroadcastManager.getInstance(this)
			.registerReceiver(mActivityReceiver, filter);
			
		doUpdateLayout();
		com.slipkprojects.sockshttp.util.UpdateManager.checkUpdate(this, false);
	}


	/**
	 * Layout
	 */
	 
	private void doLayout() {
		setContentView(R.layout.activity_main_drawer);

		toolbar_main = (Toolbar) findViewById(R.id.toolbar_main);
		mDrawerPanel.setDrawer(toolbar_main);
		setSupportActionBar(toolbar_main);

		mDrawer.setDrawer(this);

		starterButton = (Button) findViewById(R.id.activity_starterButtonMain);
		inputUnified = (EditText) findViewById(R.id.activity_mainInputUnified);

		chkUsePayload = (CheckBox) findViewById(R.id.chkUsePayload);
		chkSSL = (CheckBox) findViewById(R.id.chkSSL);
		chkEnhanced = (CheckBox) findViewById(R.id.chkEnhanced);
		chkSlowDns = (CheckBox) findViewById(R.id.chkSlowDns);
		chkEnableDNS = (CheckBox) findViewById(R.id.chkEnableDNS);
		chkUdpCustom = (CheckBox) findViewById(R.id.chkUdpCustom);
		chkPsiphon = (CheckBox) findViewById(R.id.chkPsiphon);
		chkV2ray = (CheckBox) findViewById(R.id.chkV2ray);

		android.support.design.widget.NavigationView navView = (android.support.design.widget.NavigationView) findViewById(R.id.drawerNavigationView);
		View headerView = navView.getHeaderView(0);
		// nav_input fields removed

		starterButton.setOnClickListener(this);

		noteLayout = (LinearLayout) findViewById(R.id.activity_mainNoteLayout);
		noteWebView = (WebView) findViewById(R.id.activity_mainNoteWebView);
		if (noteWebView != null) {
			noteWebView.getSettings().setJavaScriptEnabled(false);
			noteWebView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
		}
		
		configBody = (LinearLayout) findViewById(R.id.configBody);
		
		uploadText = (TextView) findViewById(R.id.uploadText);
		downloadText = (TextView) findViewById(R.id.downloadText);
		pingText = (TextView) findViewById(R.id.pingText);

		View circularButton = findViewById(R.id.activity_starterButtonMainLayout);
		if (circularButton != null) {
			circularButton.setOnClickListener(this);
		}
		
		chkUsePayload.setOnCheckedChangeListener(this);
		chkSSL.setOnCheckedChangeListener(this);
		chkUdpCustom.setOnCheckedChangeListener(this);
		chkV2ray.setOnCheckedChangeListener(this);
		
		startSpeedometer();
	}

	private void startSpeedometer() {
		if (speedRunnable != null) return;
		lastRxBytes = TrafficStats.getTotalRxBytes();
		lastTxBytes = TrafficStats.getTotalTxBytes();
		speedRunnable = new Runnable() {
			@Override
			public void run() {
				if (!SkStatus.SSH_CONECTADO.equals(SkStatus.getLastState())) {
					if (uploadText != null) uploadText.setText("0.00 Mbps");
					if (downloadText != null) downloadText.setText("0.00 Mbps");
					if (pingText != null) pingText.setText("0 ms");
				} else {
					long rxBytes = TrafficStats.getTotalRxBytes();
					long txBytes = TrafficStats.getTotalTxBytes();
					long rxDiff = rxBytes - lastRxBytes;
					long txDiff = txBytes - lastTxBytes;
					lastRxBytes = rxBytes;
					lastTxBytes = txBytes;
					
					double rxMbps = (rxDiff * 8.0) / 1000000.0;
					double txMbps = (txDiff * 8.0) / 1000000.0;
					
					if (uploadText != null) uploadText.setText(String.format("%.2f Mbps", txMbps));
					if (downloadText != null) downloadText.setText(String.format("%.2f Mbps", rxMbps));
					
					new Thread(new Runnable() {
						@Override
						public void run() {
							long startTime = System.currentTimeMillis();
							int finalPing = -1;
							try (java.net.Socket socket = new java.net.Socket()) {
								socket.connect(new java.net.InetSocketAddress("1.1.1.1", 80), 5000);
								finalPing = (int) (System.currentTimeMillis() - startTime);
							} catch (Exception e) {}
							
							final int pingResult = finalPing;
							if (pingText != null) {
								pingText.post(new Runnable() {
									@Override
									public void run() {
										if (pingResult >= 0) {
											pingText.setText(pingResult + " ms");
										} else {
											pingText.setText("—");
										}
									}
								});
							}
						}
					}).start();
				}
				speedHandler.postDelayed(this, 1000);
			}
		};
		speedHandler.postDelayed(speedRunnable, 1000);
	}
	
	private void doUpdateLayout() {
		SharedPreferences prefs = mConfig.getPrefsPrivate();
		boolean isRunning = SkStatus.isTunnelActive();

		setStarterButton(starterButton, this);
		
		String currentState = SkStatus.getLastState();
		if (SkStatus.SSH_CONECTADO.equals(currentState) && !SkStatus.SSH_CONECTADO.equals(mPreviousState)) {
			// Connected! Success vibrate.
			android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
			if (v != null) {
				v.vibrate(50);
			}
			com.slipkprojects.sockshttp.util.UpdateManager.checkUpdate(SocksHttpMainActivity.this, false);
		}
		mPreviousState = currentState;

		// Temporarily disable listeners during programmatic check updates
		chkSSL.setOnCheckedChangeListener(null);
		chkUdpCustom.setOnCheckedChangeListener(null);
		chkUsePayload.setOnCheckedChangeListener(null);
		chkV2ray.setOnCheckedChangeListener(null);

		// Populate checkboxes from preferences
		chkSSL.setChecked(prefs.getBoolean("use_ssl", false));
		chkUdpCustom.setChecked(mConfig.getVpnUdpForward());
		chkEnableDNS.setChecked(prefs.getBoolean(Settings.DNSFORWARD_KEY, true));
		chkUsePayload.setChecked(prefs.getBoolean("use_payload", false));
		chkEnhanced.setChecked(prefs.getBoolean("use_enhanced", false));
		chkSlowDns.setChecked(prefs.getBoolean("use_slowdns", false));
		chkPsiphon.setChecked(prefs.getBoolean("use_psiphon", false));
		chkV2ray.setChecked(prefs.getBoolean("use_v2ray", false));

		String savedInput = prefs.getString("unified_input", "");
		
		boolean isProtected = prefs.getBoolean(Settings.CONFIG_PROTEGER_KEY, false);
		if (isProtected) {
			inputUnified.setText("");
			inputUnified.setHint("Configuración Protegida");
			inputUnified.setEnabled(false);
			chkUsePayload.setEnabled(false);
			chkSSL.setEnabled(false);
			chkEnhanced.setEnabled(false);
			chkSlowDns.setEnabled(false);
			chkEnableDNS.setEnabled(false);
			chkUdpCustom.setEnabled(false);
			chkPsiphon.setEnabled(false);
			chkV2ray.setEnabled(false);
		} else {
			inputUnified.setText(savedInput);
			
			boolean isUdpCustom = chkUdpCustom.isChecked();
			boolean isV2ray = chkV2ray.isChecked();
			if (isUdpCustom) {
				inputUnified.setHint("ip:port@contraseña o ip:port@user:pass");
				
				chkUsePayload.setChecked(false);
				chkUsePayload.setEnabled(false);
				chkSSL.setChecked(false);
				chkSSL.setEnabled(false);
				chkV2ray.setChecked(false);
				chkV2ray.setEnabled(false);
			} else if (isV2ray) {
				inputUnified.setHint("V2Ray (Configuración o Enlace importado)");
				
				chkUsePayload.setChecked(false);
				chkUsePayload.setEnabled(false);
				chkSSL.setChecked(false);
				chkSSL.setEnabled(false);
				chkUdpCustom.setChecked(false);
				chkUdpCustom.setEnabled(false);
			} else {
				inputUnified.setHint("ip:port@user:pass");
				
				chkUsePayload.setEnabled(!isRunning);
				chkSSL.setEnabled(!isRunning);
				chkUdpCustom.setEnabled(!isRunning);
				chkV2ray.setEnabled(!isRunning);
			}
			
			inputUnified.setEnabled(!isRunning && !isUdpCustom && !isV2ray);
			chkEnhanced.setEnabled(!isRunning && !isUdpCustom && !isV2ray);
			chkSlowDns.setEnabled(!isRunning && !isUdpCustom && !isV2ray);
			chkEnableDNS.setEnabled(!isRunning);
			chkPsiphon.setEnabled(!isRunning && !isUdpCustom && !isV2ray);
		}

		// Restore listeners
		chkSSL.setOnCheckedChangeListener(this);
		chkUdpCustom.setOnCheckedChangeListener(this);
		chkUsePayload.setOnCheckedChangeListener(this);
		chkV2ray.setOnCheckedChangeListener(this);

		String noteMsg = mConfig.getPrivString(Settings.CONFIG_MENSAGEM_KEY);
		if (noteMsg != null && !noteMsg.isEmpty()) {
			noteLayout.setVisibility(View.VISIBLE);
			String htmlData = "<html><head><style>body { color: white; margin: 0; padding: 0; word-wrap: break-word; }</style></head><body>" + noteMsg + "</body></html>";
			noteWebView.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null);
		} else {
			noteLayout.setVisibility(View.GONE);
		}
	}
	
	
	private synchronized void doSaveData() {
		SharedPreferences prefs = mConfig.getPrefsPrivate();
		if (prefs.getBoolean(Settings.CONFIG_PROTEGER_KEY, false)) {
			return;
		}
		SharedPreferences.Editor edit = prefs.edit();

		String unified = inputUnified.getText().toString();
		edit.putString("unified_input", unified);

		edit.putBoolean("use_ssl", chkSSL.isChecked());
		mConfig.setVpnUdpForward(chkUdpCustom.isChecked());
		edit.putBoolean(Settings.DNSFORWARD_KEY, chkEnableDNS.isChecked());
		edit.putBoolean("use_payload", chkUsePayload.isChecked());
		edit.putBoolean("use_enhanced", chkEnhanced.isChecked());
		edit.putBoolean("use_slowdns", chkSlowDns.isChecked());
		edit.putBoolean("use_psiphon", chkPsiphon.isChecked());
		edit.putBoolean("use_v2ray", chkV2ray.isChecked());

		edit.apply();
		
		// Parse unified input
		try {
			String[] parts = unified.split("@");
			String hostPort = parts[0];
			String[] hp = hostPort.split(":");
			String ip = hp[0];
			String port = hp.length > 1 ? hp[1] : "443";
			
			edit.putString(Settings.SERVIDOR_KEY, ip);
			edit.putString(Settings.SERVIDOR_PORTA_KEY, port);

			if (parts.length > 1) {
				String[] up = parts[1].split(":");
				edit.putString(Settings.USUARIO_KEY, up[0]);
				edit.putString(Settings.SENHA_KEY, up.length > 1 ? up[1] : "");
			}
		} catch (Exception e) {}
		
		edit.apply();
	}


	/**
	 * Tunnel SSH
	 */

	public void startOrStopTunnel(Activity activity) {
		// Micro-vibrate on click
		android.os.Vibrator v = (android.os.Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
		if (v != null) {
			v.vibrate(20);
		}
		
		if (SkStatus.isTunnelActive()) {
			TunnelManagerHelper.stopSocksHttp(activity);
		}
		else {
			// oculta teclado se vísivel, tá com bug, tela verde
			//Utils.hideKeyboard(activity);
			
			Settings config = new Settings(activity);
			SharedPreferences prefs = config.getPrefsPrivate();
			
			// Check unified input
			if (!prefs.getBoolean(Settings.CONFIG_PROTEGER_KEY, false)) {
				if (chkUdpCustom.isChecked()) {
					if (prefs.getString("hysteria_host", "").isEmpty() && inputUnified.getText().toString().isEmpty()) {
						Toast.makeText(this, R.string.error_empty_settings, Toast.LENGTH_SHORT).show();
						return;
					}
				} else if (chkV2ray.isChecked()) {
					if (prefs.getString("v2ray_config", "").isEmpty()) {
						Toast.makeText(this, R.string.error_empty_settings, Toast.LENGTH_SHORT).show();
						return;
					}
				} else {
					if (inputUnified.getText().toString().isEmpty()) {
						Toast.makeText(this, R.string.error_empty_settings, Toast.LENGTH_SHORT).show();
						return;
					}
				}
			}
			
			Intent intent = new Intent(activity, LaunchVpn.class);
			intent.setAction(Intent.ACTION_MAIN);
			
			if (config.getHideLog()) {
				intent.putExtra(LaunchVpn.EXTRA_HIDELOG, true);
			}
			
			activity.startActivity(intent);
		}
	}

	private void setPayloadSwitch(int tunnelType, boolean isCustomPayload) {
		// Removed
	}

	public void setStarterButton(Button starterButton, Activity activity) {
		String state = SkStatus.getLastState();
		boolean isRunning = SkStatus.isTunnelActive();

		if (starterButton != null) {
			int resId;
			
			SharedPreferences prefsPrivate = new Settings(activity).getPrefsPrivate();

			if (ConfigParser.isValidadeExpirou(prefsPrivate
					.getLong(Settings.CONFIG_VALIDADE_KEY, 0))) {
				resId = R.string.expired;
				starterButton.setEnabled(false);

				if (isRunning) {
					startOrStopTunnel(activity);
				}
			}
			else if (prefsPrivate.getBoolean(Settings.BLOQUEAR_ROOT_KEY, false) &&
					ConfigParser.isDeviceRooted(activity)) {
			   resId = R.string.blocked;
			   starterButton.setEnabled(false);
			   
			   Toast.makeText(activity, R.string.error_root_detected, Toast.LENGTH_SHORT)
					.show();

			   if (isRunning) {
				   startOrStopTunnel(activity);
			   }
			}
			else if (SkStatus.SSH_INICIANDO.equals(state)) {
				resId = R.string.stop;
				starterButton.setEnabled(false);
			}
			else if (SkStatus.SSH_PARANDO.equals(state)) {
				resId = R.string.state_stopping;
				starterButton.setEnabled(false);
			}
			else {
				resId = isRunning ? R.string.stop : R.string.start;
				starterButton.setEnabled(true);
			}

			starterButton.setText(resId);
			
			// Custom UI updates
			TextView powerText = (TextView) activity.findViewById(R.id.powerText);
			ImageView powerIcon = (ImageView) activity.findViewById(R.id.powerIcon);
			View circularBg = activity.findViewById(R.id.activity_starterButtonMainLayout);
			TextView statusText = (TextView) activity.findViewById(R.id.statusText);
			View statusIndicator = activity.findViewById(R.id.statusIndicator);
			
			if (powerText != null && circularBg != null) {
				powerText.setText(resId);
				ImageView bgImage = (ImageView) activity.findViewById(R.id.activity_starterButtonBg);
				
				if (bgImage != null) {
					boolean isConnected = SkStatus.SSH_CONECTADO.equals(state);
					
					if (isConnected) {
						bgImage.clearAnimation();
						bgImage.setImageResource(R.drawable.bg_circle_button);
						powerText.setTextColor(ContextCompat.getColor(activity, R.color.colorAccent));
						if (powerIcon != null) powerIcon.setColorFilter(ContextCompat.getColor(activity, R.color.colorAccent));
						if (statusText != null) statusText.setText("CONECTADO");
						if (statusIndicator != null && statusIndicator.getBackground() != null) 
							statusIndicator.getBackground().setColorFilter(ContextCompat.getColor(activity, R.color.colorAccent), android.graphics.PorterDuff.Mode.SRC_IN);
					} else if (isRunning) {
						bgImage.setImageResource(R.drawable.bg_circle_button_connecting);
						powerText.setTextColor(android.graphics.Color.parseColor("#FFC107"));
						if (powerIcon != null) powerIcon.setColorFilter(android.graphics.Color.parseColor("#FFC107"));
						if (statusText != null) {
							if (SkStatus.SSH_RECONECTANDO.equals(state)) {
								statusText.setText("RECONECTANDO...");
							} else {
								statusText.setText("CONECTANDO...");
							}
						}
						if (statusIndicator != null && statusIndicator.getBackground() != null) 
							statusIndicator.getBackground().setColorFilter(android.graphics.Color.parseColor("#FFC107"), android.graphics.PorterDuff.Mode.SRC_IN);
						
						if (bgImage.getAnimation() == null) {
							android.view.animation.RotateAnimation rotate = new android.view.animation.RotateAnimation(
									0, 360,
									android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
									android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
							);
							rotate.setDuration(1000);
							rotate.setRepeatCount(android.view.animation.Animation.INFINITE);
							rotate.setInterpolator(new android.view.animation.LinearInterpolator());
							bgImage.startAnimation(rotate);
						}
					} else {
						bgImage.clearAnimation();
						bgImage.setImageResource(R.drawable.bg_circle_button_error);
						powerText.setTextColor(android.graphics.Color.parseColor("#FF5252"));
						if (powerIcon != null) powerIcon.setColorFilter(android.graphics.Color.parseColor("#FF5252"));
						if (statusText != null) statusText.setText("DESCONECTADO");
						if (statusIndicator != null && statusIndicator.getBackground() != null) 
							statusIndicator.getBackground().setColorFilter(android.graphics.Color.parseColor("#FF5252"), android.graphics.PorterDuff.Mode.SRC_IN);
					}
				}
			}
		}
	}
	

	
	@Override
    public void onPostCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onPostCreate(savedInstanceState, persistentState);
        if (mDrawerPanel.getToogle() != null)
			mDrawerPanel.getToogle().syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerPanel.getToogle() != null)
			mDrawerPanel.getToogle().onConfigurationChanged(newConfig);
    }
	
	private boolean isMostrarSenha = false;
	
	private boolean checkSecurity() {
		return true;
	}

	@Override
	public void onClick(View p1)
	{
		SharedPreferences prefs = mConfig.getPrefsPrivate();

		int id = p1.getId();
		if (id == R.id.activity_starterButtonMain || id == R.id.activity_starterButtonMainLayout) {
                if (!checkSecurity()) return;
				doSaveData();
				startOrStopTunnel(this);
		}
	}

	@Override
	public void onCheckedChanged(RadioGroup p1, int p2)
	{
		// Removed
	}

	@Override
	public void onCheckedChanged(CompoundButton p1, boolean p2)
	{
		int id = p1.getId();
		boolean isRunning = SkStatus.isTunnelActive();
		
		if (id == R.id.chkUdpCustom) {
			if (p2) {
				chkUsePayload.setOnCheckedChangeListener(null);
				chkSSL.setOnCheckedChangeListener(null);
				chkV2ray.setOnCheckedChangeListener(null);
				
				chkUsePayload.setChecked(false);
				chkSSL.setChecked(false);
				chkV2ray.setChecked(false);
				
				chkUsePayload.setEnabled(false);
				chkSSL.setEnabled(false);
				chkV2ray.setEnabled(false);
				
				chkUsePayload.setOnCheckedChangeListener(this);
				chkSSL.setOnCheckedChangeListener(this);
				chkV2ray.setOnCheckedChangeListener(this);
				
				inputUnified.setHint("ip:port@contraseña o ip:port@user:pass");
				inputUnified.setEnabled(false);
				
				chkEnhanced.setEnabled(false);
				chkSlowDns.setEnabled(false);
				chkPsiphon.setEnabled(false);
			} else {
				chkUsePayload.setEnabled(!isRunning);
				chkSSL.setEnabled(!isRunning);
				chkV2ray.setEnabled(!isRunning);
				
				inputUnified.setHint("ip:port@user:pass");
				inputUnified.setEnabled(!isRunning);
				
				chkEnhanced.setEnabled(!isRunning);
				chkSlowDns.setEnabled(!isRunning);
				chkPsiphon.setEnabled(!isRunning);
			}
		} else if (id == R.id.chkV2ray) {
			if (p2) {
				chkUsePayload.setOnCheckedChangeListener(null);
				chkSSL.setOnCheckedChangeListener(null);
				chkUdpCustom.setOnCheckedChangeListener(null);
				
				chkUsePayload.setChecked(false);
				chkSSL.setChecked(false);
				chkUdpCustom.setChecked(false);
				
				chkUsePayload.setEnabled(false);
				chkSSL.setEnabled(false);
				chkUdpCustom.setEnabled(false);
				
				chkUsePayload.setOnCheckedChangeListener(this);
				chkSSL.setOnCheckedChangeListener(this);
				chkUdpCustom.setOnCheckedChangeListener(this);
				
				inputUnified.setHint("V2Ray (Configuración o Enlace importado)");
				inputUnified.setEnabled(false);
				
				chkEnhanced.setEnabled(false);
				chkSlowDns.setEnabled(false);
				chkPsiphon.setEnabled(false);
			} else {
				chkUsePayload.setEnabled(!isRunning);
				chkSSL.setEnabled(!isRunning);
				chkUdpCustom.setEnabled(!isRunning);
				
				inputUnified.setHint("ip:port@user:pass");
				inputUnified.setEnabled(!isRunning);
				
				chkEnhanced.setEnabled(!isRunning);
				chkSlowDns.setEnabled(!isRunning);
				chkPsiphon.setEnabled(!isRunning);
			}
		} else if (id == R.id.chkUsePayload || id == R.id.chkSSL) {
			if (p2) {
				chkUdpCustom.setOnCheckedChangeListener(null);
				chkV2ray.setOnCheckedChangeListener(null);
				
				chkUdpCustom.setChecked(false);
				chkV2ray.setChecked(false);
				
				chkUdpCustom.setEnabled(false);
				chkV2ray.setEnabled(false);
				
				chkUdpCustom.setOnCheckedChangeListener(this);
				chkV2ray.setOnCheckedChangeListener(this);
				
				inputUnified.setHint("ip:port@user:pass");
			} else {
				if (!chkUsePayload.isChecked() && !chkSSL.isChecked()) {
					chkUdpCustom.setEnabled(!isRunning);
					chkV2ray.setEnabled(!isRunning);
				}
			}
		}
	}
	
	protected void showBoasVindas() {
		new AlertDialog.Builder(this)
            . setTitle(R.string.attention)
            . setMessage(R.string.first_start_msg)
			. setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface di, int p) {
					// ok
				}
			})
			. setCancelable(false)
            . show();
	}
	
	@Override
	public void updateState(final String state, String msg, int localizedResId, final ConnectionStatus level, Intent intent)
	{
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				doUpdateLayout();
				if (level == ConnectionStatus.LEVEL_CONNECTED) {
					if (TunnelUtils.isNetworkOnline(SocksHttpMainActivity.this)) {
						if (adsManager != null) {
							Log.d(TAG, "Server connected and online. Loading interstitial ad...");
							adsManager.loadAdsInterstitial(true);
						}
					}
				}
			}
		});
	}


	/**
	 * Recebe locais Broadcast
	 */

	private BroadcastReceiver mActivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

            if (action.equals(UPDATE_VIEWS) && !isFinishing()) {
				doUpdateLayout();
			}
			else if (action.equals(OPEN_LOGS)) {
				if (mDrawer != null && !isFinishing()) {
					DrawerLayout drawerLayout = mDrawer.getDrawerLayout();
					
					if (!drawerLayout.isDrawerOpen(GravityCompat.END)) {
						drawerLayout.openDrawer(GravityCompat.END);
					}
				}
			}
        }
    };


	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mDrawerPanel.getToogle() != null && mDrawerPanel.getToogle().onOptionsItemSelected(item)) {
            return true;
        }

		// Menu Itens
		int id = item.getItemId();
		if (id == R.id.miLogs) {
  			Intent intent = new Intent(OPEN_LOGS);
  			android.support.v4.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
  		} else if (id == R.id.miLimparConfig) {
				if (!SkStatus.isTunnelActive()) {
					DialogFragment dialog = new ClearConfigDialogFragment();
					dialog.show(getSupportFragmentManager(), "alertClearConf");
				} else {
					Toast.makeText(this, R.string.error_tunnel_service_execution, Toast.LENGTH_SHORT)
						.show();
				}
		} else if (id == R.id.miSettings) {
				Intent intentSettings = new Intent(this, ConfigGeralActivity.class);
				//intentSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intentSettings);
		} else if (id == R.id.miSettingImportar) {
				if (SkStatus.isTunnelActive()) {
					Toast.makeText(this, R.string.error_tunnel_service_execution,
						Toast.LENGTH_SHORT).show();
				}
				else {
					Intent intentImport = new Intent(Intent.ACTION_GET_CONTENT);
					intentImport.setType("*/*");
					startActivityForResult(intentImport, 1234);
				}
		} else if (id == R.id.miSettingExportar) {
				SharedPreferences prefs = mConfig.getPrefsPrivate();
				
				if (SkStatus.isTunnelActive()) {
					Toast.makeText(this, R.string.error_tunnel_service_execution,
						Toast.LENGTH_SHORT).show();
				}
				else if (prefs.getBoolean(Settings.CONFIG_PROTEGER_KEY, false)) {
					Toast.makeText(this, R.string.error_settings_blocked,
						Toast.LENGTH_SHORT).show();
				}
				else {
					Intent intentExport = new Intent(this, ConfigExportFileActivity.class);
					startActivity(intentExport);
				}
		} else if (id == R.id.miLimparLogs) {
				mDrawer.clearLogs();
		} else if (id == R.id.miExit) {
				if (Build.VERSION.SDK_INT >= 16) {
					finishAffinity();
				}
				
				System.exit(0);
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item) {
		// Close drawer
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawerLayoutMain);
		if (drawer != null) {
			drawer.closeDrawer(GravityCompat.START);
		}
		
		int id = item.getItemId();
		if (id == R.id.miPayload) {
			if (mConfig.getPrefsPrivate().getBoolean(Settings.CONFIG_PROTEGER_KEY, false)) {
				Toast.makeText(this, R.string.error_settings_blocked, Toast.LENGTH_SHORT).show();
			} else {
				showPayloadDialog();
			}
			return true;
		} else if (id == R.id.miSNI) {
			if (mConfig.getPrefsPrivate().getBoolean(Settings.CONFIG_PROTEGER_KEY, false)) {
				Toast.makeText(this, R.string.error_settings_blocked, Toast.LENGTH_SHORT).show();
			} else {
				showSNIDialog();
			}
			return true;
		} else if (id == R.id.miSettings) {
			Intent intentSettings = new Intent(this, ConfigGeralActivity.class);
			intentSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intentSettings);
			return true;
		} else if (id == R.id.miCheckUpdate) {
			com.slipkprojects.sockshttp.util.UpdateManager.checkUpdate(this, true);
			return true;
		} else {
			// fallback to onOptionsItemSelected
			return onOptionsItemSelected(item);
		}
	}

	@Override
	public void onBackPressed() {
		DrawerLayout layout = mDrawer.getDrawerLayout();

		if (mDrawerPanel.getDrawerLayout().isDrawerOpen(GravityCompat.START)) {
            mDrawerPanel.getDrawerLayout().closeDrawers();
        }
		else if (layout.isDrawerOpen(GravityCompat.END)) {
            // fecha drawer
			layout.closeDrawers();
        }
		else {
			// mostra opção para sair
			showExitDialog();
		}
	}

	@Override
    public void onResume() {
        super.onResume();

		mDrawer.onResume();
		
		//doSaveData();
		doUpdateLayout();

		if (adsBannerView != null) {
			adsBannerView.resume();
		}
		
		SkStatus.addStateListener(this);
    }

	@Override
	protected void onPause()
	{
		super.onPause();
		
		doSaveData();

		if (adsBannerView != null) {
			adsBannerView.pause();
		}
		
		SkStatus.removeStateListener(this);
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		
		mDrawer.onDestroy();

		if (adsBannerView != null) {
			adsBannerView.destroy();
		}

		LocalBroadcastManager.getInstance(this)
			.unregisterReceiver(mActivityReceiver);
	}


	/**
	 * DrawerLayout Listener
	 */

	@Override
	public void onDrawerOpened(View view) {
		if (view.getId() == R.id.activity_mainLogsDrawerLinear) {
			toolbar_main.getMenu().clear();
			getMenuInflater().inflate(R.menu.logs_menu, toolbar_main.getMenu());
		}
	}

	@Override
	public void onDrawerClosed(View view) {
		if (view.getId() == R.id.activity_mainLogsDrawerLinear) {
			toolbar_main.getMenu().clear();
			getMenuInflater().inflate(R.menu.main_menu, toolbar_main.getMenu());
		}
	}

	@Override
	public void onDrawerStateChanged(int stateId) {}
	@Override
	public void onDrawerSlide(View view, float p2) {}

	
	/**
	 * Utils
	 */

	public static void updateMainViews(Context context) {
		Intent updateView = new Intent(UPDATE_VIEWS);
		LocalBroadcastManager.getInstance(context)
			.sendBroadcast(updateView);
	}
	
	public void showExitDialog() {
		AlertDialog dialog = new AlertDialog.Builder(this).
			create();
		dialog.setTitle(getString(R.string.attention));
		dialog.setMessage(getString(R.string.alert_exit));

		dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.
				string.exit),
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					Utils.exitAll(SocksHttpMainActivity.this);
				}
			}
		);

		dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.
				string.minimize),
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// minimiza app
					Intent startMain = new Intent(Intent.ACTION_MAIN);
					startMain.addCategory(Intent.CATEGORY_HOME);
					startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(startMain);
				}
			}
		);

		dialog.show();
	}
	
	private String getHotspotIpAddress() {
        try {
            String hotspotIp = null;
            String tunIp = null;

            // First pass: look for known hotspot interface names and tun interface
            for (java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                java.net.NetworkInterface intf = en.nextElement();
                String name = intf.getName();
                if (name == null) continue;
                
                if (name.contains("tun")) {
                    for (java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                        java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                            tunIp = inetAddress.getHostAddress();
                        }
                    }
                } else if (name.contains("ap") || name.contains("swlan") || name.equals("wlan1") || name.contains("rndis") || name.contains("tether")) {
                    if (!intf.isUp()) continue;
                    for (java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                        java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                            hotspotIp = inetAddress.getHostAddress();
                        }
                    }
                }
            }
            
            // Second pass: use reflection to check if Wifi AP is enabled and return any valid local IP if not found
            if (hotspotIp == null) {
                android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
                java.lang.reflect.Method method = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
                method.setAccessible(true);
                boolean isApEnabled = (Boolean) method.invoke(wifiManager);
                if (isApEnabled) {
                    for (java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                        java.net.NetworkInterface intf = en.nextElement();
                        String name = intf.getName();
                        if (name == null || name.startsWith("tun") || name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") || name.startsWith("dummy") || name.equals("lo")) {
                            continue; 
                        }
                        if (!intf.isUp()) continue;
                        for (java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                            java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                                hotspotIp = inetAddress.getHostAddress();
                            }
                        }
                    }
                }
            }

            if (hotspotIp != null || tunIp != null) {
                StringBuilder result = new StringBuilder();
                if (tunIp != null) {
                    result.append(tunIp).append(" (IP VPN Recomendada)");
                }
                if (hotspotIp != null) {
                    if (result.length() > 0) result.append("\n    ");
                    result.append(hotspotIp).append(" (IP Hotspot)");
                }
                return result.toString();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

	public void showPayloadDialog() {
		final android.app.Dialog dialog = new android.app.Dialog(this);
		dialog.setContentView(R.layout.dialog_payload);
		dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		
		final EditText editPayload = dialog.findViewById(R.id.editPayload);
		final EditText editRemoteProxy = dialog.findViewById(R.id.editRemoteProxy);
		Button btnApplyPayload = dialog.findViewById(R.id.btnApplyPayload);
		
		SharedPreferences prefs = mConfig.getPrefsPrivate();
		editPayload.setText(prefs.getString(Settings.CUSTOM_PAYLOAD_KEY, ""));
		String ip = prefs.getString(Settings.PROXY_IP_KEY, "");
		String port = prefs.getString(Settings.PROXY_PORTA_KEY, "");
		if (!ip.isEmpty()) {
			editRemoteProxy.setText(ip + ":" + port);
		}
		
		btnApplyPayload.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				SharedPreferences.Editor edit = mConfig.getPrefsPrivate().edit();
				edit.putString(Settings.CUSTOM_PAYLOAD_KEY, editPayload.getText().toString());
				String proxyStr = editRemoteProxy.getText().toString();
				if (!proxyStr.isEmpty()) {
					String[] parts = proxyStr.split(":");
					edit.putString(Settings.PROXY_IP_KEY, parts[0]);
					if (parts.length > 1) {
						edit.putString(Settings.PROXY_PORTA_KEY, parts[1]);
					}
				} else {
					edit.putString(Settings.PROXY_IP_KEY, "");
					edit.putString(Settings.PROXY_PORTA_KEY, "");
				}
				edit.apply();
				dialog.dismiss();
				Toast.makeText(SocksHttpMainActivity.this, "Payload Applied", Toast.LENGTH_SHORT).show();
			}
		});
		dialog.show();
	}
	
	public void showShareNetDialog() {
		final com.slipkprojects.sockshttp.util.ShareNetProxy proxy = com.slipkprojects.sockshttp.util.ShareNetProxy.getInstance();
		
		String ip = getHotspotIpAddress();
		if (ip == null && !proxy.isRunning()) {
		    android.support.v7.app.AlertDialog.Builder errBuilder = new android.support.v7.app.AlertDialog.Builder(this);
		    errBuilder.setTitle("Compartir internet");
		    errBuilder.setMessage("Para poder compartir internet necesitas tener primero el hotspot encendido.");
		    errBuilder.setPositiveButton("Aceptar", null);
		    errBuilder.show();
		    return;
		}
		
		if (ip == null) ip = "192.168.43.1"; // Fallback just in case

		String status = proxy.isRunning() ? "Activo" : "Inactivo";
		String msg = "Para compartir internet por WiFi Hotspot, conecta el otro dispositivo a tu red y configura su Proxy HTTP a:\n\n" +
				"IP: " + ip + "\nPuerto: 7071\n\nEstado: " + status;

		android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
		builder.setTitle("Compartir internet");
		builder.setMessage(msg);
		if (proxy.isRunning()) {
			builder.setPositiveButton("Detener", new android.content.DialogInterface.OnClickListener() {
				@Override
				public void onClick(android.content.DialogInterface dialog, int which) {
					proxy.stop();
					android.widget.Toast.makeText(SocksHttpMainActivity.this, "Compartir internet detenido", android.widget.Toast.LENGTH_SHORT).show();
				}
			});
		} else {
			builder.setPositiveButton("Iniciar", new android.content.DialogInterface.OnClickListener() {
				@Override
				public void onClick(android.content.DialogInterface dialog, int which) {
                    int socksPort = 1080;
                    try {
                        socksPort = Integer.parseInt(mConfig.getPrivString(com.slipkprojects.ultrasshservice.config.Settings.PORTA_LOCAL_KEY));
                    } catch (Exception e) {}
					proxy.start(7071, socksPort);
					android.widget.Toast.makeText(SocksHttpMainActivity.this, "Compartir internet iniciado en el puerto 7071", android.widget.Toast.LENGTH_SHORT).show();
				}
			});
		}
		builder.setNegativeButton("Cerrar", null);
		builder.show();
	}

	public void showSNIDialog() {
		final android.app.Dialog dialog = new android.app.Dialog(this);
		dialog.setContentView(R.layout.dialog_sni);
		dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		
		final EditText editSNI = dialog.findViewById(R.id.editSNI);
		Button btnApplySNI = dialog.findViewById(R.id.btnApplySNI);
		
		SharedPreferences prefs = mConfig.getPrefsPrivate();
		editSNI.setText(prefs.getString(com.slipkprojects.ultrasshservice.config.SettingsConstants.CUSTOM_SNI, ""));
		
		btnApplySNI.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				SharedPreferences.Editor edit = mConfig.getPrefsPrivate().edit();
				edit.putString(com.slipkprojects.ultrasshservice.config.SettingsConstants.CUSTOM_SNI, editSNI.getText().toString());
				edit.apply();
				dialog.dismiss();
				Toast.makeText(SocksHttpMainActivity.this, "SNI Applied", Toast.LENGTH_SHORT).show();
			}
		});
		dialog.show();
	}
	
	public void showUdpCustomSettingsDialog() {
		final android.app.Dialog dialog = new android.app.Dialog(this);
		dialog.setContentView(R.layout.dialog_udp_custom);
		dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		
		final EditText editUdpObfs = dialog.findViewById(R.id.editUdpObfs);
		final EditText editUdpUp = dialog.findViewById(R.id.editUdpUp);
		final EditText editUdpDown = dialog.findViewById(R.id.editUdpDown);
		Button btnApplyUdpCustom = dialog.findViewById(R.id.btnApplyUdpCustom);
		
		SharedPreferences prefs = mConfig.getPrefsPrivate();
		editUdpObfs.setText(prefs.getString(com.slipkprojects.ultrasshservice.config.SettingsConstants.UDP_CUSTOM_OBFS_KEY, ""));
		editUdpUp.setText(prefs.getString(com.slipkprojects.ultrasshservice.config.SettingsConstants.UDP_CUSTOM_UP_KEY, "10"));
		editUdpDown.setText(prefs.getString(com.slipkprojects.ultrasshservice.config.SettingsConstants.UDP_CUSTOM_DOWN_KEY, "50"));
		
		btnApplyUdpCustom.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				SharedPreferences.Editor edit = mConfig.getPrefsPrivate().edit();
				edit.putString(com.slipkprojects.ultrasshservice.config.SettingsConstants.UDP_CUSTOM_OBFS_KEY, editUdpObfs.getText().toString());
				edit.putString(com.slipkprojects.ultrasshservice.config.SettingsConstants.UDP_CUSTOM_UP_KEY, editUdpUp.getText().toString());
				edit.putString(com.slipkprojects.ultrasshservice.config.SettingsConstants.UDP_CUSTOM_DOWN_KEY, editUdpDown.getText().toString());
				edit.apply();
				dialog.dismiss();
				Toast.makeText(SocksHttpMainActivity.this, "UDP Custom Settings Applied", Toast.LENGTH_SHORT).show();
			}
		});
		dialog.show();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 1234 && resultCode == RESULT_OK && data != null) {
            if (!checkSecurity()) return;
			try {
				java.io.InputStream in = getContentResolver().openInputStream(data.getData());
				if (com.slipkprojects.ultrasshservice.config.ConfigParser.convertInputAndSave(in, this)) {
					Toast.makeText(this, R.string.success_import_settings, Toast.LENGTH_SHORT).show();
					Intent intent = new Intent(this, SocksHttpMainActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
					startActivity(intent);
					finish();
				} else {
					Toast.makeText(this, R.string.error_file_config_incompatible, Toast.LENGTH_SHORT).show();
				}
			} catch (java.io.IOException e) {
				Toast.makeText(this, R.string.error_file_config_incompatible, Toast.LENGTH_SHORT).show();
			}
		}
	}
}

