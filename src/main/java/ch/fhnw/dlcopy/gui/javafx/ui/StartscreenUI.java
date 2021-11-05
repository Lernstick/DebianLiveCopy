package ch.fhnw.dlcopy.gui.javafx.ui;

import ch.fhnw.dlcopy.gui.javafx.SceneContext;
import ch.fhnw.dlcopy.gui.javafx.ui.export.ConfigUI;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;

public class StartscreenUI implements View{
    
    private Parent root;
    private Button btnIsoExport;
    private SceneContext context;
    
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
            context.setScene(new ConfigUI());
            
        });
    }
    
    public Parent getRoot(SceneContext context) {
        this.context = context;
        return root;
    }
   
}
