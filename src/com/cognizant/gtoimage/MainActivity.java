package com.cognizant.gtoimage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.google.android.glass.app.Card;
import com.google.android.glass.media.CameraManager;

public class MainActivity extends Activity {

	private static final String TAG = "GTOActivity";
	private static final int RUN_CAMERA = 0;
	private boolean mBound = false;
	private boolean imagecreated = false;
	private RelativeLayout cardParent;
	private ImageView mImageView;
	private String rememberItem;
	private ProgressBar mProgress;
	FileObserver observer;
	private String finalPhotoPath;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		//getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		cardParent = (RelativeLayout) findViewById(R.id.reminder_card_holder);
		mImageView = (ImageView) findViewById(R.id.image_to_remember);
		mProgress = (ProgressBar) findViewById(R.id.someProgressBar);
		LocalBroadcastManager.getInstance(this).registerReceiver(
				mMessageReceiver, new IntentFilter("custom-event-name"));
		// ArrayList<String> voiceResults = getIntent().getExtras()
		// .getStringArrayList(RecognizerIntent.EXTRA_RESULTS);
		// rememberItem = voiceResults.get(0);

		fireReminderPicture();
	}

	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// Get extra data included in the Intent
			rememberItem = intent.getStringExtra("message");
			setCard();
			Log.d("receiver", "Got message: " + rememberItem);
		}
	};

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == RESULT_OK) {

			if (requestCode == RUN_CAMERA) {
				Log.d(TAG, "Running oResult");
				imagecreated = true;
				mProgress.setVisibility(View.VISIBLE);
				String filePath = data
						.getStringExtra(CameraManager.EXTRA_PICTURE_FILE_PATH);
				finalPhotoPath = filePath;
				Log.d(TAG, "Le file path: " + filePath);
				filePath = filePath.substring(0, filePath.lastIndexOf("/"));
				Log.d(TAG, "Parent file: " + filePath);

				final File leFile = new File(filePath);
				if (leFile.exists()) {
					Log.d(TAG, "New file exists!");
				} else {
					Log.d(TAG, "New file does not exist");
				}
				observer = new FileObserver(filePath) {
					private Bitmap bitmap;

					// set up a file observer to watch this directory on sd card
					@Override
					public void onEvent(int event, String file) {
						try {
							if (file != null) {
								File forBitmap = new File(finalPhotoPath);

								if (forBitmap.exists()) {
									if (imagecreated && forBitmap.length() > 0) {
										postImage(forBitmap);
										imagecreated = false;
									}

									BitmapFactory.Options op = new BitmapFactory.Options();
									op.inSampleSize = 2;
									bitmap = BitmapFactory.decodeFile(
											forBitmap.getAbsolutePath(), op);

									if (bitmap != null) {
										mImageView.post(new Runnable() {
											@Override
											public void run() {
												mImageView
														.setScaleType(ImageView.ScaleType.FIT_XY);
												mImageView
														.setImageBitmap(bitmap);
											}
										});
										this.stopWatching();
									}
								}
							}
						} catch (Exception e) {
							Log.d(TAG, "Exception", e);
						}
					}
				};
				observer.startWatching(); // START OBSERVING
			}

		}// end of check for RESULT_OK
		else {
			Log.d(TAG, "Oh no! " + resultCode);
		}

	}// end of onActivityResult

	private void postImage(File file)  {
		Log.i(TAG, "upload" + file.exists() + " " + file.length()+ " " +file.getAbsolutePath());
		try {
			// Set your file path here
			FileInputStream fstrm = new FileInputStream(file.getAbsolutePath());

			HttpFileUpload hfu = new HttpFileUpload(
					"http://tmark.cloudapp.net/fr/api/facereg", "title",
					"description",this);
			hfu.Send_Now(fstrm);
		} catch (FileNotFoundException e) {
			// Error: File not found
			Log.i(TAG, "upload err" + e.getMessage());
		}

	}

	public static void trimCache(Context context) {
		try {
			File dir = context.getCacheDir();
			if (dir != null && dir.isDirectory()) {
				deleteDir(dir);
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	public static boolean deleteDir(File dir) {
		if (dir != null && dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}

		// The directory is now empty so delete it
		return dir.delete();
	}

	private void setCard() {
		final Card card = new Card(MainActivity.this);
		card.setText(rememberItem);
		// card.addImage(uri);
		cardParent.post(new Runnable() {
			@Override
			public void run() {
				cardParent.addView(card.toView());
			}
		});

		mProgress.setVisibility(View.GONE);
	}

	private void fireReminderPicture() {
		Intent cameraIntent = new Intent(
				android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
		startActivityForResult(cameraIntent, RUN_CAMERA);

	}// end of fireReminderPicture

	@Override
	protected void onDestroy() {
		super.onDestroy();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(
				mMessageReceiver);

		Log.d("MainActivity", "onDestory is running.");
		try {
			trimCache(this);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (observer != null)
			observer.stopWatching();
		// liveCard.unpublish();
		// unbindService(mConnection);

	}

	/**
	 * Callback for the service
	 */
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mBound = false;
		}

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// cast the Binder to obtain the service.
			mBound = true;
		}
	};

}// end of class