package com.slipkprojects.sockshttp;

import android.app.Activity;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.NavigationView;
import android.content.pm.PackageInfo;
import com.slipkprojects.sockshttp.util.Utils;
import android.support.v7.app.AppCompatActivity;
import com.slipkprojects.sockshttp.R;
import android.view.View;
import android.widget.TextView;
import android.support.annotation.NonNull;
import android.view.MenuItem;
import android.widget.Toast;
import android.os.Build;
import android.content.Intent;
import android.net.Uri;
import com.slipkprojects.sockshttp.util.GoogleFeedbackUtils;
import com.slipkprojects.ultrasshservice.logger.SkStatus;
import android.support.v4.view.GravityCompat;
import com.slipkprojects.sockshttp.activities.ConfigGeralActivity;
import com.slipkprojects.sockshttp.activities.AboutActivity;

public class DrawerPanelMain
	implements NavigationView.OnNavigationItemSelectedListener
{
	private AppCompatActivity mActivity;
	
	public DrawerPanelMain(AppCompatActivity activity) {
		mActivity = activity;
	}
	

	private DrawerLayout drawerLayout;
	private ActionBarDrawerToggle toggle;

	public void setDrawer(Toolbar toolbar) {
		NavigationView drawerNavigationView = (NavigationView) mActivity.findViewById(R.id.drawerNavigationView);
		drawerLayout = (DrawerLayout) mActivity.findViewById(R.id.drawerLayoutMain);

		// set drawer
		toggle = new ActionBarDrawerToggle(mActivity,
			drawerLayout, toolbar, R.string.open, R.string.cancel);

        drawerLayout.setDrawerListener(toggle);

		toggle.syncState();

		// set app info
		PackageInfo pinfo = Utils.getAppInfo(mActivity);
		if (pinfo != null) {
			String version_nome = pinfo.versionName;
			int version_code = pinfo.versionCode;
			String header_text = String.format("v. %s (%d)", version_nome, version_code);

			View view = drawerNavigationView.getHeaderView(0);

			TextView app_info_text = view.findViewById(R.id.nav_headerAppVersion);
			app_info_text.setText(header_text);
		}

		// set navigation view
		drawerNavigationView.setNavigationItemSelectedListener(this);
	}
	
	public ActionBarDrawerToggle getToogle() {
		return toggle;
	}
	
	public DrawerLayout getDrawerLayout() {
		return drawerLayout;
	}
	
	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item) {
		int id = item.getItemId();

		if (id == R.id.miShareNet) {
				if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
					drawerLayout.closeDrawers();
				}
				if (mActivity instanceof SocksHttpMainActivity) {
					((SocksHttpMainActivity)mActivity).showShareNetDialog();
				}
		} else if (id == R.id.miPayload) {
				if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
					drawerLayout.closeDrawers();
				}
				if (mActivity instanceof SocksHttpMainActivity) {
					SocksHttpMainActivity mainAct = (SocksHttpMainActivity)mActivity;
					if (new com.slipkprojects.ultrasshservice.config.Settings(mainAct).getPrefsPrivate().getBoolean(com.slipkprojects.ultrasshservice.config.Settings.CONFIG_PROTEGER_KEY, false)) {
						android.widget.Toast.makeText(mainAct, R.string.error_settings_blocked, android.widget.Toast.LENGTH_SHORT).show();
					} else {
						mainAct.showPayloadDialog();
					}
				}
		} else if (id == R.id.miSNI) {
				if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
					drawerLayout.closeDrawers();
				}
				if (mActivity instanceof SocksHttpMainActivity) {
					SocksHttpMainActivity mainAct = (SocksHttpMainActivity)mActivity;
					if (new com.slipkprojects.ultrasshservice.config.Settings(mainAct).getPrefsPrivate().getBoolean(com.slipkprojects.ultrasshservice.config.Settings.CONFIG_PROTEGER_KEY, false)) {
						android.widget.Toast.makeText(mainAct, R.string.error_settings_blocked, android.widget.Toast.LENGTH_SHORT).show();
					} else {
						mainAct.showSNIDialog();
					}
				}
		} else if (id == R.id.miUdpCustomSettings) {
				if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
					drawerLayout.closeDrawers();
				}
				if (mActivity instanceof SocksHttpMainActivity) {
					SocksHttpMainActivity mainAct = (SocksHttpMainActivity)mActivity;
					if (new com.slipkprojects.ultrasshservice.config.Settings(mainAct).getPrefsPrivate().getBoolean(com.slipkprojects.ultrasshservice.config.Settings.CONFIG_PROTEGER_KEY, false)) {
						android.widget.Toast.makeText(mainAct, R.string.error_settings_blocked, android.widget.Toast.LENGTH_SHORT).show();
					} else {
						android.content.Intent intent = new android.content.Intent(mainAct, com.slipkprojects.sockshttp.activities.HysteriaSettingsActivity.class);
						mainAct.startActivity(intent);
					}
				}
		} else if (id == R.id.miV2raySettings) {
				if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
					drawerLayout.closeDrawers();
				}
				if (mActivity instanceof SocksHttpMainActivity) {
					SocksHttpMainActivity mainAct = (SocksHttpMainActivity)mActivity;
					if (new com.slipkprojects.ultrasshservice.config.Settings(mainAct).getPrefsPrivate().getBoolean(com.slipkprojects.ultrasshservice.config.Settings.CONFIG_PROTEGER_KEY, false)) {
						android.widget.Toast.makeText(mainAct, R.string.error_settings_blocked, android.widget.Toast.LENGTH_SHORT).show();
					} else {
						android.content.Intent intent = new android.content.Intent(mainAct, com.slipkprojects.sockshttp.activities.V2raySettingsActivity.class);
						mainAct.startActivity(intent);
					}
				}
		} else if (id == R.id.miHWID) {
				if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
					drawerLayout.closeDrawers();
				}
				final String hwid = com.slipkprojects.sockshttp.util.Utils.getHWID(mActivity);
				android.support.v7.app.AlertDialog dialog = new android.support.v7.app.AlertDialog.Builder(mActivity)
					.setTitle("HWID")
					.setMessage(hwid)
					.setPositiveButton("Copiar", new android.content.DialogInterface.OnClickListener() {
						@Override
						public void onClick(android.content.DialogInterface dialog, int which) {
							try {
								com.slipkprojects.sockshttp.util.Utils.copyToClipboard(mActivity, hwid);
								android.widget.Toast.makeText(mActivity, "HWID copiado", android.widget.Toast.LENGTH_SHORT).show();
							} catch (Exception e) {}
						}
					})
					.setNegativeButton("Cerrar", null)
					.create();
				dialog.show();
		} else if (id == R.id.miSettings) {
				Intent intent = new Intent(mActivity, ConfigGeralActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				mActivity.startActivity(intent);
		} else if (id == R.id.miOptimizarBateria) {
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
				Intent intent = new Intent();
				String packageName = mActivity.getPackageName();
				android.os.PowerManager pm = (android.os.PowerManager) mActivity.getSystemService(android.content.Context.POWER_SERVICE);
				if (!pm.isIgnoringBatteryOptimizations(packageName)) {
					intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
					intent.setData(android.net.Uri.parse("package:" + packageName));
					mActivity.startActivity(intent);
				} else {
					android.widget.Toast.makeText(mActivity, "La optimización de batería ya está desactivada.", android.widget.Toast.LENGTH_SHORT).show();
				}
			}
		} else if (id == R.id.miSendFeedback) {
				if (false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
					try {
						GoogleFeedbackUtils.bindFeedback(mActivity);
					} catch (Exception e) {
						Toast.makeText(mActivity, "No disponible en su dispositivo", Toast.LENGTH_SHORT)
							.show();
						SkStatus.logDebug("Error: " + e.getMessage());
					}
				}
				else {
					Intent email = new Intent(Intent.ACTION_SEND);  
					email.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

					email.putExtra(Intent.EXTRA_EMAIL, new String[]{"slipkprojects@gmail.com"});  
					email.putExtra(Intent.EXTRA_SUBJECT, "SocksHttp - " + mActivity.getString(R.string.feedback));  
					//email.putExtra(Intent.EXTRA_TEXT, "");  

					//need this to prompts email client only  
					email.setType("message/rfc822");  

					mActivity.startActivity(Intent.createChooser(email, "Choose an Email client:"));
				}
		} else if (id == R.id.miAbout) {
				if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            		drawerLayout.closeDrawers();
        		}
				Intent aboutIntent = new Intent(mActivity, AboutActivity.class);
				mActivity.startActivity(aboutIntent);
		} else if (id == R.id.miCheckUpdate) {
				if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
					drawerLayout.closeDrawers();
				}
				com.slipkprojects.sockshttp.util.UpdateManager.checkUpdate(mActivity, true);
		}

		return true;
	}
}
