package cn.kuwo.player.lyric;

import android.view.Gravity;
import android.util.TypedValue;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;

public class KaraokeViewManager extends SimpleViewManager<KaraokeTextView> {
  @Override public String getName() { return "LXKaraokeText"; }

  @Override protected KaraokeTextView createViewInstance(ThemedReactContext context) {
    KaraokeTextView view = new KaraokeTextView(context);
    view.setIncludeFontPadding(true);
    view.setPadding(0, 0, 0, 0);
    view.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
    return view;
  }

  @ReactProp(name = "text") public void setText(KaraokeTextView view, String text) { view.setText(text); }
  @ReactProp(name = "lyricTime") public void setTime(KaraokeTextView view, double time) { view.setLyricTime((int) time); }
  @ReactProp(name = "active") public void setActive(KaraokeTextView view, boolean active) { view.setActive(active); }
  @ReactProp(name = "playedColor", customType = "Color") public void setPlayedColor(KaraokeTextView view, Integer color) {
    view.setKaraokeColors(0xff888888, color == null ? 0xff07c556 : color);
  }
  @ReactProp(name = "fontSize") public void setFontSize(KaraokeTextView view, float size) { view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size); }
  @ReactProp(name = "lineHeight") public void setLineHeight(KaraokeTextView view, float height) {
    view.setDesiredLineHeight(height);
  }
  @ReactProp(name = "align") public void setAlign(KaraokeTextView view, String align) {
    view.setGravity(Gravity.TOP | ("center".equals(align) ? Gravity.CENTER_HORIZONTAL : "right".equals(align) ? Gravity.RIGHT : Gravity.LEFT));
  }
}