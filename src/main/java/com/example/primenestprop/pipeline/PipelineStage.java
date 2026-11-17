package com.example.primenestprop.pipeline;

/** Mandate pipeline, in order. LOST can be reached from any open stage. */
public enum PipelineStage {
    PROSPECT,
    MEETING,
    INSPECTION,
    MANDATE_SIGNED,
    MARKETING,
    VIEWINGS,
    OFFER,
    CONVEYANCING,
    COMPLETED,
    LOST;

    public boolean isClosed() {
        return this == COMPLETED || this == LOST;
    }
}
