package ch.fhnw.dlcopy.gui.javafx.ui;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.*;
import javafx.scene.control.*;

public class StartscreenUI implements View{
    
    private Parent root;
    private Button btnIsoExport;
    
    public StartscreenUI(){
        try {
            root = FXMLLoader.load(getClass().getResource("/fxml/startscreen.fxml"));
        } catch (IOException ex) {
            Logger.getLogger(StartscreenUI.class.getName()).log(Level.SEVERE, "Failed to load FXML-file!", ex);
        }
        init();
    }
    
    @Override
    public void initControls(){
        root.getChildrenUnmodifiable().stream()
                .filter(child -> child.getId().equals("btnIsoExport"))
                .findFirst()
                .ifPresent(node -> btnIsoExport=(Button)node);
    }
    
    @Override
    public void setupEventHandlers(){
        btnIsoExport.setOnAction(event -> {
            System.out.println("Button was pressed");
        });
    }

    @Override
    public Parent getView() {
        return root;
    }
   
}
