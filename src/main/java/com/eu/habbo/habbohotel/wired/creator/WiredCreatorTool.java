package com.eu.habbo.habbohotel.wired.creator;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredCreatorTool {
    private final int id;
    private final int pageId;
    private final int orderNumber;
    private final String catalogName;
    private final String displayName;
    private final String previewAsset;
    private final Item item;

    public WiredCreatorTool(ResultSet set, Item item) throws SQLException {
        this.id = set.getInt("id");
        this.pageId = set.getInt("page_id");
        this.orderNumber = set.getInt("order_number");
        this.catalogName = set.getString("catalog_name");
        this.displayName = set.getString("display_name");
        this.previewAsset = set.getString("preview_asset");
        this.item = item;
    }

    public int getId() {
        return id;
    }

    public int getPageId() {
        return pageId;
    }

    public int getOrderNumber() {
        return orderNumber;
    }

    public String getCatalogName() {
        return catalogName;
    }

    public String getDisplayName() {
        return (displayName == null || displayName.isEmpty()) ? item.getFullName() : displayName;
    }

    public String getPreviewAsset() {
        return previewAsset == null ? "" : previewAsset;
    }

    public Item getItem() {
        return item;
    }
}
