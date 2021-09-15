package com.fgil55.amazfit.musicremote;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.huami.watch.companion.mediac.MediaInfo;
import com.huami.watch.keyevent_lib.HMKeyDef;
import com.huami.watch.keyevent_lib.KeyEventHelpers;
import com.huami.watch.keyevent_lib.KeyeventConsumer;
import com.huami.watch.keyevent_lib.KeyeventProcessor;
import com.huami.watch.launcher.musiccontrol.CommandHandler;
import com.huami.watch.launcher.musiccontrol.MusicAction;
import com.huami.watch.launcher.musiccontrol.MusicConsoleClient;
import com.huami.watch.launcher.musiccontrol.MusicStatusListener;
import com.fgil55.amazfit.musicremote.ui.ProgressArc;

import java.lang.reflect.Field;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

public class MusicRemote extends Activity implements KeyeventConsumer {

    //Tag for logging purposes. Change this to something suitable
    private static final String TAG = "MusicRemote";
    private Context mContext;
    //These get set up later
    private boolean mHasActive = false;
    private CommandHandler commandHandler;
    private TextView artist, album, track, player;
    private ImageButton playPause, skipPrev, skipNext;
    private ProgressArc progressArc, volumeArc;
    //All booleans by default need to be false as they are the initial state
    private boolean isPlaying = false;
    private boolean isVolumePicker = false;
    private boolean hasLoadedMusic = false;
    //Initially the positions and durations are 0
    private long prevPos = 0;
    private long millis = 0;
    private long duration = 0;
    private KeyeventProcessor mKeyeventProcessor;
    private Timer timer = new Timer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(createView(this.getApplicationContext()));
        setupListener();
        setWindowAlwaysOn();
    }

    private void setWindowAlwaysOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getAttributes().screenBrightness = 0F;
        //getWindow().getAttributes().dimAmount = 1F;
    }

    public View createView(final Context paramContext) {
        //Keep context
        this.mContext = paramContext;
        //Inflate layout as required. The layout here being inflated is "widget_blank"
        View mView = LayoutInflater.from(paramContext).inflate(R.layout.musicremote_page, null);
        //Set up variables from layout
        artist = mView.findViewById(R.id.artist);
        album = mView.findViewById(R.id.album);
        track = mView.findViewById(R.id.track);
        player = mView.findViewById(R.id.player);
        skipPrev = mView.findViewById(R.id.skipPrev);
        progressArc = mView.findViewById(R.id.progress);
        volumeArc = mView.findViewById(R.id.volumeArc);
        //Set up click listeners for buttons
        skipPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //If in volume picker mode, turn volume down. Otherwise, skip backwards
                if (commandHandler != null)
                    commandHandler.sendAction(isVolumePicker ? new MusicAction("vol_down") : new MusicAction("pre"));
                if (isVolumePicker) setupHideVolume();
            }
        });
        skipNext = mView.findViewById(R.id.skipNext);
        skipNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //If in volume picker mode, turn volume up. Otherwise, skip forwards
                if (commandHandler != null)
                    commandHandler.sendAction(isVolumePicker ? new MusicAction("vol_up") : new MusicAction("next"));
                if (isVolumePicker) setupHideVolume();
            }
        });
        playPause = mView.findViewById(R.id.playPause);
        playPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //If playing, pause. If not, play
                if (commandHandler != null)
                    commandHandler.sendAction(isPlaying ? new MusicAction("pause") : new MusicAction("resume"));
            }
        });
        //Long press on play/pause switches between volume and normal controls
        playPause.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                //Ignore if there's no music loaded (volume isn't loaded until music is)
                if (!hasLoadedMusic) return true;
                //Toggle the volume picker
                toggleVolumePicker();
                return true;
            }
        });

        final AtomicReference<Toast> toast = new AtomicReference<>();

        this.mKeyeventProcessor = new KeyeventProcessor(new KeyEventHelpers.EventCallBack() {
            @Override
            public boolean onKeyClick(HMKeyDef.HMKeyEvent hmKeyEvent) {
                final MusicCommand command;
                switch (hmKeyEvent) {
                    case KEY_UP:
                        command = isVolumePicker ? MusicCommand.VOL_DOWN : MusicCommand.PRE;
                        break;
                    case KEY_DOWN:
                        command = isVolumePicker ? MusicCommand.VOL_UP : MusicCommand.NEXT;
                        break;
                    case KEY_CENTER:
                        command = isPlaying ? MusicCommand.PAUSE : MusicCommand.RESUME;
                        break;
                    default:
                        return false;
                }

                if (commandHandler != null)
                    commandHandler.sendAction(command.getAction());

                if (toast.get() != null) toast.get().cancel();
                toast.set(Toast.makeText(paramContext, command.getResourceLabelId(), Toast.LENGTH_SHORT));
                toast.get().show();
                return true;
            }

            @Override
            public boolean onKeyLongOneSecond(HMKeyDef.HMKeyEvent hmKeyEvent) {
                return false;
            }

            @Override
            public boolean onKeyLongOneSecondTimeOut(HMKeyDef.HMKeyEvent hmKeyEvent) {
                if (hmKeyEvent != HMKeyDef.HMKeyEvent.KEY_CENTER && hasLoadedMusic) {
                    MusicRemote.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toggleVolumePicker();
                        }
                    });
                    return true;
                }
                return false;
            }

            @Override
            public boolean onKeyLongThreeSecond(HMKeyDef.HMKeyEvent hmKeyEvent) {
                return false;
            }

            @Override
            public boolean onKeyLongThreeSecondTimeOut(HMKeyDef.HMKeyEvent hmKeyEvent) {
                return false;
            }
        });

        //Start the timer, running every second
        timer.scheduleAtFixedRate(t, 1000, 1000);
        return mView;
    }


    //Toggles the volume picker's visibility
    private void toggleVolumePicker() {
        //Toggle main variable
        isVolumePicker = !isVolumePicker;
        //Specific code for when the picker is shown
        if (isVolumePicker) {
            setupHideVolume();
        }
        //Update the volume picker's state
        setVolumePicker();
    }

    private TimerTask hideVolume;

    private void setupHideVolume() {
        //Cancel previous task is required
        if (hideVolume != null) {
            hideVolume.cancel();
        }
        hideVolume = new TimerTask() {
            @Override
            public void run() {
                //Hide the picker (must be run on UI)
                MusicRemote.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        isVolumePicker = false;
                        setVolumePicker();
                    }
                });
            }
        };
        //Run after 5s (doesn't matter if the user has already hidden it)
        timer.schedule(hideVolume, 8000);
    }

    //Updates the state of the volume picker views
    private void setVolumePicker() {
        //Setup skip icons
        skipPrev.setImageDrawable(isVolumePicker ? mContext.getDrawable(R.drawable.volume_minus) : mContext.getDrawable(R.drawable.skip_previous));
        skipNext.setImageDrawable(isVolumePicker ? mContext.getDrawable(R.drawable.volume_plus) : mContext.getDrawable(R.drawable.skip_next));
        //Show and hide the appropriate arc
        progressArc.setVisibility(isVolumePicker ? View.GONE : View.VISIBLE);
        volumeArc.setVisibility(isVolumePicker ? View.VISIBLE : View.GONE);
    }

    //Sets up music listener
    private void setupListener() {
        //MusicConsoleClient is a class from the launcher that can be used by importing the library. This prevents having to use my own Bluetooth code and an app on the phone
        MusicConsoleClient musicConsoleClient = MusicConsoleClient.theInstance(mContext);
        try {
            //Because MusicConsoleClient doesn't have a way to access the CommandHandler (and it's a private field), we'll use reflection to force it out. The field is called "theReceiver"
            Field commandHandlerField = MusicConsoleClient.class.getDeclaredField("theReceiver");
            //Make it accessible (again, it's private)
            commandHandlerField.setAccessible(true);
            //Retrieve it from the instance
            commandHandler = (CommandHandler) commandHandlerField.get(musicConsoleClient);
            //Now we can set our own MusicStatusListener
            commandHandler.setStatusListener(new MusicStatusListener() {
                @Override
                public void channelChanged(boolean b) {
                    //Nothing required
                }

                @Override
                public void mediaOffline(MediaInfo mediaInfo) {
                    //Player has stopped, update
                    updateTrackInfo(mediaInfo);
                }

                @Override
                public void mediaOnline(MediaInfo mediaInfo) {
                    //Player has started, update
                    updateTrackInfo(mediaInfo);
                }

                @Override
                public void mediaOnlineUpdatePause(MediaInfo mediaInfo) {
                    //Player has paused, update
                    updateTrackInfo(mediaInfo);
                }

                @Override
                public void mediaOnlineUpdatePlay(MediaInfo mediaInfo) {
                    //Player has started playing, update
                    updateTrackInfo(mediaInfo);
                }

                @Override
                public void mediaOnlineUpdateStop(MediaInfo mediaInfo) {
                    //Player has sent "stop" signal, update
                    updateTrackInfo(mediaInfo);
                }

                @Override
                public void offline(String s) {
                    //Appears to be when the player stops for the last time, but no data is sent. The string is the player name, not really useful
                }

                @Override
                public void online(String s) {
                    //Appears to be when the player starts for the first time, but no data is sent. The string is the player name, not really useful
                }

                @Override
                public void updateVolume(MediaInfo mediaInfo) {
                    //Volume has updated, update
                    updateTrackInfo(mediaInfo);
                }
            });
            //Send "so" signal. The decompiled code from the launcher suggests this is sent when the watch wakes up, maybe to update media info from when it was asleep?
            commandHandler.sendAction(new MusicAction("so"));
            sleep();
            commandHandler.sendAction(new MusicAction("so"));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(250);
        } catch (Throwable e) {
        }
    }


    //Load the given MediaInfo
    private void updateTrackInfo(final MediaInfo mediaInfo) {
        /*
            MediaInfo is another launcher class, with the following (useful) fields:
            public String album;
            public String artist;
            public int currVol;
            public Long duration;
            public int maxVol;
            public String mediaPlayerName;
            public Integer playerHasGone;
            public Long pos;
            public Integer state;
            public String title;
         */
        //Allow long press volume control
        hasLoadedMusic = true;
        //All updates to UI must be run on the main thread
        MusicRemote.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //Update textviews
                if (artist != null) artist.setText(mediaInfo.artist);
                if (album != null) album.setText(mediaInfo.album);
                if (track != null) track.setText(mediaInfo.title);
                if (player != null) player.setText(mediaInfo.mediaPlayerName);
                //Update volume arc with the volume's percentage
                if (volumeArc != null)
                    volumeArc.setProgress((double) mediaInfo.currVol / (double) mediaInfo.maxVol);
                //Update the play/pause button. Playing is state 3, paused is 2
                if (mediaInfo.state == 3) {
                    //Playing
                    if (playPause != null)
                        playPause.setImageDrawable(mContext.getDrawable(R.drawable.pause));
                    isPlaying = true;
                } else if (mediaInfo.state == 2) {
                    if (playPause != null)
                        playPause.setImageDrawable(mContext.getDrawable(R.drawable.play));
                    isPlaying = false;
                }
                //Sometimes the listener will fire a second time with an incorrect position that's identical to the previous one. We can ignore this by remembering the previous position and, if it matches, not updating the arc
                if (mediaInfo.pos != prevPos) {
                    millis = mediaInfo.pos;
                    prevPos = millis;
                }
                //Update duration of song
                duration = mediaInfo.duration;
                //Update position of progress arc
                if (progressArc != null)
                    progressArc.setProgress((double) mediaInfo.pos / (double) mediaInfo.duration);
                //Log.d(TAG, "Updating media info to " + mediaInfo.toString() + "\npos: " + mediaInfo.pos + "\nprogress: " + ((double) mediaInfo.pos / (double) mediaInfo.duration));
            }
        });
    }

    //Main timer task, only called once so can be global
    private TimerTask t = new TimerTask() {
        @Override
        public void run() {
            //Only trigger if the position needs updating and there's actually a song playing
            if (millis < duration && isPlaying) {
                //Increment the milliseconds by 1s
                millis += 1000;
                //Updating UI so main thread is required
                MusicRemote.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //Update progress of the arc
                        if (progressArc != null)
                            progressArc.setProgress((double) millis / (double) duration);
                    }
                });
            }
        }
    };

    private void refreshView() {
        //Called when the page reloads, check for updates here if you need to
    }

    //Called when the page is destroyed completely (in app mode). Same as the onDestroy method of an activity
    @Override
    public void onDestroy() {
        super.onDestroy();
        this.mHasActive = false;
        timer.cancel();
        timer.purge();
        this.finishAndRemoveTask();
        System.exit(0);         //no need for package to stay running on background
    }

    //Called when the page is paused (in app mode)
    @Override
    public void onPause() {
        super.onPause();
        this.mHasActive = false;
    }


    //Called when the page is shown again (in app mode)
    @Override
    public void onResume() {
        super.onResume();
        //Check if view already loaded
        if ((!this.mHasActive)) {
            //It is, simply refresh
            this.mHasActive = true;
            refreshView();
        }
        //Store active state
        this.mHasActive = true;
    }

    //Called when the page is stopped (in app mode)
    @Override
    public void onStop() {
        super.onStop();
        this.mHasActive = false;
    }

    @Override
    public boolean canAccept() {
        return mHasActive;
    }

    @Override
    public void injectKeyevent(KeyEvent keyEvent) {
        if (mKeyeventProcessor != null) mKeyeventProcessor.injectKeyEvent(keyEvent);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        this.injectKeyevent(event);
        return true;
    }
}