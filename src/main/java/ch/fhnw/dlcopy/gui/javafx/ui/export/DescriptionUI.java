package ch.fhnw.dlcopy.gui.javafx.ui.export;

import ch.fhnw.dlcopy.gui.javafx.StorageMediaManagement;
import ch.fhnw.dlcopy.gui.javafx.ui.SceneManager;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class DescriptionUI implements View, SceneManager {
    private Parent root;
    private StorageMediaManagement context;

    public DescriptionUI(){
         try {
            root = FXMLLoader.load(getClass().getResource("/fxml/export/description.fxml"));
        } catch (IOException ex) {
            Logger.getLogger(StartscreenUI.class.getName()).log(Level.SEVERE, "Failed to load FXML-file!", ex);
        }
        init();
    }
    
    @Override
    public Parent getRoot(StorageMediaManagement context) {
        this.context = context;
        return root;
    }
    
    
}
