package com.stick.gsliu.addsoundtracktomp4.edit.sound;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC;
import static android.media.MediaFormat.KEY_MIME;
import static com.stick.gsliu.addsoundtracktomp4.edit.Util.TAG;

/**
 * Created by gsliu on 2018/3/6.
 * 从mp4中分离音视频数据，生成视频文件和音频文件
 */

public class SoundTrackUtil {


    public static void extractVideo(String mp4Path, String targetMp4Path) {
        MediaExtractor mediaExtractor = new MediaExtractor();
        MediaMuxer mediaMuxer = null;
        try {
            mediaExtractor.setDataSource(mp4Path);
            mediaMuxer = new MediaMuxer(targetMp4Path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int numTracks = mediaExtractor.getTrackCount();
            int videoTrackIndex = -1;
            for (int i = 0; i < numTracks; i++) {
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                String mineType = format.getString(MediaFormat.KEY_MIME);
                if (mineType.startsWith("video/")) {
                    videoTrackIndex = i;
                }
            }

            ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1024);
            mediaExtractor.selectTrack(videoTrackIndex);

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            mediaMuxer.addTrack(mediaExtractor.getTrackFormat(videoTrackIndex));
            mediaMuxer.start();
            while (true) {
                int readSampleCount = mediaExtractor.readSampleData(byteBuffer, 0);
                if (readSampleCount < 0) {
                    Log.i(TAG, "read data end ！！！");
                    break;
                }
                byte[] buffer = new byte[readSampleCount];
                byteBuffer.get(buffer);
                bufferInfo.presentationTimeUs = mediaExtractor.getSampleTime();
                Log.i(TAG, "pts : " + bufferInfo.presentationTimeUs);
                bufferInfo.size = readSampleCount;
                bufferInfo.offset = 0;
                bufferInfo.flags = mediaExtractor.getSampleFlags();
                mediaMuxer.writeSampleData(videoTrackIndex, byteBuffer, bufferInfo);
                byteBuffer.clear();
                mediaExtractor.advance();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mediaMuxer != null) {
                mediaMuxer.stop();
                mediaMuxer.release();
            }
            mediaExtractor.release();
            mediaExtractor = null;
            Log.i(TAG, "mediaExtractor release ！！！");
            mediaMuxer = null;
            Log.i(TAG, "mediaMuxer release ！！！");

        }
    }

    public static void addAacToMp4(String mp4Path, String aacPath, String targetMp4path) {
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();

        try {
            videoExtractor.setDataSource(mp4Path);
            MediaFormat videoFormat = null;
            int videoTrackIndex = -1;
            int videoTrackCount = videoExtractor.getTrackCount();
            for (int i = 0; i < videoTrackCount; i++) {
                videoFormat = videoExtractor.getTrackFormat(i);
                String mimeType = videoFormat.getString(MediaFormat.KEY_MIME);
                if (mimeType.startsWith("video/")) {
                    videoTrackIndex = i;
                    break;
                }
            }

            audioExtractor.setDataSource(aacPath);
            MediaFormat audioFormat = null;
            int audioTrackIndex = -1;
            int audioTrackCount = audioExtractor.getTrackCount();
            for (int i = 0; i < audioTrackCount; i++) {
                audioFormat = audioExtractor.getTrackFormat(i);
                String mimeType = audioFormat.getString(MediaFormat.KEY_MIME);
                if (mimeType.startsWith("audio/")) {
                    audioTrackIndex = i;
                    break;
                }
            }

            videoExtractor.selectTrack(videoTrackIndex);
            audioExtractor.selectTrack(audioTrackIndex);

            MediaMuxer mediaMuxer = new MediaMuxer(targetMp4path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if (videoTrackIndex == -1 || audioTrackIndex == -1 || videoFormat == null || audioFormat == null) {
                return;
            }
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            int writeVideoTrackIndex = mediaMuxer.addTrack(videoFormat);
            int writeAudioTrackIndex = mediaMuxer.addTrack(audioFormat);
            mediaMuxer.start();

            int videoPtsCount = 0; //这个参数保证 音频长度和视频长度 一样
            ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);
            while (true) {
                int readVideoSampleSize = videoExtractor.readSampleData(byteBuffer, 0);
                if (readVideoSampleSize < 0) {
                    Log.i(TAG, "read video data end ！！！");
                    break;
                }
                videoBufferInfo.size = readVideoSampleSize;
                videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                videoBufferInfo.offset = 0;
                videoBufferInfo.flags = videoExtractor.getSampleFlags();
                Log.i(TAG, "writeSampleData video data end ！！！pts:" + videoBufferInfo.presentationTimeUs + "  videoPtsCount: " + videoPtsCount);
                mediaMuxer.writeSampleData(writeVideoTrackIndex, byteBuffer, videoBufferInfo);
                videoPtsCount++;
                videoExtractor.advance();
            }
            long audioFirstPts = 0L;// 这个参数决定循环添加音轨数据的时候 pts的正确性
            while (true) {
                if (videoPtsCount == 0) {
                    Log.i(TAG, "videoPtsCount = 0, read audio data end ！！！");
                    break;
                }
                int readAudioSampleSize = audioExtractor.readSampleData(byteBuffer, 0);
                if (readAudioSampleSize < 0) {
                    //audioFirstPts =
                    Log.i(TAG, "read audio data end ！！！");
                    audioExtractor.seekTo(0, SEEK_TO_CLOSEST_SYNC);
                    readAudioSampleSize = audioExtractor.readSampleData(byteBuffer, 0);
                }
                audioBufferInfo.size = readAudioSampleSize;
                audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                audioBufferInfo.offset = 0;
                audioBufferInfo.flags = videoExtractor.getSampleFlags();

                Log.i(TAG, "writeSampleData audio data end ！！！pts:" + audioBufferInfo.presentationTimeUs + "  videoPtsCount: " + videoPtsCount);
                mediaMuxer.writeSampleData(writeAudioTrackIndex, byteBuffer, audioBufferInfo);
                videoPtsCount--;
                audioExtractor.advance();
            }
            mediaMuxer.stop();
            mediaMuxer.release();
            videoExtractor.release();
            audioExtractor.release();
            Log.i(TAG, "videoExtractor audioExtractor mediaMuxer release ！！！");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 建议方法放在子线程去处理
     *
     * @param videoPath       原始视频路径
     * @param aacPath         aac文件路径
     * @param targetVideoPath 合成视频文件路径
     */
    public static void addSoundToVideo(String videoPath, String aacPath, String targetVideoPath) {
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor aacExtractor = new MediaExtractor();
        MediaMuxer mediaMuxer = null;
        try {
            videoExtractor.setDataSource(videoPath);
            MediaFormat videoFormat = null;
            int videoTrackIndex = -1;
            int videoTrackCount = videoExtractor.getTrackCount();
            for (int i = 0; i < videoTrackCount; i++) {
                videoFormat = videoExtractor.getTrackFormat(i);
                String mimeType = videoFormat.getString(MediaFormat.KEY_MIME);
                if (mimeType.startsWith("video/")) {
                    videoTrackIndex = i;
                    break;
                }
            }

            aacExtractor.setDataSource(aacPath);
            MediaFormat aacFormat = null;
            int aacTrackIndex = -1;
            int aacTrackCount = aacExtractor.getTrackCount();
            for (int i = 0; i < aacTrackCount; i++) {
                aacFormat = aacExtractor.getTrackFormat(i);
                String mimeType = aacFormat.getString(MediaFormat.KEY_MIME);
                if (mimeType.startsWith("audio/")) {
                    aacTrackIndex = i;
                    break;
                }
            }

            if (videoTrackIndex == -1 || aacTrackIndex == -1 || videoFormat == null || aacFormat == null) {
                // here data is null , work will fail.
                return;
            }

            videoExtractor.selectTrack(videoTrackIndex);
            aacExtractor.selectTrack(aacTrackIndex);

            mediaMuxer = new MediaMuxer(targetVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo aacBufferInfo = new MediaCodec.BufferInfo();
            int writeVideoTrackIndex = mediaMuxer.addTrack(videoFormat);
            int writeAacTrackIndex = mediaMuxer.addTrack(aacFormat);
            mediaMuxer.start();

            ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);

            // videoPtsCount is important, keeping long synchronization of video and audio
            int videoPtsCount = 0;

            // Write video data first
            while (true) {
                int readVideoSampleSize = videoExtractor.readSampleData(byteBuffer, 0);
                if (readVideoSampleSize < 0) {
                    Log.i(TAG, "read video data end !!!");
                    break;
                }
                videoBufferInfo.flags = videoExtractor.getSampleFlags();
                videoBufferInfo.offset = 0;
                videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                videoBufferInfo.size = readVideoSampleSize;
                Log.i(TAG, "writeSampleData video data end ！！！pts:" + videoBufferInfo.presentationTimeUs + "  videoPtsCount: " + videoPtsCount);
                mediaMuxer.writeSampleData(videoTrackIndex, byteBuffer, videoBufferInfo);
                videoPtsCount++;
                videoExtractor.advance();
            }

            byteBuffer.clear();

            // Define your own PTS here.
            long currentAacPts = 0;
            while (true) {
                if (videoPtsCount == 0) {
                    Log.i(TAG, "you aac duration is just the same as mp4 duration, read aac data end !!! ");
                    break;
                }
                int readAacSampleSize = aacExtractor.readSampleData(byteBuffer, 0);
                if (readAacSampleSize < 0) {
                    aacExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    readAacSampleSize = aacExtractor.readSampleData(byteBuffer, 0);
                    Log.i(TAG, "you aac duration is just the same as mp4 duration, read aac data end !!! ");
                }
                aacBufferInfo.offset = 0;
                aacBufferInfo.size = readAacSampleSize;
                long tempPts = aacExtractor.getSampleTime();
                boolean b = currentAacPts == 0;
                Log.i(TAG, "init currentAacPts: " + currentAacPts + "  tempPts: " + tempPts + "  b: " + b);
                if (b && tempPts != 0) {
                    currentAacPts = tempPts;
                    Log.i(TAG, "init currentAacPts: " + currentAacPts + "  tempPts: " + tempPts);
                }
                aacBufferInfo.presentationTimeUs = currentAacPts++;
                int flags = aacExtractor.getSampleFlags();
                if (videoPtsCount == 1) {
                    aacBufferInfo.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                } else if (flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    aacBufferInfo.flags = 0;
                } else {
                    aacBufferInfo.flags = flags;
                }

                Log.i(TAG, "writeSampleData aac data !!!  pts:" + aacBufferInfo.presentationTimeUs + "  videoPtsCount: " + videoPtsCount);
                mediaMuxer.writeSampleData(writeAacTrackIndex, byteBuffer, aacBufferInfo);
                videoPtsCount--;
                aacExtractor.advance();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mediaMuxer != null) {
                mediaMuxer.stop();
                mediaMuxer.release();
            }
            videoExtractor.release();
            aacExtractor.release();
            Log.i(TAG, "mediaMuxer  videoExtractor  aacExtractor release !!! ");
        }
    }

    public  static int mergeVideo(String videoFile,String audioFile,String outMp4File){
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        try {
            videoExtractor.setDataSource(videoFile);
            audioExtractor.setDataSource(audioFile);
        }catch (IOException ex)
        {
            return -1;
        }

        int videoTrack = getTrack(videoExtractor,"video/");
        int audioTrack = getTrack(audioExtractor,"audio/");
        if(videoTrack == -1)
        {
            return -1;
        }
        if(audioTrack == -1)
        {
            return -1;
        }

        videoExtractor.selectTrack(videoTrack);
        audioExtractor.selectTrack(audioTrack);
        MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrack);
        MediaFormat audioFormat = audioExtractor.getTrackFormat(audioTrack);

        MediaMuxer muxer;
        try {
            muxer = new MediaMuxer(outMp4File, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }catch(IOException ex)
        {
            return -1;
        }
        int videoIndex = muxer.addTrack(videoFormat);
        int audioIndex = muxer.addTrack(audioFormat);
        ByteBuffer videoBuffer = ByteBuffer.allocate(1024*1024*5);
        ByteBuffer audioBuffer = ByteBuffer.allocate(1024*1024*5);
        muxer.start();

        boolean videoEnd = false;
        boolean audioEnd = false;

        long lastAudioPts = 0;//aac文件最后一帧的pts
        long totalCurAudioPts = 0;//时间轴上的当前帧
        while(true)
        {
            int vRet = videoExtractor.readSampleData(videoBuffer,0);
            if(vRet == -1)
            {
                videoEnd = true;
            }
            long vTime = videoExtractor.getSampleTime();
            int vFlags = videoExtractor.getSampleFlags();


            int aRet = audioExtractor.readSampleData(audioBuffer,0);
            if (videoEnd){
                audioEnd = true;
            }
            if(!audioEnd && aRet == -1)
            {
                lastAudioPts = totalCurAudioPts;
                audioExtractor.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                aRet = audioExtractor.readSampleData(audioBuffer,0);
                Log.i(TAG,"seekTo: aRet "+aRet);
            }

            long currentAudioPts = audioExtractor.getSampleTime();
            totalCurAudioPts = lastAudioPts + currentAudioPts;
            long aTime = totalCurAudioPts;
            int aFlags = audioExtractor.getSampleFlags();
            Log.i(TAG,"aac pts : "+aTime);

            if(videoEnd && audioEnd)
            {
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                bi.presentationTimeUs = 0;
                bi.offset = 0;
                bi.size = 0;
                bi.flags = BUFFER_FLAG_END_OF_STREAM;
                muxer.writeSampleData(videoIndex,videoBuffer,bi);
                muxer.writeSampleData(audioIndex,audioBuffer,bi);
                Log.i(TAG,"videoEnd audioEnd aac write pts "+bi.presentationTimeUs);
                break;
            }

             if(videoEnd)
            {
                //Log.d(TAG,"time: videoEnd:" + aTime + " audio Size:" + aRet);
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                bi.presentationTimeUs = aTime;
                bi.offset = 0;
                bi.size = aRet;
                bi.flags = aFlags;
                muxer.writeSampleData(audioIndex,audioBuffer,bi);
                Log.i(TAG,"videoEnd aac write pts "+bi.presentationTimeUs);
                audioExtractor.advance();
            }
            else if(audioEnd)
            {
                //Log.d(TAG,"time: audioEnd:" + vTime + " video Size:" + vRet);
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                bi.presentationTimeUs = vTime;
                bi.offset = 0;
                bi.size = vRet;
                bi.flags = vFlags;
                muxer.writeSampleData(videoIndex,videoBuffer,bi);
                Log.i(TAG,"audioEnd video write pts "+bi.presentationTimeUs);
                videoExtractor.advance();
            }
            else if(vTime <= aTime)
            {
                //Log.d(TAG,"time:V-A" + vTime + " " + aTime + " video Size:" + vRet);
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                bi.presentationTimeUs = vTime;
                bi.offset = 0;
                bi.size = vRet;
                bi.flags = vFlags;
                muxer.writeSampleData(videoIndex,videoBuffer,bi);
                Log.i(TAG,"video write pts "+bi.presentationTimeUs);
                videoExtractor.advance();
            }else
            {
                //Log.d(TAG,"time:V-A :" + vTime + " " + aTime + " audio Size:" + aRet);
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                bi.presentationTimeUs = aTime;
                bi.offset = 0;
                bi.size = aRet;
                bi.flags = aFlags;
                muxer.writeSampleData(audioIndex,audioBuffer,bi);
                Log.i(TAG,"aac write pts "+bi.presentationTimeUs);
                audioExtractor.advance();
            }
        }
        while(true)
        {
            try {
                muxer.stop();
            }catch(IllegalStateException ex)
            {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            break;
        }
        muxer.release();
        //Log.d(TAG,"over.");
        return 0;
    }


    public static int mergeVideo1(String videoFile,String audioFile,String outMp4File){
        MediaExtractor extractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        try {
            extractor.setDataSource(videoFile);
            audioExtractor.setDataSource(audioFile);
        }catch (IOException ex)
        {
            return -1;
        }

        int videoTrack = getTrack(extractor,"video/");
        int audioTrack = getTrack(audioExtractor,"audio/");
        if(videoTrack == -1)
        {
            return -1;
        }
        if(audioTrack == -1)
        {
            return -1;
        }

        extractor.selectTrack(videoTrack);
        audioExtractor.selectTrack(audioTrack);
        MediaFormat videoFormat = extractor.getTrackFormat(videoTrack);
        MediaFormat audioFormat = audioExtractor.getTrackFormat(audioTrack);

        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(outMp4File, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }catch(IOException ex)
        {
            return -1;
        }
        int videoIndex = muxer.addTrack(videoFormat);
        int audioIndex = muxer.addTrack(audioFormat);
        ByteBuffer videoBuffer = ByteBuffer.allocate(1024*1024*5);
        ByteBuffer audioBuffer = ByteBuffer.allocate(1024*1024*5);
        muxer.start();

        boolean videoEnd = false;
        boolean audioEnd = false;
        while(true)
        {
            int vRet = extractor.readSampleData(videoBuffer,0);
            if(vRet == -1)
            {
                videoEnd = true;
            }
            long vTime = extractor.getSampleTime();
            int vFlags = extractor.getSampleFlags();

            int aRet = audioExtractor.readSampleData(audioBuffer,0);
            if(aRet == -1)
            {
                audioEnd = true;
            }
            long aTime = audioExtractor.getSampleTime();
            int aFlags = audioExtractor.getSampleFlags();

            if(videoEnd && audioEnd)
            {
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                bi.presentationTimeUs = 0;
                bi.offset = 0;
                bi.size = 0;
                bi.flags = BUFFER_FLAG_END_OF_STREAM;
                muxer.writeSampleData(videoIndex,videoBuffer,bi);
                muxer.writeSampleData(audioIndex,audioBuffer,bi);
                break;
            }
            else if(videoEnd)
            {
                //Log.d(TAG,"time: videoEnd:" + aTime + " audio Size:" + aRet);
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                bi.presentationTimeUs = aTime;
                bi.offset = 0;
                bi.size = aRet;
                bi.flags = aFlags;
                muxer.writeSampleData(audioIndex,audioBuffer,bi);
                audioExtractor.advance();
            }
            else if(audioEnd)
            {
                //Log.d(TAG,"time: audioEnd:" + vTime + " video Size:" + vRet);
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                bi.presentationTimeUs = vTime;
                bi.offset = 0;
                bi.size = vRet;
                bi.flags = vFlags;
                muxer.writeSampleData(videoIndex,videoBuffer,bi);
                extractor.advance();
            }
            else if(vTime <= aTime)
            {
                //Log.d(TAG,"time:V-A" + vTime + " " + aTime + " video Size:" + vRet);
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                bi.presentationTimeUs = vTime;
                bi.offset = 0;
                bi.size = vRet;
                bi.flags = vFlags;
                muxer.writeSampleData(videoIndex,videoBuffer,bi);
                extractor.advance();
            }else
            {
                //Log.d(TAG,"time:V-A :" + vTime + " " + aTime + " audio Size:" + aRet);
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                bi.presentationTimeUs = aTime;
                bi.offset = 0;
                bi.size = aRet;
                bi.flags = aFlags;
                muxer.writeSampleData(audioIndex,audioBuffer,bi);
                audioExtractor.advance();
            }
        }
        while(true)
        {
            try {
                muxer.stop();
            }catch(IllegalStateException ex)
            {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            break;
        }
        muxer.release();
        //Log.d(TAG,"over.");
        return 0;
    }

    public static int getTrack(MediaExtractor extractor,String mime)
    {
        for(int i = 0 ; i < extractor.getTrackCount();i++)
        {
            MediaFormat format = extractor.getTrackFormat(i);
            String key_mime = format.getString(KEY_MIME);
            if(key_mime.startsWith(mime))
            {
                return i;
            }
        }
        return -1;
    }

    public static long getDuration(String url) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(url);
        String timeString = retriever.extractMetadata(MediaMetadataRetriever
                .METADATA_KEY_DURATION);
        retriever.release();
        return Long.parseLong(timeString);
    }
}
