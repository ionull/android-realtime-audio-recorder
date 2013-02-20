package bz.tsung.media.audio.converters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.media.AmrInputStream;
import android.util.Log;

public class AmrWaveConverter implements WaveConverter {
	private static final String TAG = "AmrWaveConverter";
	private AmrInputStream ais;

	@Override
	public void init(InputStream input) {
		ais = new AmrInputStream(input);
	}

	@Override
	public void end() {
		if (ais != null) {
			try {
				ais.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void convert(WaveConvertComplete complete) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte[] tmp = new byte[32];
		try {
			Log.i(TAG, "trying to convert amr");
			int len = 0;
			while ((len = ais.read(tmp)) > 0) {
				Log.i(TAG, "ais length: " + len);
				os.write(tmp, 0, len);
				Log.i(TAG, "os length: " + os.size());
				if (os.size() >= 800) {
					byte[] amr = os.toByteArray();
					Log.i(TAG, "amr buffer size: " + amr.length);
					os.reset();
					complete.done(amr);
				}
			}
			byte[] amr = os.toByteArray();
			Log.i(TAG, "amr buffer size ending....: " + amr.length);
			os.close();
			complete.done(amr);
		} catch (Exception e) {
			Log.e(TAG, "converting" + e.getMessage());
			e.printStackTrace();
			if (os.size() > 0) {
				byte[] amr = os.toByteArray();
				Log.i(TAG, "amr buffer size ending....broken: "
						+ amr.length);
				complete.done(amr);
			}
			try {
				os.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

}
