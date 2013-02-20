package bz.tsung.media.audio;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import bz.tsung.media.audio.RehearsalAudioRecorder.IRehearsalAudioRecorderListener;
import bz.tsung.media.audio.converters.UnknownWaveConverter;
import bz.tsung.media.audio.converters.WaveConverter;
import bz.tsung.media.audio.converters.WaveConverter.WaveConvertComplete;
import bz.tsung.utils.android.DeviceUtil;

/**
 * 录音按钮控件
 * 
 * @date Dec 2, 2011
 * @author Tsung Wu <tsung.bz@gmail.com>
 */
public class AudioRecorder extends Button implements OnTouchListener, IAudioRecorder {
	private static final String TAG = "AudioRecorder";

	private Context mContext;
	private MediaPlayer mPlayerHintStart;
	private MediaPlayer mPlayerHintEnd;

	public AudioRecorder(Context context) {
		super(context);
		init(context);
	}

	public AudioRecorder(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public AudioRecorder(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	private void init(Context context) {
		mContext = context;
		this.setOnTouchListener(this);
		mPlayerHintStart = MediaPlayer.create(context, R.raw.record_start);
		mPlayerHintEnd = MediaPlayer.create(context, R.raw.record_end);
		createWaveConverter();
	}

	private AudioUtil audioUtil;
	protected WaveConverter waveConverter = new UnknownWaveConverter();

	private Date mBegin;

	private AlertDialog alertDialog;

	private ImageView recordingImageBG;

	private ImageView recordingImage;

	private TextView recordingText;

	private Timer recordingTimer;

	private Handler mHandler = new Handler();

	private boolean start = false;

	private Timer sixtySecondsTimer;
	
	public void createWaveConverter() {
		waveConverter = new UnknownWaveConverter();
	}

	private class VolumeTimerTask extends TimerTask {

		@Override
		public void run() {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					int max = audioUtil.getMaxAmplitude();

					if (max != 0) {
						FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) recordingImage
								.getLayoutParams();
						float scale = max / 7000.0f;
						if (scale < 0.3) {
							recordingImage
									.setImageResource(R.drawable.record_red);
						} else {
							recordingImage
									.setImageResource(R.drawable.record_green);
						}
						if (scale > 1) {
							scale = 1;
						}
						int height = recordingImageBG.getHeight()
								- (int) (scale * recordingImageBG.getHeight());
						params.setMargins(0, 0, 0, -1 * height);
						recordingImage.setLayoutParams(params);

						((View) recordingImage).scrollTo(0, height);
						// Log.i(TAG, "max amplitude: " + max);
						/**
						 * 倒计时提醒
						 */
						Date now = new Date();
						long between = (mBegin.getTime() + 60000)
								- now.getTime();
						if (between < 10000) {
							int second = (int) (Math.floor((between / 1000)));
							if (second == 0) {
								second = 1;
							}
							recordingText.setText("还剩: " + second + "秒");
						}
					}
				}
			});
		}

	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			if (DeviceUtil.isFullStorage()) {
				Toast.makeText(mContext, "您没有可用的SD卡，请退出U盘模式或者插入SD卡", 5000)
						.show();
				return false;
			}
			try {
				touchDown = true;

				if (mPlayerHintStart != null) {
					mPlayerHintStart.release();
				}
				mPlayerHintStart = MediaPlayer.create(mContext,
						R.raw.record_start);
				mPlayerHintStart.start();
				mPlayerHintStart
						.setOnCompletionListener(new OnCompletionListener() {

							@Override
							public void onCompletion(MediaPlayer arg0) {
								if (!touchDown) {
									return;
								}
								start = true;

								recordingTimer = new Timer();
								recordingTimer.schedule(new VolumeTimerTask(),
										0, 100);

								audioUtil
										.setOnRecordListener(mInsideRecordListener);
								try {
									Log.i(TAG, "start record");
									audioUtil.startRecord();
									mInsideRecordListener.onRecordStart();

									mBegin = new Date();

									sixtySecondsTimer = new Timer();
									sixtySecondsTimer.schedule(new TimerTask() {

										@Override
										public void run() {
											mHandler.post(new Runnable() {

												@Override
												public void run() {
													Log.i(TAG,
															"recording end by timeout");
													onRecordEnd();
												}
											});
										}
									}, 60000);
								} catch (Exception e) {
									Log.e(TAG,
											"Record start error:  " + e != null ? e
													.getMessage() : "");
									onRecordEnd();
								}
							}
						});

				/*
				 * show dialog
				 */
				AlertDialog.Builder builder;

				LayoutInflater inflater = (LayoutInflater) mContext
						.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
				View layout = inflater.inflate(
						R.layout.conversation_recording_dialog,
						(ViewGroup) findViewById(R.id.conversation_recording));

				recordingImage = (ImageView) layout
						.findViewById(R.id.conversation_recording_range);
				recordingImageBG = (ImageView) layout
						.findViewById(R.id.conversation_recording_white);

				recordingText = (TextView) layout
						.findViewById(R.id.conversation_recording_text);
				recordingText.setText("正在准备");

				builder = new AlertDialog.Builder(mContext);
				alertDialog = builder.create();
				alertDialog.show();
				alertDialog.getWindow().setContentView(layout);
			} catch (Exception e) {
				return onRecordEnd();
			}
		} else if (event.getAction() == MotionEvent.ACTION_UP) {
			Log.i(TAG, "recording end by action button up");
			return onRecordEnd();
		} else if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
			Log.i(TAG, "recording end by action button outside");
			return onRecordEnd();
		}
		return false;
	}

	private boolean touchDown = false;

	public boolean onRecordEnd() {
		touchDown = false;
		if (start) {
			if (mPlayerHintEnd != null) {
				mPlayerHintEnd.release();
			}
			mPlayerHintEnd = MediaPlayer.create(mContext, R.raw.record_end);
			mPlayerHintEnd.start();
		}
		boolean result = onRecordEnding();
		// audioUtil.resetRecorder();
		return result;
	}

	private boolean onRecordEnding() {
		/**
		 * TODO unfixed 30 seconds timeout by android button, (button action up
		 * called by android itself) and will make next recording crash bug,
		 * this bug now only found on Milstone2 and not procced. (no way to
		 * prevent system button action up, It's not an human action up)
		 */
		Log.i(TAG, "recording end occur");
		// stop sixty seconds limit
		if (sixtySecondsTimer != null) {
			sixtySecondsTimer.cancel();
			sixtySecondsTimer = null;
		}
		// stop volume task
		if (recordingTimer != null) {
			recordingTimer.cancel();
			recordingTimer = null;
		}

		String tfile = audioUtil.stopRecord();
		if (alertDialog != null) {
			alertDialog.dismiss();
		}
		if (mBegin == null) {
			if (start) {
				Toast.makeText(mContext, "录音时间太短或出错了,请将SD卡插好", 5000).show();
				// 删除录制的文件
				if (!(tfile == null || "".equals(tfile))) {
					File hey = new File(tfile);
					if (hey.exists()) {
						hey.delete();
					}
				}
				mInsideRecordListener.onStreamEnd();
			}
			start = false;
			return false;
		}
		start = false;
		Date now = new Date();
		if (now.getTime() - mBegin.getTime() < 1000) {
			mBegin = null;
			Toast.makeText(mContext, "录音时间太短了", 5000).show();
			(new File(tfile)).delete();
			// mInsideRecordListener.onRecordFail(null);
			audioRecorderListener.onRecordFail();
			mInsideRecordListener.onStreamEnd();
			return false;
		}
		if (tfile != null) {
			try {
				Date end = new Date();
				long fileTime = 0;
				try {
					fileTime = AudioUtil.getAudioDuration(tfile);
				} catch (IOException e) {
					e.printStackTrace();
				}
				long duration = fileTime == 0 ? end.getTime()
						- mBegin.getTime() : fileTime;
				// mInsideRecordListener.onRecordComplete(tfile, duration);
				audioRecorderListener.onRecordComplete(tfile, duration);
				mInsideRecordListener.onStreamEnd();
				mBegin = null;
			} catch (IllegalStateException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	class AudioRecorderInsideListener implements
			IRehearsalAudioRecorderListener {

		public void onStreamEnd() {
			if (waveConverter instanceof UnknownWaveConverter) {
				return;
			}
			try {
				if (output != null) {
					output.close();
				}
				if (waveConverter != null) {
					waveConverter.end();
				}

				if (input != null) {
					input.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void onConvertedBuffer(final byte[] buffer) {
			// Log.i(TAG, "onBuffer: " + buffer.length + " buffer 0: " +
			// buffer[0]);
			audioRecorderListener.onRecordConvertedBuffer(buffer);
			// TODO save to file
		}

		private PipedOutputStream output;

		private PipedInputStream input;

		class WaveConvertThread extends Thread {
			public void run() {
				Log.i(TAG, "writing buffer to ouput stream end.");
				if (waveConverter instanceof UnknownWaveConverter) {
					return;
				}
				if (waveConverter != null) {
					waveConverter.convert(new WaveConvertComplete() {

						@Override
						public void done(byte[] buffer) {
							onConvertedBuffer(buffer);
						}
					});
				}
			}
		}

		@Override
		public void onRecordBuffer(final byte[] buffer) {
			Log.i(TAG, "raw wave buffer size: " + buffer.length);
			if (waveConverter instanceof UnknownWaveConverter) {
				onConvertedBuffer(buffer);
			} else {
				try {
					output.write(buffer);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (mRecordListener != null) {
				mRecordListener.onRecordBuffer(buffer);
			}
		}

		@Override
		public void onRecordStart() {
			if (waveConverter instanceof UnknownWaveConverter) {
				//
			} else {
				try {
					output = new PipedOutputStream();
					input = new PipedInputStream(output);
					if (waveConverter != null) {
						waveConverter.init(input);
					}
					new WaveConvertThread().start();
				} catch (IOException e) {
					Log.e(TAG, e.getMessage());
					e.printStackTrace();
				}
			}
			if (mRecordListener != null) {
				mRecordListener.onRecordStart();
			}
		}

		// @Override
		public void onRecordFail() {
			// remove wave file and amr file?TODO
		}

		@Override
		public void onRecordPrepared() {
			mHandler.post(new Runnable() {

				@Override
				public void run() {
					recordingText.setText("正在录音");
				}
			});
		}

		@Override
		public void onRecordStop() {
			if (mRecordListener != null) {
				mRecordListener.onRecordStop();
			}
		}
	}

	private AudioRecorderInsideListener mInsideRecordListener = new AudioRecorderInsideListener();

	private IRehearsalAudioRecorderListener mRecordListener;

	public void setOnRecordListener(IRehearsalAudioRecorderListener l) {
		this.mRecordListener = l;
		if (audioUtil != null) {
			audioUtil.setOnRecordListener(mRecordListener);
		}
	}

	public void setAudioUtil(AudioUtil audioUtil) {
		this.audioUtil = audioUtil;
	}

	public AudioUtil getAudioUtil() {
		return audioUtil;
	}

	public interface IAudioRecorderListener {
		void onRecordFail();// time not enough

		void onRecordComplete(String file, long duration);

		void onRecordConvertedBuffer(byte[] buffer);
	}

	private IAudioRecorderListener audioRecorderListener;

	public IAudioRecorderListener getAudioRecorderListener() {
		return audioRecorderListener;
	}

	public void setAudioRecorderListener(
			IAudioRecorderListener audioRecorderListener) {
		this.audioRecorderListener = audioRecorderListener;
	}

	public void setWaveConverter(WaveConverter waveConverter) {
		this.waveConverter = waveConverter;
	}
}
