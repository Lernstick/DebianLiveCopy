package ch.fhnw.dlcopy.model;

import java.util.ArrayList;
import java.util.Locale;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

public class PresentationModel {
    public final static int WINDOWS_HEIGHT_DEFAULT  = 800;
    public final static int WINDOWS_WIDTH_DEFAULT   = 1280;
    
    private static PresentationModel instance;
    
    private final IntegerProperty height    = new SimpleIntegerProperty(WINDOWS_HEIGHT_DEFAULT);
    private final IntegerProperty width     = new SimpleIntegerProperty(WINDOWS_WIDTH_DEFAULT);
    
    private PresentationModel(){}
    
    public static synchronized PresentationModel getInstance(){
        if (instance == null) {
            instance = new PresentationModel();
        }
        return instance;
    }
    
    public int getHeight(){
        return height.get();
    }
    
    public void setHeight(int height){
        this.height.set(height);
    }
    
    public IntegerProperty heightProperty(){
        return height;
    }
    
    public int getWidth(){
        return width.get();
    }
    
    public void setWidth(int width){
        this.width.set(width);
    }
    
    public IntegerProperty widthProperty(){
        return width;
    }
}
