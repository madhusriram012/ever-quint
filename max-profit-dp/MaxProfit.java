/*
Earlier, my solution used DP to compute maximum profit but tracked only a single
building combination (T, P, C) per time state. This caused loss of valid solutions
when multiple combinations yielded the same maximum profit (e.g., Time = 7 where
both Theatre and Pub give equal profit).

So to fix that my updated solution separates the problem into two phases:
1) DP is used only to compute the maximum profit for each time.
2) A DFS backtracking step is introduced to reconstruct ALL combinations that
achieve this maximum profit by following only optimal transitions.
*/

import java.util.*;
/*
Time:  O(n) for dp + O(paths) for DFS
Space: O(n) for dp + O(K) for solutions
*/

public class MaxProfit {
    static final int[] TIME = {5, 4, 10};
    static final int[] RATE = {1500, 1000, 2000};

    static int[] dp;
    static int n, maxProfit;
    static List<int[]> solutions;

    static void dfs(int t, int[] counts, int lastBuilding) {
        if (dp[t] == maxProfit) solutions.add(counts.clone());
        for (int b = lastBuilding; b < 3; b++) {
            int next = t + TIME[b];
            if (next > n || n - next == 0) continue;
            if (dp[next] == dp[t] + RATE[b] * (n - next)) {
                counts[b]++;
                dfs(next, counts, b);
                counts[b]--;
            }
        }
    }

    static void printResult(int time) {
        n = time;
        dp = new int[n + 1];
        Arrays.fill(dp, -1);
        dp[0] = 0;
        maxProfit = 0;

        for (int t = 0; t <= n; t++) {
            if (dp[t] == -1) continue;
            maxProfit = Math.max(maxProfit, dp[t]);
            for (int b = 0; b < 3; b++) {
                int next = t + TIME[b];
                if (next > n) continue;
                dp[next] = Math.max(dp[next], dp[t] + RATE[b] * (n - next));
            }
        }

        solutions = new ArrayList<>();
        dfs(0, new int[3], 0);

        System.out.println("Earnings: $" + maxProfit);
        System.out.println("Solutions");
        for (int i = 0; i < solutions.size(); i++) {
            int[] c = solutions.get(i);
            System.out.printf("%d. T: %d P: %d C: %d%n", i + 1, c[0], c[1], c[2]);
        }
    }

    public static void main(String[] args) {
        printResult(7);
        printResult(8);
        printResult(13);
        printResult(49);
    }
}