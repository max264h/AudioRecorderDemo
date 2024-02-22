package com.example.audiorecorderdemo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private Button on, off, test;
    private static final int SAMPLE_RATE = 44100; // 采樣率
//    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO; // 雙聲道
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO; // 單聲道
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; // 音頻格式
    private AudioRecord audioRecord;
    private Boolean isRecording = false;
    private int bufferSize;
    private AudioTrack audioTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        on = findViewById(R.id.on);
        off = findViewById(R.id.off);
        test = findViewById(R.id.test);

        //要取錄製權限
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 200);
            return;
        }

        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);

        //將AudioTrack的參數設定成跟錄製的一樣
        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                //單聲道為MONO，雙聲道為STEREO
                AudioFormat.CHANNEL_OUT_MONO,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM);

        //開啟錄音
        on.setOnClickListener(view -> {
            startRecording("/storage/emulated/0/Download/myAudioPcm.pcm");
        });

        //關閉錄音
        off.setOnClickListener(view -> {
            isRecording = false;
        });

        //其他功能
        test.setOnClickListener(view -> {
//            //將指定路徑的pcm檔轉成wav檔
            pcmToWav("/storage/emulated/0/Download/myAudioPcm.pcm",
                    "/storage/emulated/0/Download/myAudioWav.wav",
                    false);
            //使用AudioTrack播放音檔
//            AudioTrackTest("/storage/emulated/0/Download/myAudioFilePcm.pcm");
        });
    }

    //開啟錄音功能，並將錄製的音檔存到指定檔案路徑
    public void startRecording(String fileName) {
        new File(fileName).delete();

        Thread recordingThread = new Thread(() -> {
            try (FileOutputStream out = new FileOutputStream(fileName)) {
                byte[] buffer = new byte[bufferSize];
                audioRecord.startRecording();
                isRecording = true;
                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, bufferSize);
                    if (read > 0) {
                        out.write(buffer, 0, read);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (audioRecord != null) {
                    audioRecord.stop();
                    audioRecord.release();
                }
            }
        });
        recordingThread.start();
    }



    //將音訊加上標頭變成wav檔
    private void writeWavFileHeader(FileOutputStream out, long totalAudioLen,
                                    long totalDataLen, long longSampleRate,
                                    int channels, long byteRate
    ) throws IOException {
        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = 16;
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        Log.d("test", "writeWavFileHeader: "+header);
        out.write(header, 0, 44);
    }

    //將指定路徑的pcm檔轉成wav檔並存到指定路徑，deleteOrg為是否刪除原檔案路徑
    public void pcmToWav(String inFilename, String outFilename, boolean deleteOrg) {
        FileInputStream in;
        FileOutputStream out;
        long totalAudioLen;
        long totalDataLen;
        long longSampleRate = SAMPLE_RATE;
        //單聲道設為1，雙聲道設為2
        int channels = 1;
        long byteRate = 16 * SAMPLE_RATE * channels / 8;
        byte[] data = new byte[bufferSize];
        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel( ). size();
            totalDataLen = totalAudioLen +36 ;
            writeWavFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);
            while (in.read(data) !=-1) {
                out.write(data);
            }
            in.close();
            out.close();
            if (deleteOrg) {
                new File(inFilename).delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //使用AudioTrack播放指定路徑的音訊
    //inFilename為指定路徑的音檔
    public void AudioTrackTest(String inFilename){
        try {
            File file = new File(inFilename);
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[bufferSize];

            audioTrack.play(); // 開始播放

            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                audioTrack.write(buffer, 0, bytesRead);
            }

            audioTrack.stop(); // 停止播放
            audioTrack.release(); // 釋放資源
            fis.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}