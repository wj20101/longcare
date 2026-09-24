package com.ytone.longcare.platform.webview;

import android.app.Instrumentation;
import android.os.Looper;
import android.webkit.WebView;
import com.ytone.longcare.integration.qlz.QlzReleaseTestRunner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Unit;
import org.junit.Test;
import static org.junit.Assert.*;

/** Calls the actual kept bridge in the signed R8 target, with an entirely local document. */
public final class NativeBridgeReleaseTest {
    @Test public void numericIdSurvivesR8() throws Exception { verify("123", 123); }
    @Test public void stringIdSurvivesR8() throws Exception { verify("'456'", 456); }

    private void verify(String argument, int expected) throws Exception {
        Instrumentation instrumentation = QlzReleaseTestRunner.current;
        CountDownLatch opened = new CountDownLatch(1);
        AtomicInteger customer = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        AtomicReference<Looper> callbackLooper = new AtomicReference<>();
        WebView[] view = new WebView[1];
        NativeBridge[] bridge = new NativeBridge[1];
        try {
            instrumentation.runOnMainSync(() -> {
                bridge[0] = new NativeBridge(() -> true, () -> {
                    closes.incrementAndGet();
                    return Unit.INSTANCE;
                }, id -> {
                    customer.set(id);
                    callbackLooper.set(Looper.myLooper());
                    opened.countDown();
                    return Unit.INSTANCE;
                });
                view[0] = new WebView(instrumentation.getTargetContext());
                view[0].getSettings().setJavaScriptEnabled(true);
                view[0].addJavascriptInterface(bridge[0], "NativeBridge");
                view[0].loadDataWithBaseURL("https://offline.invalid/", "<script>"
                    + "NativeBridge.enterUserDetails(0);NativeBridge.enterUserDetails('bad');"
                    + "NativeBridge.enterUserDetails(" + argument + ");NativeBridge.closeWebView();"
                    + "</script>", "text/html", "UTF-8", null);
            });
            assertTrue("R8 bridge callback missing", opened.await(20, TimeUnit.SECONDS));
            instrumentation.waitForIdleSync();
            assertEquals(expected, customer.get());
            assertSame(Looper.getMainLooper(), callbackLooper.get());
            assertEquals(0, closes.get());
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (bridge[0] != null) bridge[0].dispose();
                if (view[0] != null) {
                    view[0].removeJavascriptInterface("NativeBridge");
                    view[0].destroy();
                }
            });
        }
    }
}
