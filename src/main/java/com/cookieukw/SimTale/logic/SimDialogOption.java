package com.cookieukw.SimTale.logic;

import com.hypixel.hytale.server.core.Message;

public class SimDialogOption {
    public final Message text;
    public final Runnable action;

    public SimDialogOption(Message text, Runnable action) {
        this.text = text;
        this.action = action;
    }
}
