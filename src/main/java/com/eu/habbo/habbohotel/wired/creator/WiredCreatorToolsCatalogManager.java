package com.eu.habbo.habbohotel.wired.creator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WiredCreatorToolsCatalogManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredCreatorToolsCatalogManager.class);

    private final Map<Integer, List<WiredCreatorTool>> toolsByPage = new LinkedHashMap<>();
    private final Map<Integer, WiredCreatorTool> toolsById = new LinkedHashMap<>();

    public WiredCreatorToolsCatalogManager() {
        this.reload();
    }

    public synchronized void reload() {
        this.toolsByPage.clear();
        this.toolsById.clear();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM wired_tools WHERE enabled = 1 ORDER BY page_id ASC, order_number ASC, id ASC");
             ResultSet set = statement.executeQuery()) {

            while (set.next()) {
                Item item = Emulator.getGameEnvironment().getItemManager().getItem(set.getString("catalog_name"));

                if (item == null) {
                    LOGGER.warn("Skipping wired_tools row {} because catalog_name '{}' does not exist in items_base.", set.getInt("id"), set.getString("catalog_name"));
                    continue;
                }

                WiredCreatorTool tool = new WiredCreatorTool(set, item);

                this.toolsById.put(tool.getId(), tool);
                this.toolsByPage.computeIfAbsent(tool.getPageId(), pageId -> new ArrayList<>()).add(tool);
            }

            for (List<WiredCreatorTool> tools : this.toolsByPage.values()) {
                tools.sort(Comparator.comparingInt(WiredCreatorTool::getOrderNumber).thenComparingInt(WiredCreatorTool::getId));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while loading wired_tools", e);
        } catch (Exception e) {
            LOGGER.error("Caught exception while loading wired_tools", e);
        }
    }

    public synchronized Collection<Integer> getPageIds() {
        return new ArrayList<>(this.toolsByPage.keySet());
    }

    public synchronized List<WiredCreatorTool> getToolsForPage(int pageId) {
        return this.toolsByPage.containsKey(pageId)
                ? new ArrayList<>(this.toolsByPage.get(pageId))
                : Collections.emptyList();
    }

    public synchronized WiredCreatorTool getTool(int toolId) {
        return this.toolsById.get(toolId);
    }

    public synchronized boolean isWiredToolItem(int baseItemId) {
        for (WiredCreatorTool tool : this.toolsById.values()) {
            if (tool.getItem().getId() == baseItemId) {
                return true;
            }
        }

        return false;
    }
}
