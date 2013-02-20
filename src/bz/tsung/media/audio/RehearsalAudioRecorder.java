package bz.tsung.media.audio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class RehearsalAudioRecorder {
	private final static String TAG = "RehearsalAudioRecorder";

	/**
	 * INITIALIZING : recorder is initializing; READY : recorder has been
	 * initialized, recorder not yet started RECORDING : recording ERROR :
	 * reconstruction needed STOPPED: reset needed
	 */
	public enum State {
		INITIALIZING, READY, RECORDING, ERROR, STOPPED
	};

	public static final boolean RECORDING_UNCOMPRESSED = true;

	public static final boolean RECORDING_COMPRESSED = false;

	// The interval in which the recorded samples are output to the file
	// Used only in uncompressed mode
	private static final int TIMER_INTERVAL = 20;

	// private static final int TIMER_INTERVAL = 50;

	// Toggles uncompressed recording on/off; RECORDING_UNCOMPRESSED /
	// RECORDING_COMPRESSED
	private boolean rUncompressed;

	// Recorder used for uncompressed recording
	private AudioRecord mAudioRecord = null;

	// Recorder used for compressed recording
	private MediaRecorder mRecorder = null;

	// Stores current amplitude (only in uncompressed mode)
	private int cAmplitude = 0;

	// Output file path
	private String fPath = null;

	// Recorder state; see State
	private State state;

	// File writer (only in uncompressed mode)
	private RandomAccessFile fWriter;

	// Number of channels, sample rate, sample size(size in bits), buffer size,
	// audio source, sample size(see AudioFormat)
	private short nChannels;

	private int sRate;

	private short bSamples;

	private int mBufferSize;

	// private int mAudioSource;
	//
	// private int mAudioFormat;

	// Number of frames written to file on each output(only in uncompressed
	// mode)
	private int framePeriod;

	// Buffer for output(only in uncompressed mode)
	private byte[] buffer;

	// Number of bytes written to file after header(only in uncompressed mode)
	// after stop() is called, this size is written to the header/data chunk in
	// the wave file
	private int payloadSize;

	/*
	 * Method used for recording.
	 */
	private final AudioRecord.OnRecordPositionUpdateListener updateListener = new AudioRecord.OnRecordPositionUpdateListener() {
		@Override
		public void onMarkerReached(AudioRecord recorder) {
			// NOT USED
		}

		// private int i = 0;

		@Override
		public void onPeriodicNotification(AudioRecord recorder) {
			Log.i(TAG, "onPeriodicNotification");
			// 读缓冲区

			// if (i++ > 100) {
			// stop();
			// Log.i(TAG, "stop automaull");
			// return;
			// }
			int count = mAudioRecord.read(buffer, 0, buffer.length); // Fill
																		// buffer
			Log.i(TAG, "on buffer: " + count + " orig: " + buffer.length);

			mOnRecordListener.onRecordBuffer(buffer);
			try {
				// 将缓冲区数据写入文件
				fWriter.write(buffer); // Write buffer to file
				payloadSize += buffer.length;
				// 计算振幅

				if (bSamples == 16) {
					// int i = buffer.length / 4;
					// cAmplitude = getShort(buffer[i * 2],
					// buffer[i * 2 + 1]);
					for (int i = 0; i < buffer.length / 2; i++) { // 16bit
						// sample
						// size
						final short curSample = getShort(buffer[i * 2],
								buffer[i * 2 + 1]);
						if (curSample > cAmplitude) { // Check amplitude
							cAmplitude = curSample;
						}
					}
				} else { // 8bit sample size
					for (int i = 0; i < buffer.length; i++) {
						if (buffer[i] > cAmplitude) { // Check amplitude
							cAmplitude = buffer[i];
						}
					}
					// cAmplitude = buffer[buffer.length / 2];
				}
				Log.i(TAG, "onPeriodicNotification end");
			} catch (final IOException e) {
				Log.e(TAG,
						"Error occured in updateListener, recording is aborted");
				stop();
			}
		}
	};

	/**
	 * Default constructor Instantiates a new recorder, in case of compressed
	 * recording the parameters can be left as 0. In case of errors, no
	 * exception is thrown, but the state is set to ERROR
	 * 
	 * @param uncompressed
	 *            whether compress.if true the record would be compressed,else
	 *            not.
	 * @param audioSource
	 *            Sets the audio source to be used for recording.It could be
	 *            AudioSource.MIC etc.
	 * @param sampleRate
	 *            sample rate expressed in Hertz. Examples of rates are (but not
	 *            limited to) 44100, 22050 and 11025. IMPORTANT, here should set
	 *            8000HZ to get normal audio record
	 * @param channelConfig
	 *            describes the configuration of the audio channels. See
	 *            CHANNEL_IN_MONO and CHANNEL_IN_STEREO
	 * @param audioFormat
	 *            the format in which the audio data is represented. See
	 *            ENCODING_PCM_16BIT and ENCODING_PCM_8BIT
	 */
	public RehearsalAudioRecorder(boolean uncompressed, int audioSource,
			int sampleRate, int channelConfig, int audioFormat) {
		try {
			rUncompressed = uncompressed;
			if (rUncompressed) { // RECORDING_UNCOMPRESSED
				if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
					bSamples = 16;
				} else {
					bSamples = 8;
				}
				if (channelConfig == AudioFormat.CHANNEL_CONFIGURATION_MONO) {
					nChannels = 2;
				} else {
					nChannels = 1;
				}

				// mAudioSource = audioSource;
				sRate = sampleRate;
				// mAudioFormat = audioFormat;

				// TIMER_INTERVAL时间内的采样次数,（帧大小）
				framePeriod = sampleRate * TIMER_INTERVAL / 1000;
				// 存储空间大小，2倍于最佳值
				mBufferSize = framePeriod * 2 * bSamples * nChannels / 8;
				int minBufferSize = AudioRecord.getMinBufferSize(sampleRate,
						channelConfig, audioFormat);
				if (mBufferSize < minBufferSize) { // Check to make sure
													// buffer size is not
													// smaller than the
													// smallest allowed one
					mBufferSize = AudioRecord.getMinBufferSize(sampleRate,
							channelConfig, audioFormat);
					// Set frame period and timer interval accordingly
					framePeriod = mBufferSize / (2 * bSamples * nChannels / 8);
					Log.w(TAG,
							"Increasing buffer size to "
									+ Integer.toString(mBufferSize));
				}

				mAudioRecord = new AudioRecord(audioSource, sampleRate,
						channelConfig, audioFormat, mBufferSize);

				if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
					throw new IllegalArgumentException(
							"AudioRecord initialization failed");
				}

				mAudioRecord.setPositionNotificationPeriod(framePeriod);
				//
				mAudioRecord.setRecordPositionUpdateListener(updateListener);

			} else { // RECORDING_COMPRESSED
				mRecorder = new MediaRecorder();
				mRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
				mRecorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
				mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			}

			cAmplitude = 0;
			fPath = null;
			state = State.INITIALIZING;
		} catch (final IllegalStateException e) {
			if (e.getMessage() != null) {
				Log.e(TAG, e.getMessage());
			} else {
				Log.e(TAG, "Unknown error occured while initializing recording");
			}
			state = State.ERROR;
		}
	}

	/**
	 * Returns the largest amplitude sampled since the last call to this method.
	 * 
	 * @return returns the largest amplitude since the last call, or 0 when not
	 *         in recording state.
	 */
	public int getMaxAmplitude() {
		if (state == State.RECORDING) {
			if (rUncompressed) {
				final int result = cAmplitude;
				cAmplitude = 0;
				return result;
			} else {
				try {
					return mRecorder.getMaxAmplitude();
				} catch (final IllegalStateException e) {
					return 0;
				}
			}
		} else
			return 0;
	}

	/*
	 * Converts a byte[2] to a short, in LITTLE_ENDIAN format
	 */
	private short getShort(byte argB1, byte argB2) {
		return (short) (argB1 | (argB2 << 8));
	}

	/**
	 * Returns the state of the recorder in a RehearsalAudioRecord.State typed
	 * object. Useful, as no exceptions are thrown.
	 * 
	 * @return recorder state
	 */
	public State getState() {
		return state;
	}

	/**
	 * Prepares the recorder for recording, in case the recorder is not in the
	 * INITIALIZING state and the file path was not set the recorder is set to
	 * the ERROR state, which makes a reconstruction necessary. In case
	 * uncompressed recording is toggled, the header of the wave file is
	 * written. In case of an exception, the state is changed to ERROR
	 */
	public void prepare() throws IOException {
		try {
			if (state == State.INITIALIZING) {
				if (rUncompressed) {
					if ((mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED)
							& (fPath != null)) {
						// write file header

						fWriter = new RandomAccessFile(fPath, "rw");

						fWriter.setLength(0); // Set file length to 0, to
												// prevent unexpected behavior
												// in case the file already
												// existed
						fWriter.writeBytes("RIFF");
						fWriter.writeInt(0); // Final file size not known yet,
												// write 0
						fWriter.writeBytes("WAVE");
						fWriter.writeBytes("fmt ");
						fWriter.writeInt(Integer.reverseBytes(16)); // Sub-chunk
																	// size, 16
																	// for PCM
						fWriter.writeShort(Short.reverseBytes((short) 1)); // AudioFormat,
																			// 1
																			// for
																			// PCM
						fWriter.writeShort(Short.reverseBytes(nChannels));// Number
																			// of
																			// channels,
																			// 1
																			// for
																			// mono,
																			// 2
																			// for
																			// stereo
						fWriter.writeInt(Integer.reverseBytes(sRate)); // Sample
																		// rate
						fWriter.writeInt(Integer.reverseBytes(sRate * bSamples
								* nChannels / 8)); // Byte rate,
													// SampleRate*NumberOfChannels*BitsPerSample/8
						fWriter.writeShort(Short
								.reverseBytes((short) (nChannels * bSamples / 8))); // Block
																					// align,
																					// NumberOfChannels*BitsPerSample/8
						fWriter.writeShort(Short.reverseBytes(bSamples)); // Bits
																			// per
																			// sample
						fWriter.writeBytes("data");
						fWriter.writeInt(0); // Data chunk size not known yet,
												// write 0

						buffer = new byte[framePeriod * bSamples / 8
								* nChannels];
						state = State.READY;
					} else {
						Log.e(TAG,
								"prepare() method called on uninitialized recorder");
						state = State.ERROR;
					}
				} else {
					mRecorder.prepare();
					state = State.READY;
				}
			} else {
				Log.e(TAG, "prepare() method called on illegal state");
				release();
				state = State.ERROR;
			}
		} catch (IOException ioe) {
			if (ioe.getMessage() != null) {
				Log.e(TAG, ioe.getMessage());
			} else {
				Log.e(TAG, "Unknown error occured in prepare()");
			}
			state = State.ERROR;
			throw ioe;
		}
		mOnRecordListener.onRecordPrepared();
	}

	/**
	 * Releases the resources associated with this class, and removes the
	 * unnecessary files, when necessary
	 */
	public void release() {
		if (state == State.RECORDING) {
			stop();
		} else {
			if ((state == State.READY) & (rUncompressed)) {
				try {
					fWriter.close(); // Remove prepared file
				} catch (final IOException e) {
					Log.e(TAG,
							"I/O exception occured while closing output file");
				}
				(new File(fPath)).delete();
			}
		}

		if (rUncompressed) {
			if (mAudioRecord != null) {
				mAudioRecord.release();
			}
		} else {
			if (mRecorder != null) {
				mRecorder.release();
			}
		}
	}

	/**
	 * Resets the recorder to the INITIALIZING state, as if it was just created.
	 * In case the class was in RECORDING state, the recording is stopped. In
	 * case of exceptions the class is set to the ERROR state.
	 */
	public void reset() {
		state = State.INITIALIZING;
		try {
			if (state != State.ERROR) {
				// release();
				fPath = null; // Reset file path
				cAmplitude = 0; // Reset amplitude
				if (rUncompressed) {
					// mAudioRecord = new AudioRecord(mAudioSource, sRate,
					// nChannels + 1,
					// mAudioFormat, mBufferSize);
					//
					// if (mAudioRecord.getState() !=
					// AudioRecord.STATE_INITIALIZED) {
					// throw new
					// IllegalArgumentException("AudioRecord initialization failed");
					// }
					// mAudioRecord.setPositionNotificationPeriod(framePeriod);
					// //
					// mAudioRecord.setRecordPositionUpdateListener(updateListener);
				} else {
					// mRecorder = new MediaRecorder();
					// mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
					// mRecorder
					// .setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
					// mRecorder
					// .setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
				}
				state = State.INITIALIZING;
			}
		} catch (final IllegalStateException e) {
			Log.e(TAG, e.getMessage());
			state = State.ERROR;
		}
	}

	/**
	 * Sets output file path, call directly after construction/reset.
	 * 
	 * @param output
	 *            file path
	 */
	public void setOutputFile(String argPath) {
		try {
			if (state == State.INITIALIZING) {
				fPath = argPath;
				if (!rUncompressed) {
					mRecorder.setOutputFile(fPath);
				}
			}
		} catch (final IllegalStateException e) {
			if (e.getMessage() != null) {
				Log.e(TAG, e.getMessage());
			} else {
				Log.e(TAG, "Unknown error occured while setting output path");
			}
			state = State.ERROR;
		}
	}

	/**
	 * Starts the recording, and sets the state to RECORDING. Call after
	 * prepare().
	 */
	public void start() {
		mOnRecordListener.onRecordStart();
		if (state == State.READY) {
			if (rUncompressed) {
				payloadSize = 0;
				Log.i(TAG, "before recording");
				try {
					mAudioRecord.startRecording();
				} catch (Exception e) {
					Log.e(TAG,
							"recording start error: "
									+ (e == null ? "" : e.getMessage()));
				}
				Log.i(TAG, "after recording");
				mAudioRecord.read(buffer, 0, buffer.length);
			} else {
				mRecorder.start();
			}
			state = State.RECORDING;
		} else {
			Log.e(TAG, "start() called on illegal state");
			state = State.ERROR;
		}
	}

	/**
	 * Stops the recording, and sets the state to STOPPED. In case of further
	 * usage, a reset is needed. Also finalizes the wave file in case of
	 * uncompressed recording.
	 */
	public void stop() {
		mOnRecordListener.onRecordStop();
		if (state == State.RECORDING) {
			if (rUncompressed) {
				Log.d(TAG, "stop legal");
				mAudioRecord.stop();
				Log.d(TAG, "stop legal after");
				// mAudioRecord.release();

				try {
					fWriter.seek(4); // Write size to RIFF header
					fWriter.writeInt(Integer.reverseBytes(36 + payloadSize));

					fWriter.seek(40); // Write size to Subchunk2Size field
					fWriter.writeInt(Integer.reverseBytes(payloadSize));

					fWriter.close();
				} catch (final IOException e) {
					Log.e(TAG,
							"I/O exception occured while closing output file");
					state = State.ERROR;
				}
			} else {
				mRecorder.stop();
			}
			state = State.STOPPED;
		} else {
			Log.e(TAG, "stop() called on illegal state" + state);
			state = State.ERROR;
		}
		reset();
	}

	/**
	 * interface for wave callback
	 * @date Nov 22, 2012
	 * @author Tsung Wu <tsung.bz@gmail.com>
	 *
	 */
	public interface IRehearsalAudioRecorderListener {
		
		void onRecordPrepared();
		
		void onRecordStart();
		
		void onRecordStop();

		void onRecordBuffer(byte[] buffer);

	}
	
	private IRehearsalAudioRecorderListener mOnRecordListenerOut;

	public void setOnRecordListener(IRehearsalAudioRecorderListener l) {
		mOnRecordListenerOut = l;
	}

	private IRehearsalAudioRecorderListener mOnRecordListener = new IRehearsalAudioRecorderListener() {

		@Override
		public void onRecordPrepared() {
			if(mOnRecordListenerOut != null) {
				mOnRecordListenerOut.onRecordPrepared();
			}
		}

		@Override
		public void onRecordStart() {
			if(mOnRecordListenerOut != null) {
				mOnRecordListenerOut.onRecordStart();
			}
		}

		@Override
		public void onRecordStop() {
			if(mOnRecordListenerOut != null) {
				mOnRecordListenerOut.onRecordStop();
			}
		}

		@Override
		public void onRecordBuffer(byte[] buffer) {
			if(mOnRecordListenerOut != null) {
				mOnRecordListenerOut.onRecordBuffer(buffer);
			}
		}};
}
