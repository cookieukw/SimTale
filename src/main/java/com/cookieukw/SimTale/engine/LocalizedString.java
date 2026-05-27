package com.cookieukw.SimTale.engine;

public class LocalizedString {
    private String pt;
    private String en;

    public LocalizedString() {}

    public LocalizedString(String pt, String en) {
        this.pt = pt;
        this.en = en;
    }

    public String getPt() {
        return pt;
    }

    public void setPt(String pt) {
        this.pt = pt;
    }

    public String getEn() {
        return en;
    }

    public void setEn(String en) {
        this.en = en;
    }
}
