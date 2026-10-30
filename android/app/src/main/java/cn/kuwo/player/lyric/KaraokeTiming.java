package cn.kuwo.player.lyric;

import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KaraokeTiming {
  private static final Pattern LINE = Pattern.compile("^\\[(\\d+):(\\d+)(?:\\.(\\d+))?\\](.*)$");
  private static final Pattern WORD = Pattern.compile("<(-?\\d+),(-?\\d+)(?:,-?\\d+)?>");
  private static final Pattern OFFSET = Pattern.compile("\\[offset:\\s*(-?\\d+)\\]", Pattern.CASE_INSENSITIVE);
  private static Map<Integer, Line> lines = Collections.emptyMap();
  private static double position;
  private static long anchor;
  private static double rate = 1;
  private static boolean playing;
  private static int lyricOffset;

  public static final class Word {
    public final int from, to, start, duration;
    Word(int from, int to, int start, int duration) {
      this.from = from;
      this.to = to;
      this.start = start;
      this.duration = duration;
    }
    public float progress(double time) {
      if (time < start) return 0;
      if (duration == 0) return 1;
      return (float) Math.max(0, Math.min(1, (time - start) / duration));
    }
  }

  public static final class Line {
    public final String text;
    public final List<Word> words;
    Line(String text, List<Word> words) {
      this.text = text;
      this.words = Collections.unmodifiableList(words);
    }
  }

  public static Map<Integer, Line> parse(String lyric) {
    Map<Integer, Line> result = new HashMap<>();
    for (String rawLine : lyric.split("\\r\\n|\\n|\\r")) {
      Matcher lineMatch = LINE.matcher(rawLine.trim());
      if (!lineMatch.matches()) continue;
      try {
        String fraction = lineMatch.group(3);
        int milliseconds = fraction == null ? 0 : Integer.parseInt((fraction + "000").substring(0, 3));
        int time = Math.addExact(Math.multiplyExact(Integer.parseInt(lineMatch.group(1)), 60000),
          Math.addExact(Math.multiplyExact(Integer.parseInt(lineMatch.group(2)), 1000), milliseconds));
        String content = lineMatch.group(4);
        Matcher wordMatch = WORD.matcher(content);
        StringBuilder text = new StringBuilder();
        List<Word> words = new ArrayList<>();
        int previousEnd = 0, start = 0, duration = 0;
        boolean hasWord = false;
        while (wordMatch.find()) {
          String part = content.substring(previousEnd, wordMatch.start());
          int from = text.length();
          text.append(part);
          if (hasWord && !part.isEmpty()) words.add(new Word(from, text.length(), start, duration));
          start = Math.addExact(time, Math.max(0, Integer.parseInt(wordMatch.group(1))));
          duration = Math.max(0, Integer.parseInt(wordMatch.group(2)));
          previousEnd = wordMatch.end();
          hasWord = true;
        }
        int from = text.length();
        text.append(content.substring(previousEnd));
        if (hasWord && text.length() > from) words.add(new Word(from, text.length(), start, duration));
        String fullText = text.toString();
        String trimmed = fullText.trim();
        int leading = fullText.indexOf(trimmed);
        List<Word> trimmedWords = new ArrayList<>();
        for (Word word : words) {
          int wordFrom = Math.max(0, word.from - leading);
          int wordTo = Math.min(trimmed.length(), word.to - leading);
          if (wordTo > wordFrom) trimmedWords.add(new Word(wordFrom, wordTo, word.start, word.duration));
        }
        if (!trimmedWords.isEmpty()) result.put(time, new Line(trimmed, trimmedWords));
      } catch (ArithmeticException | NumberFormatException ignored) {
        // Malformed timing must not disable ordinary line lyrics.
      }
    }
    return result;
  }

  public static synchronized void setLyric(String lyric) {
    lines = parse(lyric);
    lyricOffset = 0;
    Matcher matcher = OFFSET.matcher(lyric);
    if (matcher.find()) {
      try { lyricOffset = Integer.parseInt(matcher.group(1)); } catch (NumberFormatException ignored) {}
    }
  }

  public static synchronized Line getLine(int time, String text) {
    Line line = lines.get(time);
    return line != null && text.startsWith(line.text) ? line : null;
  }

  public static synchronized void sync(double time, boolean isPlaying, double playbackRate) {
    position = Math.max(0, time);
    anchor = SystemClock.elapsedRealtime();
    playing = isPlaying;
    rate = Math.max(0.01, playbackRate);
  }

  public static synchronized double getTime() {
    return position + (playing ? (SystemClock.elapsedRealtime() - anchor) * rate : 0) + lyricOffset;
  }

  public static synchronized boolean isPlaying() { return playing; }

  public static synchronized boolean hasLines() { return !lines.isEmpty(); }

  public static synchronized void freeze() {
    position += playing ? (SystemClock.elapsedRealtime() - anchor) * rate : 0;
    anchor = SystemClock.elapsedRealtime();
    playing = false;
  }
}