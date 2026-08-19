package com.ooooyt.babycommander.intent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.ooooyt.babycommander.intent.IntentDetector.Mode;
import com.ooooyt.babycommander.util.I18n;
import java.util.Locale;

class IntentDetectorTest {

    @BeforeEach
    void setUp() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @Test
    void testDetectBugfix() {
        assertEquals(Mode.BUGFIX, IntentDetector.detect("fix the authentication bug"));
        assertEquals(Mode.BUGFIX, IntentDetector.detect("the tests are broken"));
        assertEquals(Mode.BUGFIX, IntentDetector.detect("there's a bug in the calculator"));
        assertEquals(Mode.BUGFIX, IntentDetector.detect("debug the authentication"));
    }

    @Test
    void testDetectRefactor() {
        assertEquals(Mode.REFACTOR, IntentDetector.detect("refactor the UserService"));
        assertEquals(Mode.REFACTOR, IntentDetector.detect("extract the validation logic into a separate class"));
        assertEquals(Mode.REFACTOR, IntentDetector.detect("rename the UserEntity to CustomerEntity"));
        assertEquals(Mode.REFACTOR, IntentDetector.detect("clean up the code structure"));
    }

    @Test
    void testDetectExtension() {
        assertEquals(Mode.EXTENSION, IntentDetector.detect("add user profile support"));
        assertEquals(Mode.EXTENSION, IntentDetector.detect("extend the API with pagination"));
        assertEquals(Mode.EXTENSION, IntentDetector.detect("implement a new feature for exporting reports"));
        assertEquals(Mode.EXTENSION, IntentDetector.detect("add JWT authentication"));
    }

    @Test
    void testDetectCreate() {
        assertEquals(Mode.CREATE, IntentDetector.detect("create a REST API for todos"));
        assertEquals(Mode.CREATE, IntentDetector.detect("build a Python calculator"));
        assertEquals(Mode.CREATE, IntentDetector.detect("write a todo list app"));
        assertEquals(Mode.CREATE, IntentDetector.detect("generate a new Flask project"));
    }

    @Test
    void testMixedInputPrefersExplicitKeywords() {
        Mode mode = IntentDetector.detect("fix the bug and add logging");
        assert mode == Mode.BUGFIX || mode == Mode.EXTENSION : "Should detect either BUGFIX or EXTENSION";
    }

    @Test
    void testNullAndEmptyInput() {
        assertEquals(Mode.CREATE, IntentDetector.detect(null));
        assertEquals(Mode.CREATE, IntentDetector.detect(""));
        assertEquals(Mode.CREATE, IntentDetector.detect("   "));
    }

    @Test
    void testFromFlagDocument() {
        assertEquals(IntentDetector.Mode.DOCUMENT, IntentDetector.fromFlag("document"));
        assertEquals(IntentDetector.Mode.DOCUMENT, IntentDetector.fromFlag("doc"));
    }
}
