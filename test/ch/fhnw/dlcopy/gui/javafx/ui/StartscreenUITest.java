
package ch.fhnw.dlcopy.gui.javafx.ui;

import ch.fhnw.dlcopy.gui.javafx.SceneContext;
import ch.fhnw.dlcopy.model.PresentationModel;
import java.util.Locale;
import static org.mockito.Mockito.*;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;

public class StartscreenUITest extends ApplicationTest {
    
    private PresentationModel model = PresentationModel.getInstance();
    
    @Override public void start(Stage stage) throws Exception {
        SceneContext sceneContext = mock(SceneContext.class);
        
        View view = new StartscreenUI();
        Parent root = view.getRoot(sceneContext);
        Scene scene = new Scene(root);
        stage.setScene(scene);
        
        stage.show();
    }
    
    @Test public void hasExportDataButton_de() {
        
        // Arrange
        model.setLanguage(Locale.GERMAN);
        
        // Act
        
        //Assert
        verifyThat("#btnExportData", hasText("Daten exportieren"));
    }
        
    @Test public void hasExportSystemButton_de() {
        
        // Arrange
        model.setLanguage(Locale.GERMAN);
        
        // Act
        
        //Assert
        verifyThat("#btnExportSystem", hasText("System exportieren"));
    }
    
    @Test public void hasInstallButton_de() {
        
        // Arrange
        model.setLanguage(Locale.GERMAN);
        
        // Act
        
        //Assert
        verifyThat("#btnInstall", hasText("Installieren"));
    }
    
    @Test public void hasResetButton_de() {
        
        // Arrange
        model.setLanguage(Locale.GERMAN);
        
        // Act
        
        //Assert
        verifyThat("#btnReset", hasText("Zurücksetzten"));
    }
    
    @Test public void hasUpdateButton_de() {
        
        // Arrange
        model.setLanguage(Locale.GERMAN);
        
        // Act
        
        //Assert
        verifyThat("#btnUpdate", hasText("Aktuallisieren"));
    }
}   
