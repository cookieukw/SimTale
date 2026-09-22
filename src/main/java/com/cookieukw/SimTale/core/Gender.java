package com.cookieukw.SimTale.core;

public enum Gender {
    MALE("Masculino"),
    FEMALE("Feminino");
    
    private final String displayName;

    Gender(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
