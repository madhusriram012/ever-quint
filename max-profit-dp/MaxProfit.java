import java.util.Arrays;

public class MaxProfit {
    // Time complexity:  O(n)
    // Space complexity: O(n)
    static final int[] TIME = {5, 4, 10};
    static final int[] RATE = {1500, 1000, 2000};

    static final int THEATER = 0;
    static final int PUB = 1;
    static final int COMMERCIAL_PARK = 2;

    static int[] solve(int n) {
        int[] dp = new int[n + 1];
        int[][] counts = new int[n + 1][3];

        Arrays.fill(dp, -1);
        dp[0] = 0;

        int bestTime = 0;

        for (int currentTime = 0; currentTime <= n; currentTime++) {
            if (dp[currentTime] == -1) {
                continue;
            }

            for (int building = 0; building < 3; building++) {
                int completionTime = currentTime + TIME[building];
                if (completionTime > n) {
                    continue;
                }

                int profit = dp[currentTime] + RATE[building] * (n - completionTime);

                if (profit > dp[completionTime]) {
                    dp[completionTime] = profit;

                    counts[completionTime][THEATER]         = counts[currentTime][THEATER];
                    counts[completionTime][PUB]             = counts[currentTime][PUB];
                    counts[completionTime][COMMERCIAL_PARK] = counts[currentTime][COMMERCIAL_PARK];
                    counts[completionTime][building]++;

                    if (dp[completionTime] > dp[bestTime] || (dp[completionTime] == dp[bestTime] && completionTime < bestTime)) {
                        bestTime = completionTime;
                    }
                }
            }
        }

        return new int[]{dp[bestTime], counts[bestTime][THEATER], counts[bestTime][PUB], counts[bestTime][COMMERCIAL_PARK]};
    }

    static void printResult(int n) {
        int[] result = solve(n);
        System.out.printf("Time Unit: %-3d | Earnings: $%d | T: %d P: %d C: %d%n", n, result[0], result[1], result[2], result[3]);
    }

    public static void main(String[] args) {
        printResult(7);
        printResult(8);
        printResult(13);
    }
}