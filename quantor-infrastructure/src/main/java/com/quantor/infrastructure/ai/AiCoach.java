package com.quantor.infrastructure.ai;

import com.quantor.domain.ai.AiStatsTracker;
import com.quantor.domain.strategy.Strategy;
import com.quantor.exchange.ChatGptClient;

public class AiCoach {

    private final ChatGptClient gpt;
    private final AiStatsTracker stats;

    public AiCoach(ChatGptClient gpt, AiStatsTracker stats) {
        this.gpt = gpt;
        this.stats = stats;
    }

    /** Overall review of all trading activity */
    public String review() throws Exception {
        String s = stats.buildStatsForPrompt();
        String prompt =
                "You are a professional trading coach. Here is the trader's statistics:\n" +
                        s + "\n" +
                        "Provide a short, business-style analysis without fluff. " +
                        "What is good, what is bad, and what 1–3 strategy improvements would you recommend?";
        return gpt.sendPrompt(prompt);
    }

    /** Review of a specific trade */
    public String reviewTrade(int index) throws Exception {
        var list = stats.getTrades();
        if (index < 0 || index >= list.size()) {
            return "❌ No trade with this index.";
        }

        var t = list.get(index);

        String prompt =
                "You are a professional trading coach. Analyze the following trade:\n" +
                        "pnl=" + t.pnl + ", equityAfter=" + t.equityAfter + "\n" +
                        "Explain possible reasons for the outcome and how entry/exit could be improved.";
        return gpt.sendPrompt(prompt);
    }

    /** Risk check: should real trading be disabled */
    public boolean shouldDisableRealTrading() throws Exception {
        String statsStr = stats.buildStatsForPrompt();

        String prompt = """
        You are a risk manager for an algorithmic trading bot.
        Based on the statistics, assess the current risk level.
        Respond with exactly ONE word: SUSPEND or CONTINUE.
        
        Statistics:
        """ + statsStr;

        String resp = gpt.sendPrompt(prompt).trim().toUpperCase();
        return resp.contains("SUSPEND");
    }

    /** Strategy tuning recommendations (for /ai_tune and auto-/ai_tune) */
    public String tuneStrategy(String currentParams) throws Exception {
        String statsStr = stats.buildStatsForPrompt();

        String prompt =
                "You are a professional trading coach and quant.\n" +
                        "Here is aggregated trading statistics:\n" +
                        statsStr + "\n\n" +
                        "Here is a brief description of the current strategy parameters and bot operating mode:\n" +
                        currentParams + "\n\n" +
                        "Provide clear, practical strategy tuning recommendations " +
                        "(adjusting stops, takes, position size, EMA parameters, trend filters, etc.). " +
                        "Structure the answer as 5–10 short bullet points, concise and to the point.";

        return gpt.sendPrompt(prompt);
    }
}