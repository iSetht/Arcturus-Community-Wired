package com.eu.habbo.habbohotel.wired.highscores;

import com.eu.habbo.Emulator;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.habbohotel.users.HabboInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WiredHighscoreManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredHighscoreManager.class);

    private final HashMap<Integer, List<WiredHighscoreDataEntry>> data = new HashMap<>();
    
    private final static String locale = (System.getProperty("user.language") != null ? System.getProperty("user.language") : "en");
    private final static String country = (System.getProperty("user.country") != null ? System.getProperty("user.country") : "US");

    private final static DayOfWeek firstDayOfWeek = WeekFields.of(Locale.of(locale, country)).getFirstDayOfWeek();
    private final static DayOfWeek lastDayOfWeek = DayOfWeek.of(((firstDayOfWeek.getValue() + 5) % DayOfWeek.values().length) + 1);
    private final static ZoneId zoneId = ZoneId.systemDefault();

    public static ScheduledFuture<?> midnightUpdater = null;

    public void load() {
        long millis = System.currentTimeMillis();

        this.data.clear();
        this.loadHighscoreData();

        LOGGER.info("Highscore Manager -> Loaded! ({} MS, {} items)", System.currentTimeMillis() - millis, this.data.size());
    }

    @EventHandler
    public static void onEmulatorLoaded(EmulatorLoadedEvent event) {
        if (midnightUpdater != null) {
            midnightUpdater.cancel(true);
        }
        
        midnightUpdater = Emulator.getThreading().run(new WiredHighscoreMidnightUpdater(), WiredHighscoreMidnightUpdater.getNextUpdaterRun());
    }

    public void dispose() {
        if (midnightUpdater != null) {
            midnightUpdater.cancel(true);
        }

        this.data.clear();
    }

    private void loadHighscoreData() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM items_highscore_data")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    WiredHighscoreDataEntry entry = new WiredHighscoreDataEntry(set);

                    if (!this.data.containsKey(entry.getItemId())) {
                        this.data.put(entry.getItemId(), new ArrayList<>());
                    }

                    this.data.get(entry.getItemId()).add(entry);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void addHighscoreData(WiredHighscoreDataEntry entry) {
        if (!this.data.containsKey(entry.getItemId())) {
            this.data.put(entry.getItemId(), new ArrayList<>());
        }

        this.data.get(entry.getItemId()).add(entry);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO `items_highscore_data` (`item_id`, `user_ids`, `score`, `is_win`, `timestamp`) VALUES (?, ?, ?, ?, ?)")) {
            statement.setInt(1, entry.getItemId());
            statement.setString(2, String.join(",", entry.getUserIds().stream().map(Object::toString).collect(Collectors.toList())));
            statement.setInt(3, entry.getScore());
            statement.setInt(4, entry.isWin() ? 1 : 0);
            statement.setInt(5, entry.getTimestamp());

            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public boolean deleteHighscoreRowForItem(int itemId, WiredHighscoreClearType clearType, WiredHighscoreScoreType scoreType, int rowIndex) {
        List<WiredHighscoreRow> rows = this.getHighscoreRowsForItem(itemId, clearType, scoreType);
        if (rows == null || rowIndex < 0 || rowIndex >= rows.size()) return false;

        WiredHighscoreRow row = rows.get(rowIndex);
        List<WiredHighscoreDataEntry> entries = this.data.get(itemId);
        if (entries == null || entries.isEmpty()) return false;

        List<Integer> userIds = new ArrayList<>(row.getUserIds());
        int score = row.getValue();

        List<WiredHighscoreDataEntry> removed = entries.stream()
                .filter(entry -> this.matchesDeletedRow(entry, clearType, scoreType, userIds, score))
                .collect(Collectors.toList());

        if (removed.isEmpty()) return false;

        entries.removeAll(removed);
        if (entries.isEmpty()) {
            this.data.remove(itemId);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM `items_highscore_data` WHERE `item_id` = ? AND `user_ids` = ? AND `score` = ? AND `is_win` = ? AND `timestamp` = ?")) {
            for (WiredHighscoreDataEntry entry : removed) {
                statement.setInt(1, entry.getItemId());
                statement.setString(2, String.join(",", entry.getUserIds().stream().map(Object::toString).collect(Collectors.toList())));
                statement.setInt(3, entry.getScore());
                statement.setInt(4, entry.isWin() ? 1 : 0);
                statement.setInt(5, entry.getTimestamp());
                statement.addBatch();
            }

            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return true;
    }

    public List<WiredHighscoreRow> getHighscoreRowsForItem(int itemId, WiredHighscoreClearType clearType, WiredHighscoreScoreType scoreType) {
        if (!this.data.containsKey(itemId)) return null;

        Stream<WiredHighscoreRow> highscores = new ArrayList<>(this.data.get(itemId)).stream()
                .filter(entry -> this.timeMatchesEntry(entry, clearType) && (scoreType != WiredHighscoreScoreType.MOSTWIN || entry.isWin()))
                .map(this::createHighscoreRow);

        if (scoreType == WiredHighscoreScoreType.CLASSIC) {
            return highscores.sorted(WiredHighscoreRow::compareTo).collect(Collectors.toList());
        }

        if (scoreType == WiredHighscoreScoreType.PERTEAM) {
            return highscores
                    .collect(Collectors.groupingBy(h -> h.getUsers().hashCode()))
                    .entrySet()
                    .stream()
                    .map(e -> e.getValue().stream()
                            .sorted(WiredHighscoreRow::compareTo)
                            .collect(Collectors.toList())
                            .get(0)
                    )
                    .sorted(WiredHighscoreRow::compareTo)
                    .collect(Collectors.toList());
        }

        if (scoreType == WiredHighscoreScoreType.MOSTWIN) {
            return highscores
                    .collect(Collectors.groupingBy(h -> h.getUsers().hashCode()))
                    .entrySet()
                    .stream()
                    .map(e -> new WiredHighscoreRow(e.getValue().get(0).getUsers(), e.getValue().get(0).getLooks(), e.getValue().get(0).getUserIds(), e.getValue().size()))
                    .sorted(WiredHighscoreRow::compareTo)
                    .collect(Collectors.toList());
        }

        return null;
    }

    private WiredHighscoreRow createHighscoreRow(WiredHighscoreDataEntry entry) {
        List<String> users = new ArrayList<>();
        List<String> looks = new ArrayList<>();
        List<Integer> userIds = new ArrayList<>();

        for (Integer userId : entry.getUserIds()) {
            HabboInfo info = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(userId);

            if (info == null) continue;

            users.add(info.getUsername());
            looks.add(info.getLook());
            userIds.add(info.getId());
        }

        return new WiredHighscoreRow(users, looks, userIds, entry.getScore());
    }

    private boolean matchesDeletedRow(WiredHighscoreDataEntry entry, WiredHighscoreClearType clearType, WiredHighscoreScoreType scoreType, List<Integer> userIds, int score) {
        if (!this.timeMatchesEntry(entry, clearType)) return false;
        if (!entry.getUserIds().equals(userIds)) return false;

        if (scoreType == WiredHighscoreScoreType.MOSTWIN) {
            return entry.isWin();
        }

        if (scoreType == WiredHighscoreScoreType.PERTEAM) {
            return true;
        }

        return entry.getScore() == score;
    }

    private boolean timeMatchesEntry(WiredHighscoreDataEntry entry, WiredHighscoreClearType timeType) {
        switch (timeType) {
            case DAILY:
                return entry.getTimestamp() > this.getTodayStartTimestamp() && entry.getTimestamp() < this.getTodayEndTimestamp();
            case WEEKLY:
                return entry.getTimestamp() > this.getWeekStartTimestamp() && entry.getTimestamp() < this.getWeekEndTimestamp();
            case MONTHLY:
                return entry.getTimestamp() > this.getMonthStartTimestamp() && entry.getTimestamp() < this.getMonthEndTimestamp();
            case ALLTIME:
                return true;
        }

        return false;
    }

    public HashMap<Integer, List<WiredHighscoreDataEntry>> getData() {
        return this.data;
    }

    public List<WiredHighscoreDataEntry> getEntriesForItemId(int itemId) {
        return this.data.get(itemId);
    }

    public void setEntriesForItemId(int itemId, List<WiredHighscoreDataEntry> entries) {
        this.data.put(itemId, entries);
    }

    private long getTodayStartTimestamp() {
        return LocalDateTime.now().with(LocalTime.MIDNIGHT).atZone(zoneId).toEpochSecond();
    }

    private long getTodayEndTimestamp() {
        return LocalDateTime.now().with(LocalTime.MIDNIGHT).plusDays(1).plusSeconds(-1).atZone(zoneId).toEpochSecond();
    }

    private long getWeekStartTimestamp() {
        return LocalDateTime.now().with(LocalTime.MIDNIGHT).with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).atZone(zoneId).toEpochSecond();
    }

    private long getWeekEndTimestamp() {
        return LocalDateTime.now().with(LocalTime.MIDNIGHT).plusDays(1).plusSeconds(-1).with(TemporalAdjusters.nextOrSame(lastDayOfWeek)).atZone(zoneId).toEpochSecond();
    }

    private long getMonthStartTimestamp() {
        return LocalDateTime.now().with(LocalTime.MIDNIGHT).with(TemporalAdjusters.firstDayOfMonth()).atZone(zoneId).toEpochSecond();
    }

    private long getMonthEndTimestamp() {
        return LocalDateTime.now().with(LocalTime.MIDNIGHT).plusDays(1).plusSeconds(-1).with(TemporalAdjusters.lastDayOfMonth()).atZone(zoneId).toEpochSecond();
    }
}
