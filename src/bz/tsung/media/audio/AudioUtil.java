
package bz.tsung.media.audio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder.AudioSource;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import bz.tsung.media.audio.RehearsalAudioRecorder.IRehearsalAudioRecorderListener;

/**
 * 录音、播音的操作都是异步的，不用另起线程来执行
 * 
 * @author mk
 */
public class AudioUtil{
    // 录音的时侯，声音的最大幅度
    public final static int MAX_SAMPLING_VOLUME = 0x7FFF;

    private static final String TAG = "AudioUtil";
    
    private String mFilePath;
    
    private final String baseAudioPath = Environment.getExternalStorageDirectory().getPath()
            + "/.RealtimeAudioRecorder/";

    private Context mContext;

    private MediaPlayer mPlayer;

    private MediaPlayer mPlayerEnd;

    private static MediaPlayer mDurationPlayer = new MediaPlayer();

    private RehearsalAudioRecorder mRecorder;

    private ArrayList<OnCompletionListener> mOnCompletionListeners = new ArrayList<OnCompletionListener>();

    private ArrayList<OnStopListener> mOnStopListeners = new ArrayList<OnStopListener>();

    private boolean isRecording = false;

    private long mRecordTimeStamp = 0;

    public final static int STOP_REASON_RECORDING = 0;

    public final static int STOP_REASON_OTHER = 1;

    // 正在播放的文件名，多播放控制用
    public String playingFile = "";

    private Looper mCurrentLooper;

    class LooperThread extends Thread {
        public Handler mHandler;

        public void run() {
            Looper.prepare();

            // Log.i(TAG, "is null? " + ((Looper.myLooper() == null) ? "yes" :
            // "no"));
            // Log.i(TAG, "is null? " + ((Looper.myLooper() ==
            // Looper.getMainLooper()) ? "yes" : "no"));
            mCurrentLooper = Looper.myLooper();
            mRecorder = new RehearsalAudioRecorder(true, AudioSource.DEFAULT, 8000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

            mHandler = new Handler() {
                public void handleMessage(Message msg) {
                    // process incoming messages here
                }
            };

            Looper.loop();
        }
    }

    public AudioUtil(Context context) {
        mContext = context;
        mPlayer = new MediaPlayer();
        new LooperThread().start();
    }

    /**
     * 播放结束时调用此函数
     * 
     * @param l
     */
    public void setOnCompletionListener(OnCompletionListener l) {
        mOnCompletionListeners.add(l);
    }

    public void setOnStopListener(OnStopListener l) {
        mOnStopListeners.add(l);
    }

    public static long getAudioDuration(String fileName) throws IOException {
        long duration = 0;
        if (mDurationPlayer == null) {
            return duration;
        }
        try {
            mDurationPlayer.reset();
            mDurationPlayer.setDataSource(fileName);
            mDurationPlayer.prepare();
            duration = mDurationPlayer.getDuration();
            mDurationPlayer.stop();
        } catch (IOException e) {
            Log.e(TAG, "IOException:" + e.getMessage());
            throw e;
        } catch (IllegalStateException e) {
            Log.e(TAG, "getAudioDuration start playing IllegalStateException");
            throw e;
        }
        return duration;
    }

    /**
     * 开始录音
     */
    public void startRecord() throws IllegalStateException, IOException {
        if (mPlayer.isPlaying()) {
            stopPlaying(AudioUtil.STOP_REASON_RECORDING);
        }

        if (isRecording) { // 先停止录音
            stopRecording();
        }

        startRecording();
    }

    /**
     * 录音结束
     * 
     * @return 录音文件的绝对路径。如果录音失败返回null
     */
    public String stopRecord() throws IllegalStateException {
        Log.i(TAG, "stopRecord");
        if (isRecording) {
            stopRecording();
            long interval = Calendar.getInstance().getTimeInMillis() - mRecordTimeStamp;
            if (interval < 1100) {
                // mContext.deleteFile(mFilePath);
                // return null;
            }
            return mFilePath;
        } else {
            return null;
        }

    }

    /**
     * 是否正在录音
     * 
     * @return true-正在录音；false-录音结束
     */
    public synchronized boolean isRecording() {
        return isRecording;
    }

    /**
     * 开始播放录音
     * 
     * @param path 录音文件的路径
     * @return START_SUCCESS:播放成功；（TODO：文件不存在，编码不支持等）
     * @throws IOException
     * @throws IllegalStateException
     */
    public void startPlay(final String fileName) throws IllegalStateException, IOException {
        if (fileName == null) {
            Log.e(TAG, "file name is null");
            return;
        }
        playingFile = fileName;
        if (isRecording) { // 停止录音
            stopRecording();
        }

        if (mPlayer == null) {
            return;
        }
        if (mPlayer.isPlaying()) { // 先停止当然的播放
            stopPlaying();
        }

        startPlaying(fileName);
    }

    /**
     * 手动停止播音（正常情况下会自己结束）
     */
    public void stopPlay() throws IllegalStateException {
        if (mPlayer != null && mPlayer.isPlaying()) {
            stopPlaying();
        }
    }

    /**
     * 是否正在播放录音
     * 
     * @return true-正在播放录音
     */
    public synchronized boolean isPlaying() {
        return mPlayer != null && mPlayer.isPlaying();
    }

    /**
     * 释放录音，播音的资源。（可以在退出单个私聊界面的时侯释放，不必每次录音结束都调用。 释放完之后，这个实例将不可再用）
     */
    public void release() {
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }

        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }

        if (mCurrentLooper != null) {
            mCurrentLooper.quit();
            mCurrentLooper = null;
        }
    }

    // public void resetRecorder() {
    // if (mRecorder != null) {
    // mRecorder.release();
    // mRecorder = null;
    // }
    //
    // new LooperThread().start();
    // }

    private void startPlaying(final String fileName)
            throws IllegalStateException, IOException {
        if (mPlayer == null) {
            return;
        }
        try {
            mPlayer.reset();
            mPlayer.setDataSource(fileName);
            mPlayer.prepare();
            mPlayer.start();
            Log.i(TAG, "start play");
            OnCompletionListener mOnCompletionListener = new OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer arg0) {
                    if (mPlayerEnd != null) {
                        mPlayerEnd.release();
                    }
                    mPlayerEnd = MediaPlayer.create(mContext, R.raw.play_end);
                    mPlayerEnd.start();
                    for (Iterator<OnCompletionListener> itr = mOnCompletionListeners
                            .iterator(); itr
                            .hasNext();) {
                        OnCompletionListener curr = itr.next();
                        curr.onCompletion(arg0);
                    }
                }
            };
            mPlayer.setOnCompletionListener(mOnCompletionListener);
        } catch (IOException e) {
            Log.e(TAG, "IOException");
            throw e;
        } catch (IllegalStateException e) {
            Log.e(TAG, "start playing IllegalStateException");
            throw e;
        }
    }

    private void stopPlaying() throws IllegalStateException {
        stopPlaying(AudioUtil.STOP_REASON_OTHER);
    }

    private void stopPlaying(int reason) throws IllegalStateException {
        if (mPlayer != null) {
            try {
                Log.i(TAG, "_stop play");
                mPlayer.stop();
                for (Iterator<OnStopListener> itr = mOnStopListeners.iterator(); itr
                        .hasNext();) {
                    OnStopListener curr = itr.next();
                    curr.onStop(reason);
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "stop playing IllegalStateException");
                throw e;
            }
        }
    }

    private void startRecording() throws IllegalStateException, IOException {
        if (mRecorder != null) {
            // 获取录音文件存放的路径
            Calendar calendar = Calendar.getInstance();
            long timestamp = calendar.getTimeInMillis();
            String fileName = String.valueOf(timestamp);
            File destDir = new File(baseAudioPath);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            mFilePath = baseAudioPath + fileName;
            Log.i(TAG, "file path:" + mFilePath);
            try { // 准备并开始录音
                  // mRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
                  // mRecorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
                  // mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mRecorder.setOutputFile(mFilePath);
                mRecorder.prepare();
                mRecorder.start();
                mRecordTimeStamp = Calendar.getInstance().getTimeInMillis();
                // 监听录音时的声音振幅
                Log.i(TAG, "_start record");
            } catch (IOException e) {
                Log.e(TAG, "IOException");
                throw e;
            } catch (IllegalStateException e) {
                Log.e(TAG, "start recording IllegalStateException");
                throw e;
            }

            isRecording = true;
        }
    }

    private void stopRecording() throws IllegalStateException {
        if (mRecorder != null) {
            try {
                Log.i(TAG, "_stop record");
                isRecording = false;
                mRecorder.stop();
                // mRecorder.reset();
            } catch (IllegalStateException e) {
                Log.e(TAG, "stop recording IllegalStateException");
                throw e;
            } catch (Exception e) {
                Log.e(TAG, "exception:" + e.getMessage());
            }
        }
        // if (mCurrentLooper != null) {
        // mCurrentLooper.quit();
        // mCurrentLooper = null;
        // }
    }

    public int getMaxAmplitude() {
        if (isRecording && mRecorder != null) {
            return mRecorder.getMaxAmplitude();
        } else {
            return 0;
        }
    }

    public interface OnStopListener {
        void onStop(int reason);
    }

    public interface RecordAmplitudeListener {
        void onMessage(int amplitude);
    }

    public void setOnRecordListener(IRehearsalAudioRecorderListener l) {
        if (mRecorder != null) {
            mRecorder.setOnRecordListener(l);
        }
    }
}
