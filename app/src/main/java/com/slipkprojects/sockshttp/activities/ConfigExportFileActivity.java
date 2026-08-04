package com.slipkprojects.sockshttp.activities;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import com.slipkprojects.sockshttp.R;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.Button;
import android.view.View;
import android.widget.Toast;
import android.widget.CompoundButton;
import android.content.Intent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.File;
import java.io.IOException;
import android.os.Environment;
import java.util.Calendar;
import android.app.DatePickerDialog;
import android.widget.DatePicker;
import android.content.DialogInterface;
import java.util.concurrent.atomic.AtomicReference;
import android.util.Log;
import java.text.DateFormat;
import java.util.Date;
import com.slipkprojects.ultrasshservice.util.FileUtils;
import com.slipkprojects.ultrasshservice.config.Settings;
import com.slipkprojects.ultrasshservice.config.ConfigParser;
import android.support.v7.widget.AppCompatCheckBox;
import java.io.FileOutputStream;
import android.widget.CheckBox;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.webkit.WebView;

public class ConfigExportFileActivity
	extends BaseActivity
		implements CompoundButton.OnCheckedChangeListener, View.OnClickListener
{
	private static final String TAG = ConfigExportFileActivity.class.getSimpleName();
	
	private Settings mConfig;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		mConfig = new Settings(this);
		
		doLayout();
		
		// requista permissões ao armazenamento externo
		requestPermissions();
	}

	@Override
	public boolean onSupportNavigateUp()
	{
		super.onBackPressed();
		return true;
	}
	
	
	/**
	* Main Views
	*/
	private AppCompatCheckBox validadeCheck;
	private TextView validadeText;
	private EditText nomeEdit;
	private EditText mensagemEdit;
	private EditText hwidEdit;
	
	private AppCompatCheckBox addNoteCheck;
	private AppCompatCheckBox hideServerMessageCheck;
	private LinearLayout noteLayout;
	
	private boolean mIsProteger = false;
	private String mMensagem = "";
	private boolean mPedirSenha = false;
	private boolean mBloquearRoot = false;
	private String mTargetHwid = "";
	private boolean mSshAuthHwid = false;
	
	private void doLayout() {
		setContentView(R.layout.activity_config_export);
		
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_main);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		// impede autoinicio dos editText
		findViewById(R.id.activity_config_exportMainLinearLayout)
			.requestFocus();
		
		nomeEdit = (EditText) findViewById(R.id.activity_config_exportNomeEdit);
		AppCompatCheckBox protegerCheck = (AppCompatCheckBox) findViewById(R.id.activity_config_exportProtegerCheck);
		validadeCheck = (AppCompatCheckBox) findViewById(R.id.activity_config_exportValidadeCheck);
		validadeText = (TextView) findViewById(R.id.activity_config_exportValidadeText);
		mensagemEdit = (EditText) findViewById(R.id.activity_config_exportMensagemEdit);
		Button exportarButton = (Button) findViewById(R.id.activity_config_exportButton);
		AppCompatCheckBox showLoginCheck = (AppCompatCheckBox) findViewById(R.id.activity_config_exportShowLoginScreenCheck);
		AppCompatCheckBox blockRootCheck = (AppCompatCheckBox) findViewById(R.id.activity_config_exportBlockRootCheck);
		AppCompatCheckBox blockHwidCheck = (AppCompatCheckBox) findViewById(R.id.activity_config_exportBlockHwidCheck);
		AppCompatCheckBox sshAuthHwidCheck = (AppCompatCheckBox) findViewById(R.id.activity_config_exportSshAuthHwidCheck);
		hwidEdit = (EditText) findViewById(R.id.activity_config_exportHwidEdit);
		
		addNoteCheck = (AppCompatCheckBox) findViewById(R.id.activity_config_exportAddNoteCheck);
		hideServerMessageCheck = (AppCompatCheckBox) findViewById(R.id.activity_config_exportHideServerMessageCheck);
		noteLayout = (LinearLayout) findViewById(R.id.activity_config_exportNoteLayout);
		Button previewNoteButton = (Button) findViewById(R.id.activity_config_exportPreviewNoteButton);
		
		showSegurancaLayout(false);
		mensagemEdit.setText(mConfig.getMensagemConfigExportar());
		if (mensagemEdit.getText().toString().length() > 0) {
			addNoteCheck.setChecked(true);
			noteLayout.setVisibility(View.VISIBLE);
		}
		
		hideServerMessageCheck.setChecked(mConfig.getPrefsPrivate().getBoolean(Settings.HIDE_SERVER_MESSAGE_KEY, false));
		
		validadeCheck.setOnCheckedChangeListener(this);
		protegerCheck.setOnCheckedChangeListener(this);
		exportarButton.setOnClickListener(this);
		showLoginCheck.setOnCheckedChangeListener(this);
		blockRootCheck.setOnCheckedChangeListener(this);
		blockHwidCheck.setOnCheckedChangeListener(this);
		sshAuthHwidCheck.setOnCheckedChangeListener(this);
		
		addNoteCheck.setOnCheckedChangeListener(this);
		hideServerMessageCheck.setOnCheckedChangeListener(this);
		previewNoteButton.setOnClickListener(this);
	}
	
	
	private File exportConfiguracao(String nome)
			throws IOException {
		if (!FileUtils.isExternalStorageWritable()) {
			throw new IOException(getString(R.string.error_permission_writer_required));
		}
		
		File fileDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Http LTM");
		
		if (!fileDir.exists()) {
			fileDir.mkdirs();
		}
		
		File fileExport = new File(fileDir, String.format("%s.%s", nome, ConfigParser.FILE_EXTENSAO));
		if (!fileExport.exists()) {
			try {
				fileExport.createNewFile();
			} catch(IOException e) {
				throw new IOException(getString(R.string.error_save_settings));
			}
		}
		
		// salva mensaje y opcion
		if (addNoteCheck.isChecked()) {
			mConfig.setMensagemConfigExportar(mMensagem);
			mConfig.getPrefsPrivate().edit().putBoolean(Settings.HIDE_SERVER_MESSAGE_KEY, hideServerMessageCheck.isChecked()).apply();
		} else {
		    mConfig.getPrefsPrivate().edit().putBoolean(Settings.HIDE_SERVER_MESSAGE_KEY, false).apply();
		}
		
		try {
			ConfigParser.convertDataToFile(new FileOutputStream(fileExport), this,
				mIsProteger, mPedirSenha, mBloquearRoot, mTargetHwid, mMensagem, mValidade, mSshAuthHwid, hideServerMessageCheck.isChecked());
		} catch(IOException e) {
			fileExport.delete();
			throw e;
		}
		return fileExport;
	}
	
	
	/**
	* Validade
	*/
	
	private long mValidade = 0;
	
	private void setValidadeDate() {
		
		// Get Current Date
		Calendar c = Calendar.getInstance();
		final long time_hoje = c.getTimeInMillis();
		
		c.setTimeInMillis(time_hoje+(1000*60*60*24));
		
		int mYear = c.get(Calendar.YEAR);
		int mMonth = c.get(Calendar.MONTH);
		int mDay = c.get(Calendar.DAY_OF_MONTH);

		mValidade = c.getTimeInMillis();

		final DatePickerDialog dialog = new DatePickerDialog(this,
			new DatePickerDialog.OnDateSetListener() {
				@Override
				public void onDateSet(DatePicker p1, int year, int monthOfYear, int dayOfMonth) {
					Calendar c = Calendar.getInstance();
					c.set(year, monthOfYear, dayOfMonth);

					mValidade = c.getTimeInMillis();
				}
			},
			mYear, mMonth, mDay);
		
		dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.ok),
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog2, int which) {
					DateFormat df = DateFormat.getDateInstance();
					DatePicker date = dialog.getDatePicker();
					
					Calendar c = Calendar.getInstance();
					c.set(date.getYear(), date.getMonth(), date.getDayOfMonth());
					
					mValidade = c.getTimeInMillis();
					
					if (mValidade < time_hoje) {
						mValidade = 0;

						Toast.makeText(getApplicationContext(), R.string.error_date_selected_invalid,
							Toast.LENGTH_SHORT).show();
						
						if (validadeCheck != null)
							validadeCheck.setChecked(false);
					}
					else {
						long dias = ((mValidade-time_hoje)/1000/60/60/24);

						if (validadeText != null) {
							validadeText.setVisibility(View.VISIBLE);
							validadeText.setText(String.format("%s (%s)", dias, df.format(mValidade)));
						}
					}
				}
			}
		);

		dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel),
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mValidade = 0;
					
					if (validadeCheck != null) {
						validadeCheck.setChecked(false);
					}
				}
			}
		);

		dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface v1) {
				mValidade = 0;
				if (validadeCheck != null) {
					validadeCheck.setChecked(false);
				}
			}
		});
		
		dialog.show();
	}
	
	private void requestPermissions() {
		FileUtils.requestForPermissionExternalStorage(this);
	}
	
	
	/**
	* Oculta/Mostra layout com opções
	*/
	
	private int[] idsProtegerViews = {
		R.id.activity_config_exportValidadeCheck,
		R.id.activity_config_exportValidadeText,
		R.id.activity_config_exportBlockRootCheck,
		R.id.activity_config_exportShowLoginScreenCheck
	};
	
	private int[] idsProtegerChecksView = {
		R.id.activity_config_exportValidadeCheck,
		R.id.activity_config_exportBlockRootCheck,
		R.id.activity_config_exportShowLoginScreenCheck
	};
	
	private void showSegurancaLayout(boolean is) {
		if (is) {
			Toast.makeText(this, R.string.alert_block_settings,
				Toast.LENGTH_LONG).show();
		}
		else {
			for (int id : idsProtegerChecksView) {
				((CheckBox) findViewById(id)).setChecked(false);
			}
		}
		
		for (int id : idsProtegerViews) {
			findViewById(id).setEnabled(is);
		}
	}
	
	
	@Override
	public void onCheckedChanged(CompoundButton p1, boolean is)
	{
		int id = p1.getId();
		if (id == R.id.activity_config_exportValidadeCheck) {
				if (is) {
					setValidadeDate();
				}
				else {
					mValidade = 0;
					if (validadeText != null) {
						validadeText.setVisibility(View.INVISIBLE);
						validadeText.setText("");
					}
				}
		} else if (id == R.id.activity_config_exportProtegerCheck) {
				mIsProteger = is;
				showSegurancaLayout(is);
		} else if (id == R.id.activity_config_exportShowLoginScreenCheck) {
				mPedirSenha = is;
		} else if (id == R.id.activity_config_exportBlockRootCheck) {
				mBloquearRoot = is;
		} else if (id == R.id.activity_config_exportSshAuthHwidCheck) {
				mSshAuthHwid = is;
		} else if (id == R.id.activity_config_exportBlockHwidCheck) {
				if (is) {
					hwidEdit.setVisibility(View.VISIBLE);
				} else {
					hwidEdit.setVisibility(View.GONE);
					hwidEdit.setText("");
				}
		} else if (id == R.id.activity_config_exportAddNoteCheck) {
				if (is) {
					noteLayout.setVisibility(View.VISIBLE);
				} else {
					noteLayout.setVisibility(View.GONE);
				}
		}
	}
	
	
	@Override
	public void onClick(View p1) {
		int id = p1.getId();
		if (id == R.id.activity_config_exportPreviewNoteButton) {
				String noteText = mensagemEdit.getText().toString();
				if (noteText.isEmpty()) return;
				
				WebView webView = new WebView(this);
				webView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
				String htmlData = "<html><head><style>body { color: black; margin: 0; padding: 10px; word-wrap: break-word; }</style></head><body>" + noteText + "</body></html>";
				webView.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null);
				
				new AlertDialog.Builder(this)
					.setTitle("Previsualizar Nota")
					.setView(webView)
					.setPositiveButton(R.string.ok, null)
					.show();
		} else if (id == R.id.activity_config_exportButton) {
				String nomeConfig = nomeEdit.getText().toString();
				mMensagem = addNoteCheck.isChecked() ? mensagemEdit.getText().toString() : "";
				mTargetHwid = hwidEdit.getText().toString().trim();
				
				if (nomeConfig.isEmpty()) {
					Toast.makeText(ConfigExportFileActivity.this, R.string.error_empty_name_file, Toast.LENGTH_SHORT)
						.show();
					return;
				}

				if (mIsProteger == false || mValidade < 0) {
					mValidade = 0;
				}

				try {
					File exported = exportConfiguracao(nomeConfig);

					Toast.makeText(ConfigExportFileActivity.this, R.string.success_export_settings, Toast.LENGTH_SHORT)
						.show();

					// Share file
					android.net.Uri uri = android.support.v4.content.FileProvider.getUriForFile(
						ConfigExportFileActivity.this, 
						getPackageName() + ".provider", 
						exported);
					Intent shareIntent = new Intent(Intent.ACTION_SEND);
					shareIntent.setType("*/*");
					shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
					shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
					startActivity(Intent.createChooser(shareIntent, "Compartir configuración"));

				} catch(IOException e) {
					Toast.makeText(ConfigExportFileActivity.this, R.string.error_export_settings, Toast.LENGTH_SHORT)
						.show();

					Toast.makeText(ConfigExportFileActivity.this, e.getMessage(), Toast.LENGTH_SHORT)
						.show();
				}

				onBackPressed();
		}
	}
	
}
