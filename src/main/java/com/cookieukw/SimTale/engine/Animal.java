package com.cookieukw.SimTale.engine;

import java.util.Map;

public class Animal {
    private String id;
    private LocalizedString name;
    private int playCount;
    private Map<String, Double> answers;

    public Animal() {}

    public Animal(String id, LocalizedString name, int playCount, Map<String, Double> answers) {
        this.id = id;
        this.name = name;
        this.playCount = playCount;
        this.answers = answers;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalizedString getName() {
        return name;
    }

    public void setName(LocalizedString name) {
        this.name = name;
    }

    public int getPlayCount() {
        return playCount;
    }

    public void setPlayCount(int playCount) {
        this.playCount = playCount;
    }

    public Map<String, Double> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<String, Double> answers) {
        this.answers = answers;
    }
}
