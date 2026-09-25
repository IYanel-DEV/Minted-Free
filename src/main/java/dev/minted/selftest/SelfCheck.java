package dev.minted.selftest;

/**
 * One named self-test. The same catalogue runs in two places: headless during
 * the build ({@code mvn test}) and live on a running server
 * ({@code /minted selftest}), so "correct" is defined exactly once.
 */
public interface SelfCheck {

    String name();

    Outcome run();

    /** A check body: throws {@link AssertionError} (or anything) to fail. */
    interface Body {
        void run() throws Exception;
    }

    /** Thrown by a check body to mark itself skipped instead of failed. */
    final class SkipSignal extends RuntimeException {
        SkipSignal(String why) {
            super(why);
        }
    }

    /** Wraps a body so it can never take the caller down with it. */
    static SelfCheck of(final String name, final Body body) {
        return new SelfCheck() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Outcome run() {
                try {
                    body.run();
                    return Outcome.pass();
                } catch (SkipSignal skipped) {
                    return Outcome.skip(skipped.getMessage());
                } catch (AssertionError failed) {
                    return Outcome.fail(failed.getMessage());
                } catch (Throwable broken) {
                    return Outcome.fail(broken.getClass().getSimpleName()
                            + (broken.getMessage() == null ? "" : ": " + broken.getMessage()));
                }
            }
        };
    }

    /** Pass, skip (not applicable here) or fail - never an exception. */
    final class Outcome {
        private static final Outcome PASSED = new Outcome(true, false, null);

        private final boolean passed;
        private final boolean skipped;
        private final String detail;

        private Outcome(boolean passed, boolean skipped, String detail) {
            this.passed = passed;
            this.skipped = skipped;
            this.detail = detail;
        }

        public static Outcome pass() {
            return PASSED;
        }

        public static Outcome skip(String why) {
            return new Outcome(false, true, why);
        }

        public static Outcome fail(String why) {
            return new Outcome(false, false, why == null || why.isEmpty() ? "failed" : why);
        }

        public boolean isPassed() {
            return passed;
        }

        public boolean isSkipped() {
            return skipped;
        }

        public String detail() {
            return detail;
        }
    }
}