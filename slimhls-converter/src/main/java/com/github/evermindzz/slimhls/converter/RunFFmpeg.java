package com.github.evermindzz.slimhls.converter;

import android.content.Context;

/**
 * Run the ffmpeg binary"
 */
public class RunFFmpeg extends RunBinary {
    public static String getFfmpegPath(Context context) {
        return context.getApplicationInfo().nativeLibraryDir + "/libffmpeg.so";
    }

    /**
     * @param context the context
     */
    public RunFFmpeg(Context context) {
        super(getFfmpegPath(context), "ffmpeg");
    }
}
