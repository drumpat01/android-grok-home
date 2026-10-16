package dev.grokhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.*;
import android.media.audiofx.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** 24 kHz mono PCM streaming with bounded queues and hardware echo cancellation. */
final class AndroidAudio implements VoiceClient.AudioIO {
    private static final int RATE=24000;
    private final Consumer<byte[]> capture;
    private final Consumer<Boolean> speaking;
    private final Consumer<String> failure;
    private final BlockingQueue<Chunk> output=new LinkedBlockingQueue<>(256);
    private final Object outputLock=new Object();
    private final AudioManager manager;
    private final int previousMode;
    private final boolean previousSpeaker;
    private AudioFocusRequest focus;
    private AudioTrack track;
    private volatile AudioRecord recorder;
    private AcousticEchoCanceler echo;
    private NoiseSuppressor noise;
    private volatile boolean closed, mic;
    private volatile int epoch;
    private long writtenFrames;
    private boolean talking, outputPlaying, outputFinished;
    private volatile boolean responseEnded;
    private int primeFrames;
    private long firstWriteNanos;
    private final byte[] silence=new byte[960];
    private boolean shortPaddingSent;
    private static final class Chunk {final byte[] data;final int epoch;Chunk(byte[] data,int epoch){this.data=data;this.epoch=epoch;}}

    AndroidAudio(Context context,Consumer<byte[]> capture,Consumer<Boolean> speaking,Consumer<String> failure){
        this.capture=capture;this.speaking=speaking;this.failure=failure;
        manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        previousMode=manager.getMode();previousSpeaker=manager.isSpeakerphoneOn();
        try{
            AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change->{if(change<0 && !closed)failure.accept("Another app needs the audio. Tap microphone to retry");}).build();
            if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw new IllegalStateException("Audio focus unavailable");
            manager.setMode(AudioManager.MODE_IN_COMMUNICATION);manager.setSpeakerphoneOn(true);
            // 400 ms capacity absorbs scheduling/network jitter. Prime 180 ms
            // before playing instead of starting an empty 20 ms write stream.
            int size=Math.max(RATE*2*400/1000,AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)*4);
            track=new AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(size).setTransferMode(AudioTrack.MODE_STREAM).build();
            if(track.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("Speaker unavailable");
            primeFrames=Math.min(RATE*180/1000,track.getBufferSizeInFrames()/2);
            new Thread(this::playback,"grok-speaker").start();
        }catch(RuntimeException error){close();throw error;}
    }
    @SuppressLint("MissingPermission") // HomeActivity checks runtime permission before enabling capture.
    @Override public synchronized void microphone(boolean enabled){
        if(closed || enabled==mic)return;
        mic=enabled;
        if(!enabled){stopRecorder();return;}
        try{
            int size=Math.max(4800,AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)*2);
            AudioRecord input=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);
            if(input.getState()!=AudioRecord.STATE_INITIALIZED){input.release();throw new IllegalStateException("Microphone unavailable");}
            recorder=input;
            if(AcousticEchoCanceler.isAvailable()){echo=AcousticEchoCanceler.create(input.getAudioSessionId());if(echo!=null)echo.setEnabled(true);}
            if(NoiseSuppressor.isAvailable()){noise=NoiseSuppressor.create(input.getAudioSessionId());if(noise!=null)noise.setEnabled(true);}
            input.startRecording();
            new Thread(()->record(input),"grok-microphone").start();
        }catch(RuntimeException error){mic=false;stopRecorder();failure.accept("Check microphone permission and audio input");}
    }
    private void record(AudioRecord input){
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        byte[] buffer=new byte[960];
        try{
            while(!closed && mic && recorder==input){
                int count=input.read(buffer,0,buffer.length,AudioRecord.READ_BLOCKING);
                if(count<0)throw new IllegalStateException("Microphone read failed");
                if(count>0 && mic && recorder==input)capture.accept(Arrays.copyOf(buffer,count));
            }
        }catch(RuntimeException error){if(!closed && mic && recorder==input)failure.accept("Microphone stopped. Tap microphone to retry");}
    }
    private void stopRecorder(){
        AudioRecord input=recorder;recorder=null;
        if(input!=null){try{input.stop();}catch(IllegalStateException ignored){}input.release();}
        if(echo!=null){echo.release();echo=null;}if(noise!=null){noise.release();noise=null;}
    }
    @Override public void play(byte[] pcm){
        if(!closed && pcm.length>0 && !output.offer(new Chunk(pcm,epoch)))failure.accept("Audio buffer is full. Tap microphone to reconnect");
    }
    private void playback(){
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        try{
            while(!closed){
                Chunk chunk=output.poll(20,TimeUnit.MILLISECONDS);
                if(chunk!=null){
                    for(int offset=0;offset<chunk.data.length && !closed && chunk.epoch==epoch;){
                        boolean began=false;int count;
                        synchronized(outputLock){
                            if(closed || chunk.epoch!=epoch)break;
                            count=track.write(chunk.data,offset,Math.min(960,chunk.data.length-offset),outputPlaying?AudioTrack.WRITE_BLOCKING:AudioTrack.WRITE_NON_BLOCKING);
                            if(count<0)throw new IllegalStateException("Speaker write failed");
                            if(count>0 && writtenFrames==0)firstWriteNanos=System.nanoTime();
                            writtenFrames+=count/2;
                            boolean finalWrite=responseEnded && output.isEmpty() && offset+count>=chunk.data.length;
                            if(!outputPlaying && writtenFrames>0 && (writtenFrames>=primeFrames || finalWrite)){
                                startOutputLocked();talking=true;began=true;
                            }
                            if(outputPlaying && count>0 && !talking){talking=true;began=true;}
                        }
                        if(began)speaking.accept(true);
                        if(count==0)Thread.sleep(5);offset+=count;
                    }
                }
                boolean ended=false,began=false;
                synchronized(outputLock){
                    // Also flush a short reply that never reaches the prime
                    // threshold; do not leave its tail waiting indefinitely.
                    if(!closed && !outputPlaying && !outputFinished && writtenFrames>0 && (responseEnded || System.nanoTime()-firstWriteNanos>=TimeUnit.MILLISECONDS.toNanos(200))){
                        startOutputLocked();talking=true;began=true;
                    }
                    if(!closed && responseEnded && output.isEmpty())padShortOutputLocked();
                    if(!closed && writtenFrames>0 && output.isEmpty() && head()>=writtenFrames){
                        if(talking){talking=false;ended=true;}
                        if(responseEnded && outputPlaying){track.pause();outputPlaying=false;outputFinished=true;}
                    }
                }
                if(began)speaking.accept(true);
                if(ended)speaking.accept(false);
            }
        }catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        catch(RuntimeException error){if(!closed)failure.accept("Speaker stopped. Tap microphone to retry");}
    }
    private long head(){return Integer.toUnsignedLong(track.getPlaybackHeadPosition());}
    private void startOutputLocked(){
        if(android.os.Build.VERSION.SDK_INT>=31)track.setStartThresholdInFrames((int)Math.min(primeFrames,writtenFrames));
        else if(responseEnded)padShortOutputLocked();
        track.play();outputPlaying=true;
    }
    private void padShortOutputLocked(){
        // Android 11 has no adjustable start threshold. Supply only the missing
        // startup silence, while keeping speech duration/truncation unchanged.
        if(android.os.Build.VERSION.SDK_INT>=31 || shortPaddingSent || writtenFrames==0)return;
        int remaining=(int)Math.max(0,(track.getBufferSizeInFrames()-writtenFrames)*2);
        while(remaining>0){int n=track.write(silence,0,Math.min(silence.length,remaining),AudioTrack.WRITE_NON_BLOCKING);if(n<=0)break;remaining-=n;}
        shortPaddingSent=true;
    }
    @Override public void beginResponse(){resetOutput();}
    @Override public void finishResponse(){responseEnded=true;}
    int underruns(){synchronized(outputLock){return track==null?0:track.getUnderrunCount();}}
    boolean outputPlaying(){synchronized(outputLock){return outputPlaying;}}
    @Override public int interrupt(){
        int heard;
        synchronized(outputLock){
            if(closed || track==null)return 0;
            heard=(int)(Math.min(writtenFrames,head())*1000/RATE);
            resetOutputLocked();
        }
        speaking.accept(false);return heard;
    }
    private void resetOutput(){synchronized(outputLock){if(!closed)resetOutputLocked();}speaking.accept(false);}
    private void resetOutputLocked(){
        epoch++;output.clear();track.pause();track.flush();track.stop();writtenFrames=0;
        talking=false;outputPlaying=false;outputFinished=false;responseEnded=false;firstWriteNanos=0;shortPaddingSent=false;
    }
    @Override public synchronized void close(){
        if(closed)return;closed=true;mic=false;stopRecorder();
        synchronized(outputLock){output.clear();if(track!=null){try{track.pause();track.flush();}catch(IllegalStateException ignored){}track.release();track=null;}}
        if(focus!=null)manager.abandonAudioFocusRequest(focus);
        manager.setSpeakerphoneOn(previousSpeaker);manager.setMode(previousMode);
    }
}
