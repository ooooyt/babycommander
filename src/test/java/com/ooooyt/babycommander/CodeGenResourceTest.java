package com.ooooyt.babycommander;

import com.ooooyt.babycommander.util.I18n;
import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CodeGenResourceTest {

    @BeforeAll
    static void setup() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @Test
    void testStatusEndpoint() {
        given()
            .when().get("/api/status")
            .then()
            .statusCode(200)
            .body("active", is(false))
            .body("status", equalTo("IDLE"))
            .body("version", equalTo("2.0.0"))
            .body("currentTool", equalTo("idle"))
            .body("completedSteps", isA(List.class));
    }
}
