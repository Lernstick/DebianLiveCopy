package ch.fhnw.dlcopy.gui.javafx.ui;

import ch.fhnw.dlcopy.gui.javafx.SceneContext;
import ch.fhnw.dlcopy.model.PresentationModel;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public abstract class View {

    protected SceneContext context;
    protected PresentationModel model = PresentationModel.getInstance();
    protected URL resourcePath;
    protected ResourceBundle stringBundle = ResourceBundle.getBundle("strings/Strings");
    
    /**
     * This function is called, when the view should be deinitalized.
     * It has to be called manually!
     */
    public void deinitialize(){}
    
    @FXML
    public final void initialize(){
        initSelf();
        initControls();
        layoutControls();
        setupBindings();
        setupEventHandlers();
        setupValueChangedListeners();
    }

    protected void initSelf(){}

    protected void initControls(){}

    protected void layoutControls(){}

    protected void setupBindings(){}

    protected void setupEventHandlers(){}

    protected void setupValueChangedListeners(){}
  
    /**
    * Returns the root parent. This parent can be displayed in a FX-scene
    * For displaying an other scene, this view can call `context.setScene(new ViewToBeDisplayed())`
    * @param context The context, where to set the scene
    * @return A Parent to be displayed
    */
    public Parent getRoot(SceneContext context) throws IOException{
        this.context = context;
        
        FXMLLoader loader = new FXMLLoader(resourcePath);
        loader.setController(this);
        loader.setResources(stringBundle);
        return loader.load();
    }
 }
