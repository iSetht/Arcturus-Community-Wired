package com.eu.habbo.habbohotel.wired.variables;

import java.util.Locale;

/**
 * Strict parser for generated captured values. Metadata uses
 * {@code @array.context_variable.found|index}; creator fields and the direct,
 * read-only index projection use {@code context_variable.field|index}.
 */
public final class WiredArrayCapturePath {
    public static final String ROOT = "@array";
    public static final String FOUND = "found";
    public static final String INDEX = "index";

    public final String alias;
    public final String fieldName;
    private final boolean arrayNamespace;

    private WiredArrayCapturePath(String alias, String fieldName, boolean arrayNamespace) {
        this.alias = alias;
        this.fieldName = fieldName;
        this.arrayNamespace = arrayNamespace;
    }

    public static WiredArrayCapturePath parse(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        String prefix = ROOT + ".";
        boolean arrayNamespace = normalized.startsWith(prefix);
        String remainder = arrayNamespace
                ? normalized.substring(prefix.length())
                : normalized;

        int separator = remainder.indexOf('.');
        if (separator <= 0 || separator == remainder.length() - 1 ||
                remainder.indexOf('.', separator + 1) >= 0) return null;

        String alias = remainder.substring(0, separator);
        String field = remainder.substring(separator + 1);
        if (!WiredVariableName.isValid(alias) || !WiredVariableName.isValid(field)) return null;
        return new WiredArrayCapturePath(alias, field, arrayNamespace);
    }

    public static String fieldPath(String alias, String fieldName) {
        return alias + "." + fieldName;
    }

    public static String metadataPath(String alias, String fieldName) {
        return ROOT + "." + alias + "." + fieldName;
    }

    public boolean isMetadata() {
        return this.arrayNamespace &&
                (FOUND.equals(this.fieldName) || INDEX.equals(this.fieldName));
    }

    public boolean isIndex() {
        return INDEX.equals(this.fieldName);
    }

    public boolean isArrayNamespace() {
        return this.arrayNamespace;
    }
}
