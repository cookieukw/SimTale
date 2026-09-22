package com.cookieukw.SimTale.core;

import java.util.List;

public class Prefab {
    private int version;
    private int blockIdVersion;
    private int anchorX;
    private int anchorY;
    private int anchorZ;
    private List<PrefabBlock> blocks;

    public Prefab() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getBlockIdVersion() {
        return blockIdVersion;
    }

    public void setBlockIdVersion(int blockIdVersion) {
        this.blockIdVersion = blockIdVersion;
    }

    public int getAnchorX() {
        return anchorX;
    }

    public void setAnchorX(int anchorX) {
        this.anchorX = anchorX;
    }

    public int getAnchorY() {
        return anchorY;
    }

    public void setAnchorY(int anchorY) {
        this.anchorY = anchorY;
    }

    public int getAnchorZ() {
        return anchorZ;
    }

    public void setAnchorZ(int anchorZ) {
        this.anchorZ = anchorZ;
    }

    public List<PrefabBlock> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<PrefabBlock> blocks) {
        this.blocks = blocks;
    }
}
