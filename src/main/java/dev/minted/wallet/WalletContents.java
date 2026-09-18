package dev.minted.wallet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a Wallet holds: a count of banknotes per face value, keyed by the amount
 * in cents so whole and odd values never drift through floating point.
 *
 * <p>Capacity mirrors an ordinary inventory - at most {@link #MAX_DENOMS}
 * distinct face values, each stacked up to {@link #MAX_PER_DENOM} notes, so a
 * wallet can never grow past {@link #MAX_NOTES} notes. The contents encode to a
 * small {@code cents:count,...} string that is stored in the wallet item's NBT.
 */
public final class WalletContents {

    public static final int MAX_DENOMS = 54;
    public static final int MAX_PER_DENOM = 64;
    public static final int MAX_NOTES = MAX_DENOMS * MAX_PER_DENOM;

    private final Map<Long, Integer> notes = new LinkedHashMap<Long, Integer>();

    public static WalletContents decode(String data) {
        WalletContents contents = new WalletContents();
        if (data == null || data.isEmpty()) {
            return contents;
        }
        for (String pair : data.split(",")) {
            int colon = pair.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                long cents = Long.parseLong(pair.substring(0, colon));
                int count = Integer.parseInt(pair.substring(colon + 1));
                if (cents > 0 && count > 0) {
                    contents.set(cents, Math.min(count, MAX_PER_DENOM));
                }
            } catch (NumberFormatException ignored) {
                // A tampered wallet just loses that one bogus entry.
            }
        }
        return contents;
    }

    /** @return true when the wallet holds nothing at all */
    public boolean isEmpty() {
        return notes.isEmpty();
    }

    /** @return the total face value, in cents */
    public long valueCents() {
        long total = 0;
        for (Map.Entry<Long, Integer> entry : notes.entrySet()) {
            total += entry.getKey() * entry.getValue();
        }
        return total;
    }

    public int noteCount() {
        int total = 0;
        for (int count : notes.values()) {
            total += count;
        }
        return total;
    }

    /** @return the count of notes held at this face value, or 0 */
    public int count(long cents) {
        Integer count = notes.get(cents);
        return count == null ? 0 : count;
    }

    /**
     * Adds up to {@code count} notes of this face value, honouring the per-value
     * and distinct-value caps.
     *
     * @return how many notes were accepted (may be less than {@code count})
     */
    public int add(long cents, int count) {
        if (cents <= 0 || count <= 0) {
            return 0;
        }
        int held = count(cents);
        int accepted = Math.min(count, MAX_PER_DENOM - held);
        if (accepted <= 0) {
            return 0;
        }
        if (held == 0 && notes.size() >= MAX_DENOMS) {
            return 0;
        }
        set(cents, held + accepted);
        return accepted;
    }

    /** Removes up to {@code count} notes; returns how many were removed. */
    public int remove(long cents, int count) {
        if (cents <= 0 || count <= 0) {
            return 0;
        }
        int held = count(cents);
        int removed = Math.min(count, held);
        if (removed <= 0) {
            return 0;
        }
        int left = held - removed;
        if (left > 0) {
            set(cents, left);
        } else {
            notes.remove(cents);
        }
        return removed;
    }

    /**
     * Plans the cheapest way to gather {@code amountCents} from the held notes:
     * largest face values first, taking one extra note when needed to cross the
     * line, so every value inside a wallet stays spendable and the overshoot is
     * handed back as ordinary change. Nothing is modified unless the wallet can
     * actually cover the amount.
     *
     * @return a full {@link Result} whose {@code taken} is empty when short
     */
    public Result take(long amountCents) {
        if (amountCents <= 0) {
            return Result.covered(Collections.<Long, Integer>emptyMap(), 0);
        }
        if (valueCents() < amountCents) {
            return Result.uncovered();
        }
        List<Long> values = new ArrayList<Long>(notes.keySet());
        Collections.sort(values, new Comparator<Long>() {
            @Override
            public int compare(Long a, Long b) {
                return Long.compare(b, a);
            }
        });
        Map<Long, Integer> taken = new LinkedHashMap<Long, Integer>();
        long collected = 0;
        for (long cents : values) {
            if (collected >= amountCents) {
                break;
            }
            int held = notes.get(cents);
            long clean = Math.min(held, (amountCents - collected) / cents);
            if (clean > 0) {
                taken.put(cents, (int) clean);
                collected += clean * cents;
            }
            if (collected < amountCents && held > clean) {
                taken.put(cents, (int) clean + 1);
                collected += cents;
            }
        }
        for (Map.Entry<Long, Integer> entry : taken.entrySet()) {
            remove(entry.getKey(), entry.getValue());
        }
        return Result.covered(taken, collected - amountCents);
    }

    /** @return a detached copy of the contents (safe to iterate while mutating) */
    public Map<Long, Integer> snapshot() {
        return new LinkedHashMap<Long, Integer>(notes);
    }

    /** @return the contents as a {@code cents:count,...} string for NBT storage */
    public String encode() {
        if (notes.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Long, Integer> entry : notes.entrySet()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return builder.toString();
    }

    private void set(long cents, int count) {
        if (count <= 0) {
            notes.remove(cents);
        } else {
            notes.put(cents, count);
        }
    }

    /** The outcome of a {@link #take}: what was removed, and the overshoot change. */
    public static final class Result {
        public final Map<Long, Integer> taken;
        public final long changeCents;
        public final boolean covered;

        private Result(Map<Long, Integer> taken, long changeCents, boolean covered) {
            this.taken = taken;
            this.changeCents = changeCents;
            this.covered = covered;
        }

        private static Result covered(Map<Long, Integer> taken, long changeCents) {
            return new Result(taken, changeCents, true);
        }

        private static Result uncovered() {
            return new Result(Collections.<Long, Integer>emptyMap(), 0, false);
        }
    }
}