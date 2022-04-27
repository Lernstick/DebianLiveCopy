package ch.fhnw.dlcopy.gui.javafx.ui;

import ch.fhnw.dlcopy.gui.javafx.SceneContext;
import ch.fhnw.dlcopy.model.LanguageObserver;
import ch.fhnw.dlcopy.model.PresentationModel;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

public abstract class View implements LanguageObserver{

    protected SceneContext context;
    protected PresentationModel model = PresentationModel.getInstance();
    protected URL resourcePath;
    protected ResourceBundle stringBundle = ResourceBundle.getBundle("strings/Strings", model.getLanguage());
    
    @FXML private Label lblInfo;
    private String defaultInfo = "";
    
    /**
     * This function is called, when the view should be deinitalized.
     * It has to be called manually!
     */
    public final void deinitialize(){
        deinitialize_override();
        model.removeLanguageObserver(this);
    }
    
    /**
     * This function is called durring the deinitalize procedure.
     */
    protected void deinitialize_override(){}
    
    @FXML
    public final void initialize(){
        model.addLanguageObserver(this);
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
    
    
    protected void addToolTip(Control c, String s){
        Tooltip tt = new Tooltip(s);
        tt.setPrefWidth(400);
        tt.setWrapText(true);
        c.setTooltip(tt);
    }
     /**
     * Prints the given information on the bottem of the view
     * @param info The information to be printed
     */
    protected final void printInfo(String info){
        if (lblInfo != null){
            defaultInfo = info;
            lblInfo.setText(info);
        }
    }

  
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
    
    /**
     * This methode gets called, when the language has changed.
     * @param oldLanguage The old language
     * @param newLanguage The new set language
     */
    @Override
    public void languageChanged(Locale oldLanguage, Locale newLanguage){
        stringBundle = ResourceBundle.getBundle("strings/Strings", model.getLanguage());
    }
 }
