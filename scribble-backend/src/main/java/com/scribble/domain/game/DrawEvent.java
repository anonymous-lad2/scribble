package com.scribble.domain.game;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawEvent implements Serializable {

    public enum Type {
        PATH,   // a stroke segment from (x0,y0) to (x1,y1)
        FILL,   // bucket fill at (x0,y0)
        CLEAR,  // clear the entire canvas
        UNDO    // undo last stroke
    }

    private Type type;

    // Coordinates - normalized 0.0 to 1.0 so canvas size doesn't matter
    private float x0;
    private float y0;
    private float x1;
    private float y1;

    private String color;    // hex e.g. "#FF0000"
    private int brushSize;   // 1-40
    private long timestamp;  // System.currentTimeMillis() - used for ordering relays
}
