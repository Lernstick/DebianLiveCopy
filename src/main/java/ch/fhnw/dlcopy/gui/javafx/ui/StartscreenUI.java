package ch.fhnw.dlcopy.gui.javafx.ui;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.*;
import javafx.scene.control.*;

public class StartscreenUI implements View{
    
    private Parent root;
    
    public StartscreenUI(){
        try {
            root = FXMLLoader.load(getClass().getResource("/fxml/startscreen.fxml"));
        } catch (IOException ex) {
            Logger.getLogger(StartscreenUI.class.getName()).log(Level.SEVERE, "Failed to load FXML-file!", ex);
        }
        init();
    }

    @Override
    public Parent getView() {
        return root;
    }
   
}
