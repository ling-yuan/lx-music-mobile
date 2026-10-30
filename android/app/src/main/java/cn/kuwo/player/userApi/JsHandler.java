package cn.kuwo.player.userApi;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import java.util.Objects;

public class JsHandler extends Handler {
  private final UtilsEvent utilsEvent;
  private final String sessionId;

  JsHandler(Looper looper, UtilsEvent utilsEvent) {
    this(looper, utilsEvent, null);
  }

  JsHandler(Looper looper, UtilsEvent utilsEvent, String sessionId) {
    super(looper);
    this.utilsEvent = utilsEvent;
    this.sessionId = sessionId;
  }

  private WritableMap createParams() {
    WritableMap params = Arguments.createMap();
    if (sessionId != null) params.putString("sessionId", sessionId);
    return params;
  }

  private void sendInitFailedEvent(String errorMessage) {
    WritableMap params = createParams();
    params.putString("action", "init");
    params.putString("errorMessage", errorMessage);
    params.putString("data", "{ \"info\": null, \"status\": false, \"errorMessage\": \"Create JavaScript Env Failed\" }");
    this.utilsEvent.sendEvent(utilsEvent.API_ACTION, params);
    sendLogEvent(new Object[]{"error", errorMessage});
  }

  private void sendLogEvent(Object[] data) {
    WritableMap params = createParams();
    params.putString("action", "log");
    params.putString("type", (String) data[0]);
    params.putString("log", (String) data[1]);
    this.utilsEvent.sendEvent(utilsEvent.API_ACTION, params);
  }

  private void sendActionEvent(String action, String data) {
    WritableMap params = createParams();
    params.putString("action", action);
    params.putString("data", data);
    this.utilsEvent.sendEvent(utilsEvent.API_ACTION, params);
  }

  @Override
  public void handleMessage(Message msg) {
    switch (msg.what) {
      case HandlerWhat.INIT_SUCCESS: break;
      case HandlerWhat.INIT_FAILED:
        sendInitFailedEvent((String) msg.obj);
        break;
      case HandlerWhat.ACTION:
        Object[] action = (Object[]) msg.obj;
        sendActionEvent((String) action[0], (String) action[1]);
        // Log.d("UserApi [api call]", "action: " + action[0]);
        break;
      case HandlerWhat.LOG:
        sendLogEvent((Object[]) msg.obj);
        break;
      default:
        Log.w("UserApi [api call]", "Unknown message what: " + msg.what);
        break;
    }
  }
}
