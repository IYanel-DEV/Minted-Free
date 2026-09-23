package dev.minted.auction.storage;

import dev.minted.auction.AuctionItem;
import dev.minted.auction.AuctionState;
import dev.minted.backend.HikariPool;
import dev.minted.backend.SqlDialect;
import dev.minted.backend.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * All SQL for the auction tables.
 */
public final class AuctionDao {

    private final HikariPool pool;
    private final SqlDialect dialect;

    public AuctionDao(HikariPool pool, SqlDialect dialect) {
        this.pool = pool;
        this.dialect = dialect;
    }

    public void createTables() {
        try (Connection connection = pool.start().getConnection()) {
            exec(connection, dialect.createAuctions());
            exec(connection, dialect.createAuctionBids());
        } catch (SQLException e) {
            throw new StorageException("Could not create auction tables", e);
        }
    }

    public List<AuctionItem> loadAuctions() {
        List<AuctionItem> auctions = new ArrayList<>();
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, seller, item, start_price, buyout_price, ends_at, created_at, "
                     + "current_bid, highest_bidder, bid_count, state FROM auctions");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                auctions.add(mapRow(rows));
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load auctions", e);
        }
        return auctions;
    }

    private AuctionItem mapRow(ResultSet rows) throws SQLException {
        AuctionItem auction = new AuctionItem(
                rows.getLong(1),
                UUID.fromString(rows.getString(2)),
                dev.minted.shop.ItemCodec.decode(rows.getString(3)),
                rows.getDouble(4),
                rows.getDouble(5),
                rows.getLong(6),
                rows.getLong(7)
        );
        auction.setCurrentBid(rows.getDouble(8));
        String highestBidderStr = rows.getString(9);
        if (highestBidderStr != null) {
            auction.setHighestBidder(UUID.fromString(highestBidderStr));
        }
        auction.incrementBidCount(); // Will be overwritten by actual count
        // We need to set bidCount properly, but the constructor sets it to 0
        // Let me fix this - the row has bid_count at position 10
        try {
            java.lang.reflect.Field field = AuctionItem.class.getDeclaredField("bidCount");
            field.setAccessible(true);
            field.setInt(auction, rows.getInt(10));
        } catch (Exception ignored) {
        }
        auction.setState(AuctionState.valueOf(rows.getString(11)));
        return auction;
    }

    public void insertAuction(AuctionItem auction) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO auctions(id, seller, item, start_price, buyout_price, ends_at, created_at, "
                     + "current_bid, highest_bidder, bid_count, state) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, auction.getId());
            statement.setString(2, auction.getSeller().toString());
            statement.setString(3, dev.minted.shop.ItemCodec.encode(auction.getRawItem()));
            statement.setDouble(4, auction.getStartPrice());
            statement.setDouble(5, auction.getBuyoutPrice());
            statement.setLong(6, auction.getEndsAt());
            statement.setLong(7, auction.getCreatedAt());
            statement.setDouble(8, auction.getCurrentBid());
            statement.setString(9, auction.getHighestBidder() != null ? auction.getHighestBidder().toString() : null);
            statement.setInt(10, auction.getBidCount());
            statement.setString(11, auction.getState().name());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not insert auction", e);
        }
    }

    public void updateAuction(AuctionItem auction) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE auctions SET current_bid = ?, highest_bidder = ?, bid_count = ?, state = ? WHERE id = ?")) {
            statement.setDouble(1, auction.getCurrentBid());
            statement.setString(2, auction.getHighestBidder() != null ? auction.getHighestBidder().toString() : null);
            statement.setInt(3, auction.getBidCount());
            statement.setString(4, auction.getState().name());
            statement.setLong(5, auction.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not update auction " + auction.getId(), e);
        }
    }

    public void deleteAuction(long id) {
        try (Connection connection = pool.start().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM auction_bids WHERE auction_id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM auctions WHERE id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("Could not delete auction " + id, e);
        }
    }

    public void insertBid(long auctionId, UUID bidder, double amount, long timestamp) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO auction_bids(auction_id, bidder, amount, timestamp) VALUES(?, ?, ?, ?)")) {
            statement.setLong(1, auctionId);
            statement.setString(2, bidder.toString());
            statement.setDouble(3, amount);
            statement.setLong(4, timestamp);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not insert bid for auction " + auctionId, e);
        }
    }

    public List<AuctionBidRow> loadBids(long auctionId) {
        List<AuctionBidRow> bids = new ArrayList<>();
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT bidder, amount, timestamp FROM auction_bids WHERE auction_id = ? ORDER BY timestamp");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                bids.add(new AuctionBidRow(
                        UUID.fromString(rows.getString(1)),
                        rows.getDouble(2),
                        rows.getLong(3)
                ));
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load bids for auction " + auctionId, e);
        }
        return bids;
    }

    private void exec(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}