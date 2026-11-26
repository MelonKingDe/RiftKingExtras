package de.example.riftkingextras;

import java.util.UUID;

/**
 * The collection race: you against a rival (a player, or the number 1) in one collection, with a deadline. The data of
 * both comes from Elite every 5 minutes; your own amount is live if the collection is also tracked in the tracker.
 */
final class CollectionRace {
    /** What the last update found. [error] is set if it failed; the older values stay then. */
    record View(String title, String boardId, long mine, int myRank, String rivalName, long rivalAmount, int rivalRank,
                long updatedMs, String error) {}

    private static volatile View view = null;

    private CollectionRace() {}

    static View view() {
        return view;
    }

    /** Asks Elite (background thread). */
    static void refresh(UUID me, String myName) {
        View old = view;
        try {
            EliteDirectory.Board board = EliteDirectory.findCollection(RaceConfig.collection);
            EliteDirectory.Entry mine = EliteDirectory.rankOf(board.id(), me, myName);

            String rivalName = RaceConfig.rival.trim();
            EliteDirectory.Entry rival;
            if (rivalName.isEmpty() || rivalName.equalsIgnoreCase("top")) {
                rival = EliteDirectory.top1(board.id());
            } else {
                rival = EliteDirectory.findInTop(board.id(), rivalName, 200);
                if (rival == null) rival = EliteDirectory.rankOf(board.id(), EliteDirectory.uuidOf(rivalName), rivalName);
            }
            String title = board.collection() != null ? board.collection() : board.title() != null ? board.title() : board.id();
            view = new View(title, board.id(), Math.round(mine.amount()), mine.rank(), rival.ign(),
                    Math.round(rival.amount()), rival.rank(), System.currentTimeMillis(), null);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            view = old == null
                    ? new View(RaceConfig.collection, "", -1, 0, RaceConfig.rival, -1, 0, 0, message)
                    : new View(old.title(), old.boardId(), old.mine(), old.myRank(), old.rivalName(), old.rivalAmount(),
                            old.rivalRank(), old.updatedMs(), message);
        }
    }
}
