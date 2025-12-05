package io.github.edo9300.edopro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import libwindbot.windbot.WindBot;

public class MainActivity extends Activity {
	private final static boolean wantsScopedStorage = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q;

	private final static int PERMISSIONS = 1;
	private static final String[] REQUIRED_SDK_PERMISSIONS = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
	private static String working_directory;
	private static boolean changelog;
	private static ArrayList<String> parameter;

	final int COPY_ASSETS = 1;
	final int CHOOSE_WORKING_DIR = 2;

	@RequiresApi(Build.VERSION_CODES.R)
	private void showFileManager() {
		final var EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents";
		final var DOCUMENT_ID_PRIMARY = "primary";
		final var DOCUMENT_ID_PRIMARY_ANDROID_DATA = "primary:Android/data/" + getApplicationContext().getPackageName() + "/files/EDOPro";
		final var TREE_URI_PRIMARY_ANDROID = DocumentsContract.buildTreeDocumentUri(EXTERNAL_STORAGE_PROVIDER_AUTHORITY, DOCUMENT_ID_PRIMARY);
		final var DOCUMENT_URI_ANDROID_DATA = DocumentsContract.buildDocumentUriUsingTree(TREE_URI_PRIMARY_ANDROID, DOCUMENT_ID_PRIMARY_ANDROID_DATA);

		final var EDOPRO_PROVIDER_AUTHORITY = getApplicationContext().getPackageName() + ".document_provider";
		final var filePath = getExternalFilesDir("EDOPro").getPath();
		final var EDOPRO_TREE_URI_PRIMARY_ANDROID = DocumentsContract.buildTreeDocumentUri(EDOPRO_PROVIDER_AUTHORITY, filePath);
		final var EDOPRO_DOCUMENT_URI_ANDROID_DATA = DocumentsContract.buildDocumentUriUsingTree(EDOPRO_TREE_URI_PRIMARY_ANDROID, filePath);
		final var intents = Arrays.asList(new Intent()
						.setComponent(ComponentName.createRelative("com.google.android.documentsui", "com.android.documentsui.files.FilesActivity"))
						.setAction("android.intent.action.VIEW")
						.setData(EDOPRO_DOCUMENT_URI_ANDROID_DATA),
				new Intent().
						setComponent(ComponentName.createRelative("com.android.documentsui", ".files.FilesActivity"))
						.setAction("android.intent.action.VIEW")
						.setData(EDOPRO_DOCUMENT_URI_ANDROID_DATA),
				new Intent()
						.setComponent(ComponentName.createRelative("com.google.android.documentsui", "com.android.documentsui.files.FilesActivity"))
						.setAction("android.intent.action.VIEW")
						.setData(DOCUMENT_URI_ANDROID_DATA),
				new Intent().
						setComponent(ComponentName.createRelative("com.android.documentsui", ".files.FilesActivity"))
						.setAction("android.intent.action.VIEW")
						.setData(DOCUMENT_URI_ANDROID_DATA),
				new Intent("android.intent.action.VIEW")
						.setData(Uri.parse("content://com.android.externalstorage.documents/root/primary"))
		);

		for (final var intent : intents) {
			try {
				startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
				new Handler().postDelayed(this::finishAndRemoveTask, 600L);
				return;
			} catch (Exception ignored) {
			}
		}
		this.finishAndRemoveTask();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		var intent = getIntent();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			var extras = this.getIntent().getExtras();
			if (extras != null) {
				var manageString = extras.getString("manageFiles");
				if ("yes".equals(manageString)) {
					showFileManager();
					return;
				}
			}
		}
		if (!isTaskRoot()) {
			if (intent.hasCategory(Intent.CATEGORY_LAUNCHER)
					&& intent.getAction() != null
					&& intent.getAction().equals(Intent.ACTION_MAIN)) {
				finish();
				return;
			}
		}
		parameter = new ArrayList<>();
		if (intent.getAction() != null
				&& intent.getAction().equals(Intent.ACTION_VIEW)) {
			Log.e("Edopro open file", "aa");
			if (!isTaskRoot()) {
				Log.e("Edopro open file", "bb");
				/* TODO: Send drop event */
				finish();
				return;
			}
			Uri data = intent.getData();
			if (data != null) {
				intent.setData(null);
				try {
					Log.e("Edopro", data.getPath());
					var path = FileUtil.getFullPathFromTreeUri(data, this);
					parameter.add(path);
					Log.e("EDOPro-KCG", "parsed path: " + parameter);
				} catch (Exception e) {
					var path = data.getPath();
					parameter.add(path);
					Log.e("EDOPro-KCG", "It was already a path: " + data.getPath());
				}
			}
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (wantsScopedStorage) {
				// we skip the permissions check as we need none
				getWorkingDirectory();
			} else {
				checkPermission();
			}
		} else {
			getWorkingDirectory();
		}
	}

	protected void checkPermission() {
		final List<String> missingPermissions = new ArrayList<>();
		// check required permission
		for (final String permission : REQUIRED_SDK_PERMISSIONS) {
			final var result = ContextCompat.checkSelfPermission(this, permission);
			if (result != PackageManager.PERMISSION_GRANTED) {
				missingPermissions.add(permission);
			}
		}
		if (!missingPermissions.isEmpty()) {
			// request permission
			final var permissions = missingPermissions.toArray(new String[0]);
			ActivityCompat.requestPermissions(this, permissions, PERMISSIONS);
		} else {
			final var grantResults = new int[REQUIRED_SDK_PERMISSIONS.length];
			Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
			onRequestPermissionsResult(PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
					grantResults);
		}
	}

	@Override
	@SuppressWarnings("SwitchStatementWithTooFewBranches")
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
										   @NonNull int[] grantResults) {
		switch (requestCode) {
			case PERMISSIONS:
				for (int index = 0; index < permissions.length; index++) {
					if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
						// permission not granted - toast and exit
						Toast.makeText(this, R.string.not_granted, Toast.LENGTH_LONG).show();
						finish();
						return;
					}
				}
				// permission were granted - run
				getWorkingDirectory();
				break;
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case COPY_ASSETS: {
				try {
					var file = new File(getFilesDir(), "assets_copied");
					if (file.exists() || file.createNewFile()) {
						var wr = new FileWriter(file);
						wr.write("" + BuildConfig.VERSION_CODE);
						wr.flush();
					}
				} catch (Exception e) {
					Log.e("EDOPro-KCG", "error when creating assets_copied file: " + e.getMessage());
				}
				finish();
				break;
			}
			case CHOOSE_WORKING_DIR: {
				if (resultCode == Activity.RESULT_CANCELED) {
					break;
				}
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
					return;
				}
				var uri = data.getData();
				Log.i("EDOPro", "Result URI " + uri);
				var dest_dir = FileUtil.getFullPathFromTreeUri(uri, this);
				if (dest_dir == null) {
					Log.e("EDOPro-KCG", "returned URI is null");
					finish();
					break;
				}
				Log.i("EDOPro-KCG", "Parsed result URI " + dest_dir);
				if (dest_dir.startsWith("/storage/emulated/0"))
					setWorkingDir(dest_dir);
				else {
					var paths = getExternalFilesDirs("EDOPro-KCG");
					var dirs = dest_dir.split("/");
					var found = false;
					if (dirs.length > 2) {
						String storage = dirs[2];
						for (int i = 0; i < paths.length; i++) {
							Log.i("EDOPro-KCG", "Path " + i + " is: " + paths[i]);
							if (storage.equals(paths[i].getAbsolutePath().split("/")[2])) {
								Log.i("EDOPro-KCG", "path matching with " + dest_dir + " is: " + paths[i].getAbsolutePath());
								dest_dir = paths[i].getAbsolutePath();
								if (!paths[i].exists()) {
									paths[i].mkdirs();
								}
								found = true;
								break;
							}
						}
					}
					if (found) {
						Toast.makeText(this, String.format(getResources().getString(R.string.default_dir), dest_dir), Toast.LENGTH_LONG).show();
						final var cbdir = dest_dir;
						new AlertDialog.Builder(this)
								.setMessage(String.format(getResources().getString(R.string.default_path), dest_dir))
								.setCancelable(false)
								.setPositiveButton("OK", new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										setWorkingDir(cbdir);
									}
								})
								.create().show();
					} else {
						Toast.makeText(this, getResources().getString(R.string.no_matching), Toast.LENGTH_LONG).show();
						Log.e("EDOPro-KCG", "couldn't find matching storage");
						finish();
					}
				}
				break;
			}
		}
	}

	public void next() {
		var use_windbot = true;
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
			use_windbot = false;
		} else {
			try {
				/*
				 * windbot loading might fail, for whatever reason,
				 * disable it if that's the case
				 */
				WindBot.initAndroid(working_directory + "/WindBot");
			} catch (Exception e) {
				use_windbot = false;
			}
		}
		/*
			pass the working directory via parameters, rather than making
			the app read the working_dir file
		*/
		if (changelog)
			parameter.add(0, "-l");
		parameter.add(0, working_directory + "/");
		parameter.add(0, "-C");
		var array = parameter.toArray();
		var strArr = new String[array.length];
		for (int i = 0; i < array.length; i++) {
			strArr[i] = array[i].toString();
		}
		var intent = new Intent(this, EpNativeActivity.class);
		intent.putExtra("ARGUMENTS", strArr);
		intent.putExtra("USE_WINDBOT", use_windbot);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);
	}

	public void getWorkingDirectory() {
		var file = new File(getFilesDir(), "working_dir");
		if (file.exists()) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(file));
				working_directory = br.readLine();
				br.close();
			} catch (IOException e) {
				Log.e("EDOPro-KCG", "working directory file found but not read: " + e.getMessage());
			}
		}

		if (working_directory != null) {
			copyAssetsPrompt(working_directory);
			return;
		}
		getDefaultPath();
	}

	public void getDefaultPath() {
		final File path;
		if (wantsScopedStorage) {
			path = new File(getExternalFilesDir("EDOPro-KCG").getPath());
		} else {
			path = new File(Environment.getExternalStorageDirectory() + "/EDOPro-KCG");
		}
		final String dest_dir = path.getAbsolutePath();
		if (!dest_dir.isEmpty()) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
				Toast.makeText(this, String.format(getResources().getString(R.string.default_dir), dest_dir), Toast.LENGTH_LONG).show();
				builder.setMessage(String.format(getResources().getString(R.string.default_dir), dest_dir))
						.setCancelable(false)
						.setPositiveButton("OK", (dialog, id) -> {
							if (!path.exists()) {
								path.mkdirs();
							}
							setWorkingDir(dest_dir);
						});
			} else if (!wantsScopedStorage) {
				builder.setMessage(String.format(getResources().getString(R.string.default_dir_changeable), dest_dir))
						.setCancelable(false)
						.setPositiveButton(R.string.keep_game_folder, (dialog, id) -> {
							if (!path.exists()) {
								path.mkdirs();
							}
							setWorkingDir(dest_dir);
						})
						.setNeutralButton(R.string.change_game_folder, (dialog, id) -> chooseWorkingDir());
			}
			AlertDialog alert = builder.create();
			alert.show();
		}
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	public void chooseWorkingDir() {
		var i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		if (android.os.Build.VERSION.SDK_INT >= 26) {
			var suggestedPath = Environment.getExternalStorageDirectory().getPath() + "/EDOPro";
			Log.d("EDOPro", suggestedPath);
			i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(suggestedPath));
		}
		i.addCategory(Intent.CATEGORY_DEFAULT);
		i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		i.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
		i.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);  // not yet used properly
		i.putExtra(Intent.EXTRA_LOCAL_ONLY, true);  // Only allow local folders.
		startActivityForResult(Intent.createChooser(i, "Choose directory"), CHOOSE_WORKING_DIR);
	}

	public void setWorkingDir(String dest_dir, String data_dir) {
		{
			working_directory = dest_dir;
			var file = new File(getFilesDir(), "working_dir");
			try {
				var fOut = new FileOutputStream(file);
				new OutputStreamWriter(fOut).append(dest_dir).close();
				fOut.close();
			} catch (Exception e) {
				Log.e("EDOPro", "cannot write to working directory file: " + e.getMessage());
				finish();
				return;
			}
		}
		copyAssetsPrompt(dest_dir);
	}

	public void setWorkingDir(String dest_dir) {
		setWorkingDir(dest_dir, null);
	}

	public void copyAssetsPrompt(final String working_dir) {
		changelog = false;
		var file = new File(getFilesDir(), "assets_copied");
		if (file.exists()) {
			try {
				var fileReader = new BufferedReader(new FileReader(file));
				var line = fileReader.readLine();
				int prevversion = Integer.parseInt(line);
				if (prevversion < BuildConfig.VERSION_CODE) {
					try {
						//creates the empty assets_copied file
						new PrintWriter(file).close();
						Toast.makeText(this, getResources().getString(R.string.copying_update), Toast.LENGTH_LONG).show();
						copyAssets(working_dir, true);
					} catch (Exception ignored) {
					}
				} else
					next();
				return;
			} catch (Exception ignored) {
			}
		}
		new AlertDialog.Builder(this)
				.setMessage(R.string.assets_prompt)
				.setNegativeButton("No", (dialog, id) -> {
					try {
						var file1 = new File(getFilesDir(), "assets_copied");
						if (!file1.createNewFile()) {
							Log.e("EDOPro", "error when creating assets_copied file");
						} else {
							var wr = (new FileWriter(file1));
							wr.write("" + BuildConfig.VERSION_CODE);
							wr.flush();
						}
					} catch (Exception e) {
						Log.e("EDOPro", "error when creating assets_copied file: " + e.getMessage());
					}
					next();
				})
				.setPositiveButton("Yes", (dialog, id) -> copyAssets(working_dir, false))
				.setCancelable(false)
				.show();
	}

	public void copyAssets(String working_dir, boolean isUpdate) {
		changelog = true;
		Intent intent = new Intent(this, AssetCopy.class);
		var params = new Bundle();
		params.putString("workingDir", working_dir);
		params.putBoolean("isUpdate", isUpdate);
		intent.putExtras(params);
		startActivityForResult(intent, COPY_ASSETS);
	}
}
