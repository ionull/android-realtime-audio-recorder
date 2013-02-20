package bz.tsung.media.audio.converters;

import java.io.InputStream;

public interface WaveConverter {
	void init(InputStream input);

	void end();

	void convert(WaveConvertComplete complete);

	public interface WaveConvertComplete {
		void done(byte[] buffer);
	}

}
