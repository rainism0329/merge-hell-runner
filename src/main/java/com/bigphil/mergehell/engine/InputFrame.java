package com.bigphil.mergehell.engine;

public record InputFrame(
        boolean left,
        boolean right,
        boolean jumpPressed,
        boolean shootHeld,
        boolean dashPressed,
        boolean meleePressed) {
    public static final InputFrame NONE = new InputFrame(false, false, false, false, false, false);
}
