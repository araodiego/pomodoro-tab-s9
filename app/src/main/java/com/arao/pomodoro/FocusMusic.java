package com.arao.pomodoro;

import android.app.*;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.os.*;
import android.util.Log;
import java.io.IOException;

/** The persisted timer owns the clock. Losing playback can never change a deadline. */
public final class FocusMusic extends Service {
    private static final String CHANNEL = "focus_soundtrack";
    private static final String ENABLED = "stillCurrent";
    private static final String MIX_KEY = "stillCurrentMix";
    enum Mix {
        OFF("off", R.string.music_off, 0),
        PAD("pad", R.string.music_pad, R.raw.still_current),
        FULL("full", R.string.music_full, R.raw.still_current_full);

        final String key;
        final int label, recording;

        Mix(String key, int label, int recording) {
            this.key = key;
            this.label = label;
            this.recording = recording;
        }
    }
    private static final AudioAttributes MUSIC = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audio;
    private AudioFocusRequest request;
    private MediaPlayer player, fading;
    private Mix playingMix;
    private float currentVolume, outgoingVolume;
    private long shifted = -1, fadeStart;
    private boolean hasFocus;
    private String token = "";

    static Mix selection(Context c) {
        SharedPreferences prefs = c.getSharedPreferences("work", MODE_PRIVATE);
        String key = prefs.getString(MIX_KEY, null);
        if (key == null) return prefs.getBoolean(ENABLED, false) ? Mix.PAD : Mix.OFF;
        for (Mix mix : Mix.values()) {
            if (mix.key.equals(key)) return mix;
        }
        return Mix.OFF;
    }
    static void select(Context c, Mix mix) {
        c.getSharedPreferences("work", MODE_PRIVATE).edit()
                .putString(MIX_KEY, mix.key).remove("musicInterrupted").apply();
        sync(c,TimerStore.load(c));
    }
    static void sync(Context c, TimerState s) {
        Intent service = new Intent(c,FocusMusic.class);
        if (selection(c) == Mix.OFF || !s.running || s.phase != TimerState.Phase.FOCUS ||
                s.token.equals(c.getSharedPreferences("work",MODE_PRIVATE).getString("musicInterrupted","--"))) {
            c.stopService(service);
            return;
        }
        try { c.startForegroundService(service); }
        catch (IllegalStateException | SecurityException denied) { Log.w("FocusMusic","Playback unavailable",denied); }
    }
    private void releasePlayers() {
        if (player!=null) player.release();
        if (fading!=null) fading.release();
        player=null;
        fading=null;
        playingMix=null;
        currentVolume=0;
        shifted=-1;
    }
    private void focusChanged(int change) {
        if (change==AudioManager.AUDIOFOCUS_GAIN) {
            hasFocus=true;
            scheduleTick();
        } else {
            hasFocus=false;
            releasePlayers();
            if (change==AudioManager.AUDIOFOCUS_LOSS) interrupt();
        }
    }
    private void interrupt() {
        getSharedPreferences("work",MODE_PRIVATE).edit().putString("musicInterrupted",token).apply();
        stopSelf();
    }
    private final BroadcastReceiver unplugged = new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) { releasePlayers(); interrupt(); }
    };
    @Override public void onCreate() {
        super.onCreate();
        audio=getSystemService(AudioManager.class);
        request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(MUSIC).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this::focusChanged,handler).build();
        IntentFilter filter=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT>=33) registerReceiver(unplugged,filter,RECEIVER_NOT_EXPORTED);
        else registerReceiver(unplugged,filter);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        NotificationManager nm=getSystemService(NotificationManager.class);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Focus music",NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null,null);
        nm.createNotificationChannel(channel);
        PendingIntent open=PendingIntent.getActivity(this,44,new Intent(this,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE);
        startForeground(44,new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("Still Current").setContentText(getText(selection(this).label)).setOngoing(true)
                .setContentIntent(open).setOnlyAlertOnce(true).build());
        TimerState s=TimerStore.load(this);
        if (selection(this) == Mix.OFF || !s.running || s.phase!=TimerState.Phase.FOCUS) {
            stopSelf(); return START_NOT_STICKY;
        }
        if (!token.equals(s.token)) {
            token=s.token;
            releasePlayers();
            hasFocus=audio.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if (!hasFocus) { stopSelf(); return START_NOT_STICKY; }
        }
        scheduleTick();
        return START_STICKY;
    }
    private MediaPlayer openAt(Mix mix, long position) throws IOException {
        MediaPlayer p=new MediaPlayer();
        try (AssetFileDescriptor fd=getResources().openRawResourceFd(mix.recording)) {
            p.setAudioAttributes(MUSIC);
            p.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);
            p.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());
            p.prepare();
            p.setVolume(0,0);
            p.setOnErrorListener((mp,what,extra)->{stopSelf(); return true;});
            p.seekTo(position,MediaPlayer.SEEK_CLOSEST);
            p.start();
            return p;
        } catch (IOException | RuntimeException ex) { p.release(); throw ex; }
    }
    private void scheduleTick() {
        handler.removeCallbacks(tick);
        handler.post(tick);
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            TimerState s=TimerStore.load(FocusMusic.this);
            long now=SystemClock.elapsedRealtime();
            long remaining=s.remaining(now);
            Mix selected = selection(FocusMusic.this);
            if (selected == Mix.OFF || !s.running || !token.equals(s.token)
                    || s.phase!=TimerState.Phase.FOCUS || remaining<=0) { stopSelf(); return; }
            if (hasFocus) {
                long elapsed=s.duration-remaining;
                long shift=MusicTimeline.inserted(elapsed,s.duration);
                try {
                    if (shift!=shifted || player==null || selected!=playingMix) {
                        if (fading!=null) fading.release();
                        fading=player;
                        outgoingVolume=currentVolume;
                        player=null;
                        player=openAt(selected,Math.max(0,elapsed-shift));
                        playingMix=selected;
                        shifted=shift;
                        fadeStart=now;
                    }
                    float mix=Math.min(1f,(now-fadeStart)/1500f);
                    float end=Math.min(1f,remaining/2000f);
                    currentVolume=mix*end;
                    player.setVolume(currentVolume,currentVolume);
                    if (fading!=null) {
                        float volume=outgoingVolume*(1-mix)*end;
                        fading.setVolume(volume,volume);
                        if (mix>=1) { fading.release(); fading=null; }
                    }
                } catch (IOException | RuntimeException e) {
                    Log.w("FocusMusic","Audio could not play",e); stopSelf(); return;
                }
            }
            handler.postDelayed(this,(now-fadeStart<1600 || remaining<2200)?50:250);
        }
    };
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePlayers();
        unregisterReceiver(unplugged);
        audio.abandonAudioFocusRequest(request);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
