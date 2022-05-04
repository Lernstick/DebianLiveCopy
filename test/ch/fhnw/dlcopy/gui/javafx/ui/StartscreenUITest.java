
package ch.fhnw.dlcopy.gui.javafx.ui;

import static org.mockito.Mockito.*;
import static org.testfx.api.FxAssert.*;
import static org.testfx.matcher.base.NodeMatchers.*;
import static org.testfx.matcher.control.LabeledMatchers.*;

import ch.fhnw.dlcopy.gui.javafx.SceneContext;
import ch.fhnw.dlcopy.gui.javafx.ui.exportdata.ExportDataUI;
import ch.fhnw.dlcopy.gui.javafx.ui.exportsystem.ExportSystemUI;
import ch.fhnw.dlcopy.gui.javafx.ui.install.SelectDeviceUI;
import ch.fhnw.dlcopy.model.PresentationModel;
import java.util.Locale;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.robot.Motion;

public class StartscreenUITest extends ApplicationTest {
    
    private final PresentationModel model = PresentationModel.getInstance();
    private SceneContext sceneContext = mock(SceneContext.class);
    
    @Override public void start(Stage stage) throws Exception {
        
        View view = new StartscreenUI();
        Parent root = view.getRoot(sceneContext);
        Scene scene = new Scene(root);
        stage.setScene(scene);
        
        stage.show();
    }
    
    @Test public void clickExportDataButton(){
        // Arrange
        
        // Act
        clickOn("#btnExportData");
        
        //Assert
        verify(sceneContext, atLeastOnce()).setScene(isA(ExportDataUI.class));
    }
    
    @Test public void hasExportDataButton_de() {
        
        // Arrange
        model.setLanguage(Locale.GERMAN);
        
        // Act
        
        //Assert
        verifyThat("#btnExportData", hasText("Daten exportieren"));
    }
    
    @Test public void hoverExportDataButton() {
        
        // Arrange
        
        // Act
        moveTo("#btnExportData");
        
        //Assert
        verifyThat("#imgExportData", isVisible());
        verifyThat("#panExportData .label", isVisible());
    }
    
    @Test public void clickExportSystemButton(){
        // Arrange
        
        // Act
        clickOn("#btnExportSystem");
        
        //Assert
        verify(sceneContext, atLeastOnce()).setScene(isA(ExportSystemUI.class));
    }
        
    @Test public void hasExportSystemButton_de() {
        
        // Arrange
        model.setLanguage(Locale.GERMAN);
        
        // Act
        
        //Assert
        verifyThat("#btnExportSystem", hasText("System exportieren"));
    }
    
    @Test public void hoverExportSystemButton() {
        
        // Arrange
        
        // Act
        moveTo("#btnExportSystem");
        
        //Assert
        verifyThat("#imgExportSystem", isVisible());
        verifyThat("#panExportSystem .label", isVisible());
    }
    
    @Test public void clickInstallButton(){
        // Arrange
        
        // Act
        clickOn("#btnInstall");
        
        //Assert
        verify(sceneContext, atLeastOnce()).setScene(isA(SelectDeviceUI.class));
    }
    
    @Test public void hasInstallButton_de() {
        
        // Arrange
        model.setLanguage(Locale.GERMAN);
        
        // Act
        
        //Assert
        verifyThat("#btnInstall", hasText("Installieren"));
    }
    
    @Test public void hoverInstallButton() {
        
        // Arrange
        
        // Act
        moveTo("#btnInstall");
        
        //Assert
        verifyThat("#imgInstall", isVisible());
        verifyThat("#panInstall .label", isVisible());
    }
    
    @Test public void hasResetButton_de() {
        
        // Arrange
        model.setLanguage(Locale.GERMAN);
        
        // Act
        
        //Assert
        verifyThat("#btnReset", hasText("Zurücksetzten"));
    }
    
    @Test public void hoverResetButton() {
        
        // Arrange
        
        // Act
        moveTo("#btnReset");
        
        //Assert
        verifyThat("#imgReset", isVisible());
        verifyThat("#panReset .label", isVisible());
    }
    
    @Test public void hasUpdateButton_de() {
        
        // Arrange
        model.setLanguage(Locale.GERMAN);
        
        // Act
        
        //Assert
        verifyThat("#btnUpdate", hasText("Aktuallisieren"));
    }
    
    @Test public void hoverUpdateButton() {
        
        // Arrange
        
        // Act
        moveTo("#btnUpdate");
        
        //Assert
        verifyThat("#imgUpdate", isVisible());
        verifyThat("#panUpdate .label", isVisible());
    }
}   
