
package ch.fhnw.dlcopy.gui.javafx.ui;

import ch.fhnw.dlcopy.gui.javafx.SceneContext;
import static org.mockito.Mockito.*;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;

public class StartscreenUITest extends ApplicationTest {
    
    @Override public void start(Stage stage) throws Exception {
        SceneContext sceneContext = mock(SceneContext.class);
        
        View view = new StartscreenUI();
        Parent root = view.getRoot(sceneContext);
        Scene scene = new Scene(root);
        stage.setScene(scene);
        
        stage.show();
    }
    
    @Test public void hasAButton() {
        // expect:
        verifyThat("#btnInstall", hasText("Installieren"));
    }
}
