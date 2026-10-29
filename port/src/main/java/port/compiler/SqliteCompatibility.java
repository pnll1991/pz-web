package port.compiler;

import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;

/**
 * Replaces the body of zombie.util.PZSQLUtils.init() with our browser initialization.
 *
 * Platform plumbing only (native library loading + JDBC driver registration), not a game rule:
 * the pinned init() extracts org/sqlite/native/... from the jar and does
 * Class.forName("org.sqlite.JDBC"), exiting with System.exit(1) on ClassNotFoundException. See
 * port.runtime.jdbc.SqliteBootstrap for what the browser does instead, and docs/decisions.md.
 */
public final class SqliteCompatibility implements ClassHolderTransformer {
    private static final String CLASS = "zombie.util.PZSQLUtils";

    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals(CLASS)) return;
        // forwardTo(..., replace=true) keeps every other member of the class untouched.
        JdkCompatibility.forwardTo(cls, context, "init()V", true, "port.runtime.jdbc.SqliteBootstrap", true);
    }
}
