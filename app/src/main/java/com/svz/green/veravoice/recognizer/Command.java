package com.svz.green.veravoice.recognizer;

import android.graphics.Color;

/**
 * Created by Green on 22.01.2015.
 */
public class Command {

    private String text = "";
    private int color = Color.BLACK;

    public Command(String text) {
        this.text = text;
    }

    public Command(String text, int color) {
        this.text = text;
        this.color = color;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }
}
