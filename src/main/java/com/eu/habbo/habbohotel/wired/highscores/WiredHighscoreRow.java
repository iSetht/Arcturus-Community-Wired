package com.eu.habbo.habbohotel.wired.highscores;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class WiredHighscoreRow implements Comparable<WiredHighscoreRow> {
    public static final Comparator<WiredHighscoreRow> COMPARATOR = Comparator.comparing(WiredHighscoreRow::getValue).reversed();

    private final List<String> users;
    private final List<String> looks;
    private final List<Integer> userIds;
    private final int value;

    public WiredHighscoreRow(List<String> users, List<String> looks, List<Integer> userIds, int value) {
        List<Integer> sortedIndexes = IntStream.range(0, users.size())
                .boxed()
                .sorted(Comparator.comparing(users::get, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        this.users = sortedIndexes.stream().map(users::get).collect(Collectors.toList());
        this.looks = sortedIndexes.stream().map(index -> index < looks.size() ? looks.get(index) : "").collect(Collectors.toList());
        this.userIds = sortedIndexes.stream().map(index -> index < userIds.size() ? userIds.get(index) : 0).collect(Collectors.toList());
        this.value = value;
    }

    public List<String> getUsers() {
        return users;
    }

    public List<String> getLooks() {
        return looks;
    }

    public List<Integer> getUserIds() {
        return userIds;
    }

    public int getValue() {
        return value;
    }

    @Override
    public int compareTo(WiredHighscoreRow otherRow) {
        return COMPARATOR.compare(this, otherRow);
    }
}
