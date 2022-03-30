package ch.fhnw.dlcopy.gui.javafx;

import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import org.testfx.framework.junit.ApplicationTest;
import org.testfx.robot.Motion;

import javafx.scene.control.Button;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.Test;

import org.junit.Test;

public class ClickApplicationTest extends ApplicationTest {
    @Override public void start(Stage stage) {
        Parent sceneRoot = new ClickApplication.ClickPane();
        Scene scene = new Scene(sceneRoot, 100, 100);
        stage.setScene(scene);
        stage.show();
    }

    @Test public void should_contain_button() {
        // expect:
        verifyThat(".button", hasText("click me!"));
    }

    @Test public void should_click_on_button() {
        // when:
        clickOn(".button");

        // then:
        verifyThat(".button", hasText("clicked!"));
    }
}