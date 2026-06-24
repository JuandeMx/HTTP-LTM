package com.slipkprojects.sockshttp;

import android.app.Activity;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.AdListener;
import android.os.SystemClock;
import android.util.Log;
import com.google.android.gms.ads.AdRequest;
import android.content.Context;
import android.widget.Toast;
import android.os.CountDownTimer;
import android.content.SharedPreferences;

/**
* Ads Manager
* @author dFiR30n
*/

public class AdsManager
{
	private final String TAG = "AdsManager";
	private static final boolean FORCE_TEST_ADS = false; // Cambiar a true para forzar anuncios de prueba en compilación release
	
	private Context mContext;
	private SharedPreferences mPrefs;
	
	private InterstitialAd mInterstitialAd;
	private boolean mShowOnLoad = false;
	private int mRetryCount = 0;
	
	public static AdsManager newInstance(Context context) {
		return new AdsManager(context);
	}
	
	private AdsManager(Context context){
		mContext = context;
		mPrefs = context.getSharedPreferences(SocksHttpApp.PREFS_GERAL, Context.MODE_PRIVATE);
		
		// Ads interstitial
		setupAdsInterstitial();
	}
	
	private void setupAdsInterstitial() {
		mInterstitialAd = new InterstitialAd(mContext);
		
		if (!BuildConfig.DEBUG && !FORCE_TEST_ADS)
			mInterstitialAd.setAdUnitId(SocksHttpApp.ADS_UNITID_INTERSTITIAL_MAIN);
		else
			mInterstitialAd.setAdUnitId("ca-app-pub-3940256099942544/1033173712");
		
		mInterstitialAd.setAdListener(new AdListener() {
			@Override
			public void onAdLoaded() {
				Log.d(TAG, "Interstitial ad loaded successfully.");
				mRetryCount = 0;
				if (mShowOnLoad) {
					mShowOnLoad = false;
					if (mInterstitialAd != null && mInterstitialAd.isLoaded()) {
						mInterstitialAd.show();
					}
				}
			}

			@Override
			public void onAdFailedToLoad(int errorCode) {
				String errorMsg;
				switch (errorCode) {
					case AdRequest.ERROR_CODE_INTERNAL_ERROR:
						errorMsg = "Error interno de AdMob (0)";
						break;
					case AdRequest.ERROR_CODE_INVALID_REQUEST:
						errorMsg = "Solicitud de anuncio inválida (1)";
						break;
					case AdRequest.ERROR_CODE_NETWORK_ERROR:
						errorMsg = "Fallo de red en AdMob (2)";
						break;
					case AdRequest.ERROR_CODE_NO_FILL:
						errorMsg = "Sin anuncios disponibles en AdMob / NO_FILL (3)";
						break;
					default:
						errorMsg = "Error desconocido de AdMob (" + errorCode + ")";
				}
				Log.e(TAG, "Interstitial ad failed to load: " + errorMsg);

				// Show a short helper message to diagnose fill rate easily only after exhausting retries
				if (mShowOnLoad && mRetryCount >= 3) {
					Toast.makeText(mContext, "AdMob: " + errorMsg, Toast.LENGTH_SHORT).show();
				}

				if (mShowOnLoad && mRetryCount < 3) {
					mRetryCount++;
					Log.d(TAG, "Retrying ad load (" + mRetryCount + "/3) after failure...");
					new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
						@Override
						public void run() {
							if (mShowOnLoad) {
								loadAdsInterstitial(true);
							}
						}
					}, 3000);
				} else {
					mShowOnLoad = false;
					mRetryCount = 0;
				}
			}

			@Override
			public void onAdOpened() {
				if (mPrefs != null) {
					SharedPreferences.Editor pEdit = mPrefs.edit();
					pEdit.putLong("last_ads_time", SystemClock.elapsedRealtime());
					pEdit.apply();
				}
			}

			@Override
			public void onAdClicked() {
			}

			@Override
			public void onAdLeftApplication() {
			}

			@Override
			public void onAdClosed() {
				Toast.makeText(mContext, "¡Gracias por apoyar la aplicación! 💙", Toast.LENGTH_SHORT)
					.show();
				mShowOnLoad = false;
				mRetryCount = 0;
				// Preload the next ad
				loadAdsInterstitial(true);
			}
		});
	}
	
	public void showAdsInterstitial() {
		mRetryCount = 0;
		if (mInterstitialAd != null && mInterstitialAd.isLoaded()) {
			mInterstitialAd.show();
		} else {
			Log.d(TAG, "Interstitial ad not loaded. Requesting load...");
			mShowOnLoad = true;
			loadAdsInterstitial(true);
		}
	}

	public void cancelShowOnLoad() {
		Log.d(TAG, "Canceling show-on-load flag.");
		mShowOnLoad = false;
		mRetryCount = 0;
	}
	
	public void loadAdsInterstitial() {
		loadAdsInterstitial(false);
	}

	public void loadAdsInterstitial(boolean force) {
		// carga anuncio cada 1 hora o si es forzado
		long time = 60*60*1;
		if (mInterstitialAd != null) {
			if (force || ((SystemClock.elapsedRealtime() - mPrefs.getLong("last_ads_time", 0)) / 1000) >= time) {
				mInterstitialAd.loadAd(new AdRequest.Builder().build());
				Log.d(TAG, "Cargando anuncio intersticial (force=" + force + ")..");
			}
		}
	}
	
	
	/**
	* Ads Timer
	*/
	private CountDownTimer countDownTimer;
	private long timerMilliseconds;
	
	private void createTimer(final long milliseconds) {
        // Create the game timer, which counts down to the end of the level
        // and shows the "retry" button.
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(milliseconds, 50) {
            @Override
            public void onTick(long millisUnitFinished) {
                timerMilliseconds = millisUnitFinished;
            }

            @Override
            public void onFinish() {
				loadAdsInterstitial(); // carrega novo anúncio
            }
        };
    }
	
}
