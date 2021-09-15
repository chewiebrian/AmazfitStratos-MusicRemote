package com.fgil55.amazfit.musicremote;

import com.huami.watch.launcher.musiccontrol.MusicAction;

public enum MusicCommand {

    VOL_UP("vol_up", R.string.vol_up),
    VOL_DOWN("vol_down",R.string.vol_down),
    PRE("pre",R.string.pre),
    NEXT("next",R.string.next),
    PAUSE("pause",R.string.pause),
    RESUME("resume",R.string.resume);

    private final MusicAction action;
    private final int id;

    MusicCommand(String action, int label) {
        this.action = new MusicAction(action);
        this.id = label;
    }

    public MusicAction getAction() {
        return action;
    }

    public int getResourceLabelId() {
        return id;
    }
}
