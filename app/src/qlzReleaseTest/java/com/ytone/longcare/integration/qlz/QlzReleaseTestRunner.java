package com.ytone.longcare.integration.qlz;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/** Pure JUnit contracts against the unchanged R8 target, without AndroidX UI/tracing hooks. */
public final class QlzReleaseTestRunner extends Instrumentation {
    private String testClassName;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        testClassName = arguments == null ? null : arguments.getString("class");
        start();
    }

    @Override
    public void onStart() {
        Bundle results = new Bundle();
        try {
            Class<?> testClass = testClassName == null
                    ? QlzReleaseProtobufTest.class
                    : Class.forName(testClassName);
            Result result = JUnitCore.runClasses(testClass);
            StringBuilder report = new StringBuilder("\nTests run: ")
                    .append(result.getRunCount()).append(", failures: ")
                    .append(result.getFailureCount()).append(", ignored: ")
                    .append(result.getIgnoreCount()).append('\n');
            for (Failure failure : result.getFailures()) {
                report.append(failure.getTestHeader()).append('\n').append(failure.getTrace());
            }
            boolean passed = result.wasSuccessful() && result.getRunCount() > 0
                    && result.getIgnoreCount() == 0 && result.getAssumptionFailureCount() == 0;
            report.append(passed ? "OK" : "FAILED");
            results.putString(REPORT_KEY_STREAMRESULT, report.toString());
            finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, results);
        } catch (Throwable failure) {
            results.putString(REPORT_KEY_STREAMRESULT, "FAILED: " + failure);
            finish(Activity.RESULT_CANCELED, results);
        }
    }
}
