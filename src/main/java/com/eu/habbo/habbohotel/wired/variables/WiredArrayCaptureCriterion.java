package com.eu.habbo.habbohotel.wired.variables;

/** Versioned saved row for one Array Entry Capturer search criterion. */
public final class WiredArrayCaptureCriterion {
    public int fieldId = WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID;
    public int comparison = 2;
    public WiredArrayFieldInput reference = new WiredArrayFieldInput();

    public WiredArrayCaptureCriterion() {
    }
}
