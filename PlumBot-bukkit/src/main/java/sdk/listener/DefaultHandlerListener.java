package sdk.listener;

import sdk.listener.handler.Handler;

import java.util.HashMap;
import java.util.Map;

/**
 * 增加处理器分发功能
 * 一般给私聊或者群聊消息使用
 *
 * @param <T>
 */
public abstract class DefaultHandlerListener<T> extends SimpleListener<T> {
    protected Map<String, Handler<T>> handlerMap = new HashMap<>();

    public Map<String, Handler<T>> getHandlerMap() {
        return handlerMap;
    }

    public void setHandlerMap(Map<String, Handler<T>> handlerMap) {
        this.handlerMap = handlerMap;
    }

    public void addHandler(String key, Handler<T> handler) {
        handlerMap.put(key, handler);
    }

    public void removeHandler(String key) {
        handlerMap.remove(key);
    }

    public Handler<T> getHandler(String key) {
        return handlerMap.get(key);
    }

    public Boolean contains(String key) {
        return handlerMap.containsKey(key);
    }

}
