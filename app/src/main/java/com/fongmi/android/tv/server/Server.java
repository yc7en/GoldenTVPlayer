package com.fongmi.android.tv.server;

import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.Proxy;
import com.github.catvod.utils.Task;
import com.github.catvod.utils.Util;

public class Server {

    public static final String LOOPBACK = "127.0.0.1";

    private volatile PlaybackService service;
    private volatile Nano nano;

    private static class Loader {
        static volatile Server INSTANCE = new Server();
    }

    public static Server get() {
        return Loader.INSTANCE;
    }

    public PlaybackService getService() {
        return service;
    }

    public void setService(PlaybackService service) {
        this.service = service;
    }

    public String getAddress() {
        return getAddress(false);
    }

    public String getPageAddress(String tab) {
        String url = getAddress(false) + "/?tab=" + tab;
        return Setting.isLanServer() ? url + "&code=" + Setting.getPairCode() : url;
    }

    public String getAddress(String path) {
        return getAddress(true) + path;
    }

    public String getAddress(boolean local) {
        return "http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + Proxy.getPort();
    }

    public synchronized boolean start() {
        if (nano != null) return true;
        return start(Setting.isLanServer());
    }

    public synchronized boolean startLan() {
        Setting.putLanServer(true);
        if (nano != null && nano.isLan()) return true;
        Nano current = nano;
        nano = null;
        if (current != null) current.stop();
        return start(true);
    }

    private boolean start(boolean lan) {
        String host = lan ? null : LOOPBACK;
        for (int i = 9978; i < 9999; i++) if (start(host, i)) return true;
        return false;
    }

    private boolean start(String host, int port) {
        try {
            nano = new Nano(host, port, this);
            nano.start(500);
            Proxy.set(port);
            return true;
        } catch (Throwable e) {
            if (nano != null) nano.stop();
            nano = null;
            return false;
        }
    }

    boolean isCurrent(Nano current) {
        return nano == current;
    }

    public synchronized void stop() {
        LogSession.get().stop();
        Nano current = nano;
        service = null;
        nano = null;
        if (current != null) Task.execute(current::stop);
    }
}
