package ch.fhnw.dlcopy.gui.javafx.ui.export;

import ch.fhnw.dlcopy.gui.javafx.SceneContext;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextField;

public class ConfigUI implements View {
    private Parent root;
    private TextField txtDVDLabel;
    private SceneContext context;

    public ConfigUI(){
         try {
            root = FXMLLoader.load(getClass().getResource("/fxml/export/config.fxml"));
        } catch (IOException ex) {
            Logger.getLogger(StartscreenUI.class.getName()).log(Level.SEVERE, "Failed to load FXML-file!", ex);
        }
        init();
    }
    
    @Override
    public Parent getRoot(SceneContext context) {
        this.context = context;
        return root;
    }
    
    
}
