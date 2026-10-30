package cn.kuwo.player.lyric;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.text.Layout;
import android.view.Gravity;
import android.widget.TextView;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@android.annotation.SuppressLint("AppCompatCustomView")
public class KaraokeTextView extends TextView {
  private static final Set<KaraokeTextView> views = Collections.newSetFromMap(new WeakHashMap<>());
  private final Path highlight = new Path();
  private int lyricTime = -1;
  private int playedColor;
  private int unplayedColor = 0xff888888;
  private boolean active = true;
  private boolean followWord;
  private float desiredLineHeight;

  public KaraokeTextView(Context context) {
    super(context);
  }

  public static void refreshViews() {
    synchronized (views) {
      for (KaraokeTextView view : views) view.postInvalidate();
    }
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    synchronized (views) { views.add(this); }
    invalidate();
  }

  @Override protected void onDetachedFromWindow() {
    synchronized (views) { views.remove(this); }
    super.onDetachedFromWindow();
  }

  public void setLyricTime(int time) { lyricTime = time; invalidate(); }
  public void setActive(boolean value) { active = value; invalidate(); }
  public void setFollowWord(boolean value) { followWord = value; }
  public void setDesiredLineHeight(float height) {
    desiredLineHeight = height;
    updateLineHeight();
  }

  @Override public void setTextSize(int unit, float size) {
    super.setTextSize(unit, size);
    updateLineHeight();
  }

  private void updateLineHeight() {
    if (desiredLineHeight > 0) setLineSpacing(desiredLineHeight - getPaint().getFontMetricsInt(null), 1);
  }
  public void setKaraokeColors(int unplayed, int played) {
    unplayedColor = unplayed;
    playedColor = played;
    super.setTextColor(played);
    invalidate();
  }

  @Override protected void onTextChanged(CharSequence text, int start, int before, int count) {
    super.onTextChanged(text, start, before, count);
    invalidate();
  }

  protected boolean hasKaraoke() {
    return active && KaraokeTiming.getLine(lyricTime, getText().toString()) != null;
  }

  @Override protected void onDraw(Canvas canvas) {
    KaraokeTiming.Line timing = active ? KaraokeTiming.getLine(lyricTime, getText().toString()) : null;
    Layout layout = getLayout();
    if (timing == null || layout == null) {
      super.onDraw(canvas);
      return;
    }
    double time = KaraokeTiming.getTime();
    highlight.reset();
    float cursor = 0;
    boolean unfinished = false;
    for (KaraokeTiming.Word word : timing.words) {
      float progress = word.progress(time);
      if (progress < 1) unfinished = true;
      if (progress <= 0) continue;
      int firstLine = layout.getLineForOffset(word.from);
      int lastLine = layout.getLineForOffset(word.to - 1);
      float totalWidth = 0;
      for (int line = firstLine; line <= lastLine; line++) {
        int from = Math.max(word.from, layout.getLineStart(line));
        int to = Math.min(word.to, layout.getLineEnd(line));
        totalWidth += Math.abs(horizontal(layout, line, to) - horizontal(layout, line, from));
      }
      float remaining = totalWidth * progress;
      for (int line = firstLine; line <= lastLine && remaining > 0; line++) {
        int from = Math.max(word.from, layout.getLineStart(line));
        int to = Math.min(word.to, layout.getLineEnd(line));
        float left = horizontal(layout, line, from), right = horizontal(layout, line, to);
        float width = Math.abs(right - left);
        float fill = Math.min(width, remaining);
        float end = left + Math.signum(right - left) * fill;
        highlight.addRect(Math.min(left, end), layout.getLineTop(line), Math.max(left, end), layout.getLineBottom(line), Path.Direction.CW);
        cursor = end;
        remaining -= fill;
      }
    }
    int saved = canvas.save();
    int availableHeight = getHeight() - getCompoundPaddingTop() - getCompoundPaddingBottom();
    int verticalGravity = getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
    int verticalOffset = verticalGravity == Gravity.BOTTOM ? availableHeight - layout.getHeight()
      : verticalGravity == Gravity.CENTER_VERTICAL ? (availableHeight - layout.getHeight()) / 2 : 0;
    float scroll = followWord ? Math.max(0, Math.min(cursor - getWidth() * 0.55f, layout.getLineWidth(0) - getWidth())) : 0;
    canvas.translate(getCompoundPaddingLeft() - scroll, getCompoundPaddingTop() + Math.max(0, verticalOffset));
    int originalColor = getPaint().getColor();
    getPaint().setColor(unplayedColor);
    layout.draw(canvas);
    canvas.clipPath(highlight);
    getPaint().setColor(playedColor);
    layout.draw(canvas);
    getPaint().setColor(originalColor);
    canvas.restoreToCount(saved);
    if (unfinished && KaraokeTiming.isPlaying() && isShown()) postInvalidateOnAnimation();
  }

  private float horizontal(Layout layout, int line, int offset) {
    if (offset >= layout.getLineEnd(line)) return layout.getLineRight(line);
    return layout.getPrimaryHorizontal(offset);
  }
}