package com.cookieukw.SimTale.engine;

public class Question {
    private String id;
    private LocalizedString text;

    public Question() {}

    public Question(String id, LocalizedString text) {
        this.id = id;
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalizedString getText() {
        return text;
    }

    public void setText(LocalizedString text) {
        this.text = text;
    }
}
